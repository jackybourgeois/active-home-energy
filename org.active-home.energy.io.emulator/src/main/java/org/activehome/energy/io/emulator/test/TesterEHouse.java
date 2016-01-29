package org.activehome.energy.io.emulator.test;

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


import com.eclipsesource.json.JsonObject;
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.RequestCallback;
import org.activehome.com.ShowIfErrorCallback;
import org.activehome.com.error.Error;
import org.activehome.context.com.ContextRequest;
import org.activehome.context.com.ContextResponse;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Trigger;
import org.activehome.context.helper.ModelHelper;
import org.activehome.energy.io.emulator.mysql.DataHelper;
import org.activehome.energy.library.EnergyHelper;
import org.activehome.io.action.Command;
import org.activehome.mysql.HelperMySQL;
import org.activehome.test.ComponentTester;
import org.activehome.tools.Convert;
import org.kevoree.annotation.*;
import org.kevoree.api.ModelService;
import org.kevoree.ContainerRoot;
import org.kevoree.api.Port;
import org.kevoree.api.handler.UUIDModel;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.pmodeling.api.ModelCloner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This tester check Emeters, EInteractives, EBaseLoads
 * and EBackgrounds work fine altogether
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class TesterEHouse extends ComponentTester {

    /**
     * Default duration of simulation pause.
     */
    private static final long PAUSE_DURATION = 15000;
    /**
     * Default compression time of simulation.
     */
    private static final long ZIP = 1800;

    /**
     * Port to send message to interactive appliances.
     */
    @Output
    private Port toInteractive;

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
    @Param(optional = false)
    private String dbMetricImport;
    /**
     * Name for the  export metric data
     */
    @Param(optional = false)
    private String dbMetricExport;
    /**
     * Name for the generation metric data
     */
    @Param(optional = false)
    private String dbMetricGeneration;

    private ModelCloner cloner;
    private double sumEnergyConsumption;
    private double sumEnergyBlConsumption;
    private double sumEnergyBgConsumption;
    private double sumEnergyInterConsumption;
    private ScheduledThreadPoolExecutor stpe;
    private boolean testDone = false;
    private LinkedList<String> bgApps;
    private LinkedList<String> interApps;

    /**
     * MySQL date parser.
     */
    private static SimpleDateFormat dfMySQL;

    // == == == Component life cycle == == ==

    @Start
    public void start() {
        super.start();
        KevoreeFactory kevFactory = new DefaultKevoreeFactory();
        cloner = kevFactory.createModelCloner();
    }

    // == == == Time life cycle == == ==

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        super.onInit();
        updateSource();
        sumEnergyConsumption = 0;
        sumEnergyBgConsumption = 0;
        sumEnergyBlConsumption = 0;
        sumEnergyInterConsumption = 0;
        startTS = getTic().getTS();
        stpe = new ScheduledThreadPoolExecutor(1);

        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));

        String[] metricArray = new String[]{"energy.cons","energy.cons.bg",
                "energy.cons.inter","energy.cons.bg.baseload"};
        Request subscriptionReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe", new Object[]{metricArray, getFullId()});

        subscriptionReq.getEnviElem().put("userInfo", testUser());
        sendRequest(subscriptionReq, new ShowIfErrorCallback());
    }

    /**
     * On start time, schedule a pause
     */
    @Override
    public final void onStartTime() {
        super.onStartTime();
        configureTriggers();
        scheduleInteractiveLoad();
    }

    private void configureTriggers() {
        Trigger bgTrigger = new Trigger("(^power\\.cons\\.bg\\.)+(.*?)",
                "sum(power.cons.bg.*)", "power.cons.bg");
        Trigger interTrigger = new Trigger("(^power\\.cons\\.inter\\.)+(.*?)",
                "sum(power.cons.inter.*)", "power.cons.inter");
        Trigger consTrigger = new Trigger("^power\\.cons\\.(bg|inter)$",
                "${power.cons.bg,0}+${power.cons.inter,0}", "power.cons");
        Trigger consEnergyTrigger = new Trigger("^power\\.cons$",
                "($-1{power.cons}/1000)*(($ts{power.cons}-$ts-1{power.cons})/3600000)", "energy.cons");
        Trigger bgEnergyTrigger = new Trigger("^power\\.cons\\.bg$",
                "($-1{power.cons.bg}/1000)*(($ts{power.cons.bg}-$ts-1{power.cons.bg})/3600000)", "energy.cons.bg");
        Trigger interEnergyTrigger = new Trigger("^power\\.cons\\.inter$",
                "($-1{power.cons.inter}/1000)*(($ts{power.cons.inter}-$ts-1{power.cons.inter})/3600000)", "energy.cons.inter");
        Trigger blEnergyTrigger = new Trigger("^power\\.cons\\.bg\\.baseload$",
                "($-1{power.cons.bg.baseload}/1000)*(($ts{power.cons.bg.baseload}-$ts-1{power.cons.bg.baseload})/3600000)", "energy.cons.bg.baseload");

        Request triggerReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "addTriggers",
                new Object[]{new Trigger[]{bgTrigger, bgEnergyTrigger, blEnergyTrigger,
                        interTrigger, interEnergyTrigger, consTrigger, consEnergyTrigger}});
        sendRequest(triggerReq, new ShowIfErrorCallback());
    }

    /**
     * On stop time, extract data from db source
     * and compare with the sum energy.cons
     */
    @Override
    public final void onStopTime() {
        logResults(compareConsumption(startTS, startTS + getTestDuration()));
        super.onStopTime();
    }


    private String compareConsumption(final long start, final long end) {
        double consRef = consumedEnergyReference(start, end);
        logInfo("Test duration: " + (getTestDuration() / DAY) + " days");
        logInfo("Sum consumption bl from context:\t" + sumEnergyBlConsumption + " kWh");
        logInfo("Sum consumption inter from context:\t" + sumEnergyInterConsumption + " kWh");
        logInfo("Sum consumption bg from context:\t" + sumEnergyBgConsumption + " kWh");
        logInfo("Sum consumption from context:\t" + sumEnergyConsumption + " kWh");
        logInfo("Sum consumption reference:   \t" + consRef + " kWh");
        extractConsumptionFromContext(start,end);
        return startDate + "," + sumEnergyConsumption + "," + consRef;
    }

    @Override
    public final void onPauseTime() {
        super.onPauseTime();
        compareConsumption(startTS, getCurrentTime());
        stpe.schedule(this::resumeTime, 30, TimeUnit.SECONDS);
    }

    @Override
    protected final String logHeaders() {
        return "start (local), bg consumption, ref. bg consumption";
    }

    @Override
    protected final JsonObject prepareNextTest() {
        if (!testDone) {
            testDone = true;
            JsonObject timeProp = new JsonObject();
            timeProp.set("startDate", startDate);
            timeProp.set("zip", ZIP);
            return timeProp;
        }
        return null;
    }

    /**
     * Listen to receive the energy.cons data points
     * and sum them. They will be compared to a sum
     * based on data extracted from the database.
     *
     * @param notifStr Notification from the context received as string
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0) {
            if (notif.getContent() instanceof DataPoint) {
                DataPoint dp = (DataPoint) notif.getContent();
                if (dp.getMetricId().equals("energy.cons")
                        && dp.getTS() <= startTS + getTestDuration()) {
                    sumEnergyConsumption += Double.valueOf(dp.getValue());
                } else if (dp.getMetricId().equals("energy.cons.bg.baseload")
                        && dp.getTS() <= startTS + getTestDuration()) {
                    sumEnergyBlConsumption += Double.valueOf(dp.getValue());
                } else if (dp.getMetricId().equals("energy.cons.inter")
                        && dp.getTS() <= startTS + getTestDuration()) {
                    sumEnergyInterConsumption += Double.valueOf(dp.getValue());
                } else if (dp.getMetricId().equals("energy.cons.bg")
                        && dp.getTS() <= startTS + getTestDuration()) {
                    sumEnergyBgConsumption += Double.valueOf(dp.getValue());
                } else {
                    //logInfo("DP after stop: " + strLocalTime(dp.getTS()));
                }
            }
        }
    }

    /**
     *
     * @param startTS Period start
     * @param endTS   Period end
     * @return
     */
    public final double consumedEnergyReference(final long startTS,
                                                final long endTS) {

        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        double sumkWh = 0;
        try {
            ResultSet result = executeQuery(dbConnect, startTS, endTS);

            long lastTS = -1;
            double importWatt = 0;
            double exportWatt = 0;
            double generationWatt = 0;
            while (result.next()) {
                String metricID = result.getString("metricID");
                double value = result.getDouble("value");
                long ts = dfMySQL.parse(result.getString("timestamp")).getTime();
                if (lastTS == -1) {
                    lastTS = ts;
                }
                if (lastTS<ts) {
                    double consWatt = importWatt + generationWatt - exportWatt;
                    sumkWh += Convert.watt2kWh(consWatt, ts - lastTS);
                }

                if (metricID.equals(dbMetricExport)) {
                    exportWatt = value;
                } else if (metricID.equals(dbMetricImport)) {
                    importWatt = value;
                } else if (metricID.equals(dbMetricGeneration)) {
                    generationWatt = value;
                }
                lastTS = ts;
            }
        } catch (SQLException exception) {
            logError("SQL error while extracting"
                    + " import, export and generation references: "
                    + exception.getMessage());
        } catch (ParseException e) {
            e.printStackTrace();
        } finally {
            HelperMySQL.closeDbConnection(dbConnect);
        }
        return sumkWh;
    }

    /**
     * @return
     */
    private String generateQuery() {
        StringBuilder query = new StringBuilder();
        query.append("SELECT `metricID`, `timestamp`, `value` ")
                .append(" FROM `").append(tableName).append("`")
                .append(" WHERE (`timestamp` BETWEEN ? AND ?) ")
                .append(" AND (metricID=? OR metricID=? OR metricID=?) ")
                .append(" ORDER BY `timestamp`");
        return query.toString();
    }

    /**
     * @param startTS
     * @param endTS
     * @return
     * @throws SQLException
     * @throws ParseException
     */
    private ResultSet executeQuery(final Connection dbConnect,
                                   final long startTS,
                                   final long endTS)
            throws SQLException, ParseException {
        PreparedStatement prepStmt =
                dbConnect.prepareStatement(generateQuery());
        prepStmt.setString(1, dfMySQL.format(new Date(startTS)));
        prepStmt.setString(2, dfMySQL.format(new Date(endTS)));
        prepStmt.setString(3, dbMetricImport);
        prepStmt.setString(4, dbMetricExport);
        prepStmt.setString(5, dbMetricGeneration);
        return prepStmt.executeQuery();
    }

    /**
     * Get the interactive load for the given period
     * from the database and schedule the requests for
     * their starts.
     */
    private void scheduleInteractiveLoad() {

        Connection dbConnect = HelperMySQL.connect(urlSQLSource);
        for (String app : interApps) {
            logInfo("loading interactive loads for " + app);
            LinkedList<MetricRecord> loadList = null;
            try {
                MetricRecord metricRecord = DataHelper.loadData(dbConnect, tableName,
                        getCurrentTime(), getCurrentTime() + DAY, app);
                loadList = EnergyHelper.loadDetector(metricRecord, 0, 1);
            } catch (SQLException e) {
                logError("Database error: " + e.getMessage());
            } catch (ParseException e) {
                logError("Parsing error: " + e.getMessage());
            }

            if (loadList != null) {
                for (MetricRecord load : loadList) {
                    logInfo(app + " => " + strLocalTime(load.getStartTime()));
                    long delay = (load.getStartTime() - getCurrentTime()) / ZIP;
                    stpe.schedule(() -> setAppliance(load),
                            delay - MINUTE / ZIP, TimeUnit.MILLISECONDS);
                    stpe.schedule(() -> ctrlApp(load.getMetricId(), Command.START),
                            delay, TimeUnit.MILLISECONDS);
                }
            }
        }

    }

    /**
     * Send the parameters to an appliance,
     * getting it ready to run.
     *
     * @param metricRecord the details of the load to set
     */
    private void setAppliance(final MetricRecord metricRecord) {
        if (toInteractive != null
                && toInteractive.getConnectedBindingsSize() > 0) {
            toInteractive.send(new Request(getFullId(),
                    getNode() + "." + metricRecord.getMetricId(),
                    getCurrentTime(), Command.SET.name(),
                    new Object[]{metricRecord}).toString(), null);
        }

    }

    /**
     * Send a comand to an appliance.
     *
     * @param applianceId the appliance to control
     * @param cmd the command to fire
     */
    private void ctrlApp(final String applianceId,
                         final Command cmd) {
        logInfo("firing cmd " + cmd.name() + " to " + applianceId);
        if (toInteractive != null
                && toInteractive.getConnectedBindingsSize() > 0) {
            toInteractive.send(new Request(getFullId(),
                    getNode() + "." + applianceId,
                    getCurrentTime(), cmd.name()).toString(), null);
        }
    }

    public final void extractConsumptionFromContext(final long startTS,
                                                      final long endTS) {
        ContextRequest ctxAVGReq = new ContextRequest(new String[]{"energy.cons"}, true,
                startTS, endTS-startTS, 0, 1, "", "SUM");
        Request extractAVGReq = new Request(getFullId(), getNode() + ".context", getCurrentTime(),
                "extractSampleData", new Object[]{ctxAVGReq, 1, 0});
        sendRequest(extractAVGReq, new RequestCallback() {
            @Override
            public void success(Object o) {
                String val = ((ContextResponse) o).getResultMap().get(0)
                        .getMetricRecordMap().get("energy.cons").getRecords().getFirst().getValue();
                logInfo("sum consumption extracted from the context: " + val);
            }

            @Override
            public void error(Error error) {
                logError(error.toString());
            }
        });
    }

    private void updateSource() {
        UUIDModel model = modelService.getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());

        LinkedList<String> apps = ModelHelper.findAllRunning("BackgroundAppliance",
                new String[]{context.getNodeName()}, localModel);
        bgApps = new LinkedList<>();
        for (String appFullName : apps) {
            bgApps.add(appFullName.substring(appFullName.lastIndexOf(".")+1));
        }

        apps = ModelHelper.findAllRunning("InteractiveAppliance",
                new String[]{context.getNodeName()}, localModel);
        interApps = new LinkedList<>();
        logInfo("nb interactive appliance found: " + apps.size());
        for (String appFullName : apps) {
            interApps.add(appFullName.substring(appFullName.lastIndexOf(".")+1));
        }
    }

}
