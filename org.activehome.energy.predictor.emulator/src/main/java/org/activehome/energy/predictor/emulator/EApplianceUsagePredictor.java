package org.activehome.energy.predictor.emulator;

/*
 * #%L
 * Active Home :: Energy :: Predictor :: Emulator
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 org.activehome
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.RequestCallback;
import org.activehome.com.ShowIfErrorCallback;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.Device;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.helper.ModelHelper;
import org.activehome.mysql.HelperMySQL;
import org.activehome.predictor.Predictor;
import org.activehome.tools.Convert;
import org.activehome.tools.SunsetSunrise;
import org.kevoree.ContainerRoot;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.UUIDModel;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.log.Log;
import org.kevoree.pmodeling.api.ModelCloner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * This predictor relies on actual data to emulate a prediction
 * of each appliance, baseload and meter on the platform.
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EApplianceUsagePredictor extends Predictor {

    @Param
    private String metrics;
    @Param(defaultValue = "This predictor relies on actual data to emulate a prediction"
            + " of each appliance, baseload and meter on the platform.")
    private String description;
    /**
     * Source of the data.
     */
    @Param(optional = false)
    private String urlSQLSource;
    /**
     * Table that contains the data.
     */
    @Param(optional = false)
    private String tableName;
    /**
     * generate prediction at fixed interval.
     * (-1 will predict only when requested)
     */
    @Param(defaultValue = "1d")
    private String predictInterval;
    /**
     * Default horizon when predicting at fixed intervals.
     */
    @Param(defaultValue = "1d")
    private String defaultHorizon;
    /**
     * Default granularity when predicting at fixed intervals.
     */
    @Param(defaultValue = "1h")
    private String defaultGranularity;
    /**
     * To get a local copy of Kevoree model.
     */
    private ModelCloner cloner;
    /**
     * Map of Appliances and MicroGeneration devices running on the platform.
     */
    private HashMap<String, Device> devices;
    /**
     * MySQL date parser.
     */
    private static SimpleDateFormat dfMySQL;

    /**
     * Scheduler for regular prediction.
     */
    private ScheduledThreadPoolExecutor stpe;

    /**
     * time of the last prediction
     */
    private long lastPredictionTS;

    // component life cycle

    @Start
    public void start() {
        super.start();
        KevoreeFactory kevFactory = new DefaultKevoreeFactory();
        cloner = kevFactory.createModelCloner();
        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // time life cycle

    @Override
    public void onInit() {
        super.onInit();
        lastPredictionTS = -1;
    }

    @Override
    public void onStartTime() {
        super.onStartTime();
        if (!predictInterval.equals("-1")) {
            initExecutor();
            stpe.scheduleAtFixedRate(this::predict, 0,
                    Convert.strDurationToMillisec(predictInterval) / getTic().getZip(),
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onPauseTime() {
        super.onPauseTime();
        if (stpe != null) {
            stpe.shutdownNow();
        }
    }

    @Override
    public void onResumeTime() {
        super.onResumeTime();
        if (!predictInterval.equals("-1")) {
            initExecutor();
            long interval = Convert.strDurationToMillisec(predictInterval);
            long initialDelay = 0;
            if (lastPredictionTS != -1) {
                initialDelay = lastPredictionTS + interval - getTic().getTS();
            }
            stpe.scheduleAtFixedRate(this::predict,
                    initialDelay / getTic().getZip(),
                    interval / getTic().getZip(),
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onStopTime() {
        super.onStopTime();
        if (stpe != null) {
            stpe.shutdownNow();
        }
    }

    // predictions

    private void predict() {
        predict(getCurrentTime(),
                Convert.strDurationToMillisec(defaultHorizon),
                Convert.strDurationToMillisec(defaultGranularity),
                new ShowIfErrorCallback());
    }

    /**
     * Predict the appliance usage for the given time frame.
     *
     * @param startTS     Start time-stamp of the time frame
     * @param duration    Duration of the time frame
     * @param granularity Duration of each time slot
     * @param callback    where we send the result
     */
    @Override
    public void predict(final long startTS,
                        final long duration,
                        final long granularity,
                        final RequestCallback callback) {
        updateSource();
        Schedule predictionSchedule = loadPredictions(startTS,
                startTS + duration, granularity);
        predictionSchedule.getMetricRecordMap().values().forEach(this::addPredictionToHistory);
        callback.success(predictionSchedule);
        lastPredictionTS = getCurrentTime();
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), predictionSchedule));
    }

    public final Schedule loadPredictions(final long startTS,
                                          final long endTS,
                                          final long granularity) {
        Schedule predictionSchedule = new Schedule("schedule.prediction.power",
                startTS, endTS - startTS, granularity);
        if (devices.size() > 0) {
            Connection dbConnect = HelperMySQL.connect(urlSQLSource);
            PreparedStatement prepStmt = null;
            ResultSet result = null;
            try {
                prepStmt = dbConnect.prepareStatement(generateQueryApp());
                prepStmt.setString(1, dfMySQL.format(new Date(startTS)));
                prepStmt.setString(2, dfMySQL.format(new Date(endTS)));
                int index = 3;
                for (String metric : devices.keySet()) {
                    prepStmt.setString(index, metric.replace(getNode() + ".", ""));
                    index++;
                }

                result = prepStmt.executeQuery();
                SampledMetricRecordBuilder mrBuilder = null;
                while (result.next()) {
                    String metricId = result.getString("metricID");
                    long ts = dfMySQL.parse(result.getString("timestamp")).getTime();
                    double val = result.getDouble("value");

                    // extracted a full load, add to result list
                    if (mrBuilder != null && !mrBuilder.getMetric().endsWith(metricId)) {
                        MetricRecord predictionMR = mrBuilder.getMetricRecord();
                        predictionSchedule.getMetricRecordMap().put(predictionMR.getMetricId(), predictionMR);
                        mrBuilder = null;
                    }
                    // create a new load
                    if (mrBuilder == null) {
                        String prefix = devices.get(getNode() + "." + metricId)
                                .getAttributeMap().get("prefix");
                        mrBuilder = new SampledMetricRecordBuilder(
                                prefix + metricId, startTS, endTS, granularity);
                    }

                    mrBuilder.addValue(ts, val);
                }
                if (mrBuilder != null) {
                    MetricRecord predictionMR = mrBuilder.getMetricRecord();
                    predictionSchedule.getMetricRecordMap().put(predictionMR.getMetricId(), predictionMR);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            } finally {
                closeStmtAndResult(prepStmt, result);
            }
        }

        MetricRecord predictionBaseLoad = loadPredictionBaseLoad(startTS, endTS, granularity);
        predictionSchedule.getMetricRecordMap().put(predictionBaseLoad.getMetricId(), predictionBaseLoad);

        return predictionSchedule;
    }

    private void closeStmtAndResult(PreparedStatement prepStmt, ResultSet result) {
        if (prepStmt != null) {
            try {
                prepStmt.close();
            } catch (SQLException e) {
                logError("Closing statement: " + e.getMessage());
            }
        }
        if (result != null) {
            try {
                result.close();
            } catch (SQLException e) {
                logError("Closing resultSet: " + e.getMessage());
            }
        }
    }

    private String generateQueryApp() {
        StringBuilder query = new StringBuilder();
        query.append("SELECT `metricID`, `timestamp`, `value` ")
                .append(" FROM `").append(tableName).append("`")
                .append(" WHERE (`timestamp` BETWEEN ? AND ?) ");

        if (devices.size() > 0) {
            query.append(" AND ( ");
            boolean first = true;
            for (int i = 0; i < devices.size(); i++) {
                if (!first) {
                    query.append(" OR ");
                } else {
                    first = false;
                }
                query.append("metricID=? ");
            }
            query.append(" ) ");
        }
        query.append(" ORDER BY `metricID`, `timestamp`");
        return query.toString();
    }


    private MetricRecord loadPredictionBaseLoad(final long startTS,
                                                final long endTS,
                                                final long granularity) {
        double currentImport = 0.;
        double currentExport = 0.;

        Double prevBaseLoad = null;
        long prevTS = -1;
        HashMap<String, Double> appLoad = new HashMap<>();
        HashMap<String, Double> genLoad = new HashMap<>();

        double baseLoad;
        SampledMetricRecordBuilder mrBuilder = new SampledMetricRecordBuilder(
                "power.cons.bg.baseload", startTS, endTS, granularity);

        Connection dbConnect = HelperMySQL.connect(urlSQLSource);
        PreparedStatement prepStmt = null;
        ResultSet result = null;
        try {
            prepStmt = dbConnect.prepareStatement(generateQueryBaseLoad());
            prepStmt.setString(1, dfMySQL.format(new Date(startTS)));
            prepStmt.setString(2, dfMySQL.format(new Date(endTS)));
            int i = 2;
            for (String device : devices.keySet()) {
                i++;
                prepStmt.setString(i, device);
            }
            result = prepStmt.executeQuery();
            while (result.next()) {
                double val = result.getDouble("value");
                String metric = result.getString("metricID");
                String type = result.getString("type").split("/")[0];
                long ts = dfMySQL.parse(result.getString("timestamp")).getTime();
                if (prevTS == -1) prevTS = ts;
                if (prevTS != ts) {
                    double consumption = 0;
                    for (String key : appLoad.keySet()) {
                        consumption += appLoad.get(key);
                    }
                    double generation = 0;
                    for (String key : genLoad.keySet()) {
                        generation += genLoad.get(key);
                    }
                    baseLoad = currentImport + generation - currentExport - consumption;
                    if (baseLoad < 0) {
                        baseLoad = 0;
                    }
                    if (prevBaseLoad == null || prevBaseLoad.compareTo(baseLoad) != 0) {
                        mrBuilder.addValue(ts, baseLoad);
                        prevBaseLoad = baseLoad;
                    }
                    prevTS = ts;
                }

                if (metric.compareTo("export") == 0) {
                    currentExport = val;
                } else if (metric.compareTo("import") == 0) {
                    currentImport = val;
                } else if (type.endsWith("MicroGeneration")) {
                    genLoad.put(metric, val);
                } else {
                    appLoad.put(metric, val);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } finally {
            closeStmtAndResult(prepStmt, result);
        }

        return mrBuilder.getMetricRecord();
    }

    private String generateQueryBaseLoad() {
        StringBuilder query = new StringBuilder();
        query.append("SELECT d.`metricID`, s.`type`, d.`timestamp`, d.`value` ");
        query.append(" FROM `").append(tableName).append("` d JOIN `sources` s ON s.`metricId`=d.`metricId`");
        query.append(" WHERE d.`timestamp` BETWEEN ? AND ? ");
        query.append(" AND (d.`metricID`='import' OR d.`metricID`='export' ");

        for (int i = 0; i < devices.size(); i++) {
            query.append(" OR d.`metricId`=? ");
        }

        query.append(" ) ORDER BY d.`timestamp`");
        return query.toString();
    }

    private void updateSource() {
        UUIDModel model = getModelService().getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());
        metrics = "";
        devices = new HashMap<>();
        devices.putAll(findSourceType("BackgroundAppliance", "power.cons.bg.", localModel));
        devices.putAll(findSourceType("InteractiveAppliance", "power.cons.inter.", localModel));
        devices.putAll(findSourceType("MicroGeneration", "power.gen.", localModel));
    }

    private HashMap<String, Device> findSourceType(final String deviceType,
                                                   final String prefix,
                                                   final ContainerRoot localModel) {
        HashMap<String, Device> devices = ModelHelper.findAllRunningDevice(deviceType,
                new String[]{context.getNodeName()}, localModel);
        for (Device device : devices.values()) {
            device.getAttributeMap().put("prefix", prefix);
            addToMetricList(prefix + device.getID() + "#prediction");
        }
        return devices;
    }

    private void addToMetricList(String metric) {
        if (!metrics.equals("")) {
            metrics += ",";
        }
    }

    @Override
    public void modelUpdated() {
        sendRequest(new Request(getFullId(), getNode() + ".http", getCurrentTime(),
                        "addHandler", new Object[]{"/" + getId(), getFullId(), true}),
                new ShowIfErrorCallback());
        super.modelUpdated();
    }

    private  void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-prediction-pool");
        });
    }

}
