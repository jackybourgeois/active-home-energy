package org.activehome.energy.io.emulator;

/*
 * #%L
 * Active Home :: Energy :: IO :: Emulator
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 Active Home Project
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
import org.activehome.context.data.DataPoint;
import org.activehome.context.helper.ModelHelper;
import org.activehome.energy.io.emulator.mysql.DataHelper;
import org.activehome.io.Appliance;
import org.activehome.io.BackgroundAppliance;
import org.activehome.io.IO;
import org.activehome.mysql.HelperMySQL;
import org.activehome.time.TimeControlled;
import org.activehome.time.TimeStatus;
import org.activehome.tools.SunsetSunrise;
import org.kevoree.ContainerRoot;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.pmodeling.api.ModelCloner;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.UUIDModel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EBaseLoad extends BackgroundAppliance {

    @Param(defaultValue = "Emulate a load based on the overall consumption minus the considered appliances to compensate load that are not measured.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.io.emulator")
    private String src;

    /**
     * Commands.
     */
    @Param(defaultValue = "")
    private String commands;
    /**
     * Label of the metric sent to the context.
     */
    @Param(defaultValue = "power.cons.bg.<compId>")
    private String metrics;
    /**
     * Access to the Kevoree model
     */
    @KevoreeInject
    private ModelService modelService;

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
     * Name for the  import metric data
     */
    @Param(defaultValue = "import")
    private String dbMetricImport;
    /**
     * Name for the  export metric data
     */
    @Param(defaultValue = "export")
    private String dbMetricExport;
    /**
     * Name for the generation metric data
     */
    @Param(defaultValue = "generation")
    private String dbMetricGeneration;
    /**
     * MySQL date parser.
     */
    private static SimpleDateFormat dfMySQL;
    /**
     * Last value sent, the current power.
     */
    private double currentPower;
    /**
     * if time is paused, time when the time has been paused
     */
    private long pauseTime;

    private long endDataLoad;
    /**
     * Scheduler playing the load values at the right time.
     */
    private ScheduledThreadPoolExecutor stpe;

    private ModelCloner cloner;

    private LinkedList<DataPoint> data;

    private LinkedList<String> devices;

    private HashMap<String, Double> currentPowerMap;

    // == == == Component life cycle == == ==

    @Start
    public void start() {
        super.start();
        KevoreeFactory kevFactory = new DefaultKevoreeFactory();
        cloner = kevFactory.createModelCloner();
        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // == == == Time life cycle == == ==

    @Override
    public final void onInit() {
        super.onInit();
        endDataLoad = pauseTime = -1;
        data = new LinkedList<>();
        updateSource();
        initExecutor();
        currentPowerMap = new HashMap<>();
        currentPowerMap.put(dbMetricImport, 0.);
        currentPowerMap.put(dbMetricExport, 0.);
        currentPowerMap.put(dbMetricGeneration, 0.);
    }

    @Override
    public final void onStartTime() {
        super.onStartTime();
        currentPower = 0;
        updateCurrentPower(getTic().getTS(), currentPower);
        loadData(getCurrentTime(), getCurrentTime() + TimeControlled.DAY);
        scheduleNextVal();
    }

    @Override
    public final void onPauseTime() {
        super.onPauseTime();
        pauseTime = getCurrentTime();
        stpe.shutdownNow();
        initExecutor();
    }

    @Override
    public final void onResumeTime() {
        super.onResumeTime();
        if (pauseTime != -1 && !data.isEmpty()) {
            DataPoint dp = data.getFirst();
            long execTime = (dp.getTS() - pauseTime) / getTic().getZip();
            stpe.schedule(this::playNextValue, execTime, TimeUnit.MILLISECONDS);
            scheduleNextLoadingTime();
        }
        pauseTime = -1;
    }

    @Override
    public final void onStopTime() {
        super.onStopTime();
        stpe.shutdownNow();
    }


    /**
     * Compute and publish current base load value.
     * generation + import - everything else (including export)
     *
     * @param ts
     */
    public final void updateBaseLoad(long ts) {
        double consumption = 0;
        for (String key : currentPowerMap.keySet()) {
            if (key.equals("generation") || key.equals("import")) {
                consumption += currentPowerMap.get(key);
            } else {
                consumption -= currentPowerMap.get(key);
            }
        }

        if (consumption < 0) {
            consumption = 0;
        }

        currentPower = consumption;
        DataPoint dp = new DataPoint(metrics.replace("<compId>", getId()),
                ts, currentPower + "");
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), dp));
    }

    /**
     * Load data of import, export, generation and all appliances deployed
     *
     * @param startTS period start time
     * @param endTS   period end time
     * @return list of DataPoint
     */
    private void loadData(final long startTS,
                          final long endTS) {
        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        LinkedList<DataPoint> dataPoints = new LinkedList<>();
        PreparedStatement prepStmt = null;
        ResultSet result = null;
        try {
            prepStmt = dbConnect.prepareStatement(generateQuery());
            prepStmt.setString(1, dfMySQL.format(new Date(startTS)));
            prepStmt.setString(2, dfMySQL.format(new Date(endTS)));
            prepStmt.setString(3, dbMetricImport);
            prepStmt.setString(4, dbMetricExport);
            prepStmt.setString(5, dbMetricGeneration);
            int index = 6;
            for (String metric : devices) {
                prepStmt.setString(index, metric);
                index++;
            }
            result = prepStmt.executeQuery();
            while (result.next()) {
                String value = result.getString("value");
                long ts = dfMySQL.parse(result.getString("timestamp")).getTime();
                String metricId = result.getString("metricID");
                if (metricId.contains("SolarPV") || metricId.contains("export")) {
                    SunsetSunrise ss = new SunsetSunrise(52.041404, -0.72878, new Date(startTS), 0);
                    if (!ss.isDaytime()) {
                        value = "0";
                    }
                }
                dataPoints.addLast(new DataPoint(metricId, ts, value));
            }
        } catch (SQLException exception) {
            logError("SQL error while extracting data: " + exception.getMessage());
        } catch (ParseException e) {
            e.printStackTrace();
        } finally {
            DataHelper.closeStatement(prepStmt, this);
            DataHelper.closeResultSet(result, this);
            HelperMySQL.closeDbConnection(dbConnect);
        }

        if (data != null) {
            data.addAll(dataPoints);
        } else {
            data = dataPoints;
        }

        endDataLoad = endTS;
        scheduleNextLoadingTime();
    }

    /**
     * Send the next value of the running load.
     */
    private void playNextValue() {
        if (getTic().getStatus().equals(TimeStatus.RUNNING)) {
            if (!data.isEmpty()) {
                DataPoint dp = data.pollFirst();
                currentPowerMap.put(dp.getMetricId(), Double.valueOf(dp.getValue()));
                updateBaseLoad(dp.getTS());
                scheduleNextVal();
            }
        }
    }

    /**
     * Schedule the next value to play.
     */
    private void scheduleNextVal() {
        while (!data.isEmpty() && data.getFirst().getTS() < getCurrentTime()) {
            DataPoint dp = data.pollFirst();
            currentPowerMap.put(dp.getMetricId(), Double.valueOf(dp.getValue()));
            updateBaseLoad(dp.getTS());
        }
        if (!data.isEmpty()) {
            DataPoint dp = data.getFirst();
            long execTime = (dp.getTS() - getCurrentTime()) / getTic().getZip();
            stpe.schedule(this::playNextValue, execTime, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * @param ts    timestamp of the power update
     * @param value new power rate
     */
    private void updateCurrentPower(final long ts,
                                    final double value) {
        DataPoint powerDP = new DataPoint(metrics.replace("<compId>", getId()),
                ts, value + "");
        Notif notif = new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), powerDP);
        sendNotif(notif);
    }

    private String generateQuery() {
        StringBuilder query = new StringBuilder();
        query.append("SELECT `metricID`, `timestamp`, `value` ")
                .append(" FROM `").append(tableName).append("`")
                .append(" WHERE (`timestamp` BETWEEN ? AND ?) ")
                .append(" AND (metricID=? OR metricID=? OR metricID=? ");

        for (String device : devices) {
            query.append(" OR metricID=? ");
        }
        query.append(" ) ORDER BY `timestamp`");
        return query.toString();
    }

    /**
     *
     */
    private void updateSource() {
        UUIDModel model = modelService.getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());
        LinkedList<String> apps = ModelHelper.findAllRunning("Appliance",
                new String[]{context.getNodeName()}, localModel);

        devices = new LinkedList<>();
        for (String appFullName : apps) {
            String appName = appFullName.substring(appFullName.lastIndexOf(".") + 1);
            devices.add(appName);
        }
    }

    private void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-emulator-baseload-pool");
        });
    }

    private void scheduleNextLoadingTime() {
        long execTime;
        long startTS;
        if (endDataLoad == -1) {
            execTime = 0;
            startTS = getCurrentTime();
        } else {
            execTime = (endDataLoad - 4 * HOUR - getCurrentTime()) / getTic().getZip();
            startTS = endDataLoad;
        }
        stpe.schedule(() -> loadData(startTS, startTS + TimeControlled.DAY),
                execTime, TimeUnit.MILLISECONDS);
    }

}

