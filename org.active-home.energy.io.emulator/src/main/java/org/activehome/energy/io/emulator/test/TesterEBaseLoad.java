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
import org.activehome.com.ShowIfErrorCallback;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Trigger;
import org.activehome.context.helper.ModelHelper;
import org.activehome.mysql.HelperMySQL;
import org.activehome.test.ComponentTester;
import org.activehome.tools.Convert;
import org.kevoree.annotation.*;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.UUIDModel;
import org.kevoree.ContainerRoot;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.pmodeling.api.ModelCloner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
public class TesterEBaseLoad extends ComponentTester {

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
    private double sumEnergyBLConsumption;
    private ScheduledThreadPoolExecutor stpe;
    private boolean testDone = false;
    private LinkedList<String> appliances;

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

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        super.onInit();
        sumEnergyConsumption = 0;
        sumEnergyBLConsumption = 0;
        startTS = getTic().getTS();
        stpe = new ScheduledThreadPoolExecutor(1);

        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));

        updateSource();

        String[] metricArray = new String[]{"energy.cons","power.cons.bg.*","energy.cons.bg.baseload"};
        Request subscriptionReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe", new Object[]{metricArray, getFullId()});

        subscriptionReq.getEnviElem().put("userInfo", testUser());
        sendRequest(subscriptionReq, new ShowIfErrorCallback());
    }

    @Override
    public final void onStartTime() {
        super.onStartTime();
        configureTriggers();
    }

    private void configureTriggers() {
        Trigger bgTrigger = new Trigger("(^power\\.cons\\.bg\\.)+(.*?)",
                "sum(power.cons.bg.*)", "power.cons.bg");
        Trigger consTrigger = new Trigger("^power\\.cons\\.bg$",
                "${power.cons.bg,0}", "power.cons");
        Trigger consBLEnergyTrigger = new Trigger("^power\\.cons\\.bg\\.baseload$",
                "($-1{power.cons.bg.baseload}/1000)*(($ts{power.cons.bg.baseload}-$ts-1{power.cons.bg.baseload})/3600000)", "energy.cons.bg.baseload");
        Trigger consEnergyTrigger = new Trigger("^power\\.cons$",
                "($-1{power.cons}/1000)*(($ts{power.cons}-$ts-1{power.cons})/3600000)", "energy.cons");

        Request triggerReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "addTriggers",
                new Object[]{new Trigger[]{bgTrigger, consTrigger, consEnergyTrigger, consBLEnergyTrigger}});
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
        logInfo("Sum bl consumption from context:\t" + sumEnergyBLConsumption + " kWh");
        logInfo("Sum consumption from context:\t" + sumEnergyConsumption + " kWh");
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
            timeProp.set("zip", 1800);
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
                    sumEnergyBLConsumption += Double.valueOf(dp.getValue());
                } else {
                    logInfo("DP after stop: " + strLocalTime(dp.getTS()));
                }
            }
        }
    }

    /**
     * Compute sum energy.cons
     *
     * @param startTS Period start
     * @param endTS   Period end
     * @return List of MetricRecord ordered by time
     */
    public final double consumedEnergyReference(final long startTS,
                                                final long endTS) {
        String query = "SELECT `metricID`, `timestamp`, `value`"
                + " FROM `" + tableName + "` "
                + " WHERE `timestamp` BETWEEN ? AND ? "
                + " AND (`metricID`=? OR `metricID`=? OR `metricID`=? ";
        for (String app : appliances) {
            query += ", OR `metricID`=?";
        }
        query += ") ORDER BY `timestamp`";

        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        double sumkWh = 0;
        double sumBLkWh = 0;
        try {
            PreparedStatement prepStmt = dbConnect.prepareStatement(query);
            prepStmt.setString(1, dfMySQL.format(startTS));
            prepStmt.setString(2, dfMySQL.format(endTS));
            prepStmt.setString(3, dbMetricImport);
            prepStmt.setString(4, dbMetricGeneration);
            prepStmt.setString(5, dbMetricExport);
            int index = 6;
            for (String app : appliances) {
                prepStmt.setString(index, app);
                index++;
            }
            ResultSet result = prepStmt.executeQuery();

            HashMap<String, Double> appWattMap = new HashMap<>();
            double importWatt = 0;
            double exportWatt = 0;
            double generationWatt = 0;
            long lastTS = -1;
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
                    double appWatt = appWattMap.values().stream().mapToDouble(Double::intValue).sum();;
                    sumBLkWh += Convert.watt2kWh(consWatt - appWatt, ts - lastTS);
                }

                if (metricID.equals(dbMetricExport)) {
                    exportWatt = value;
                } else if (metricID.equals(dbMetricImport)) {
                    importWatt = value;
                } else if (metricID.equals(dbMetricGeneration)) {
                    generationWatt = value;
                } else {
                    appWattMap.put(metricID, value);
                }
                lastTS = ts;
            }
        } catch (SQLException exception) {
            logError("SQL error while extracting reference metrics: "
                    + exception.getMessage());
        } catch (ParseException e) {
            e.printStackTrace();
        } finally {
            HelperMySQL.closeDbConnection(dbConnect);
        }
        logInfo("sum bl reference: " + sumBLkWh + " kWh");
        logInfo("Sum consumption reference:   \t" + sumkWh + " kWh");
        return sumkWh;
    }

    private void updateSource() {
        UUIDModel model = modelService.getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());
        LinkedList<String> apps = ModelHelper.findAllRunning("Appliance",
                new String[]{context.getNodeName()}, localModel);

        appliances = new LinkedList<>();
        for (String appFullName : apps) {
            String appName = appFullName.substring(appFullName.lastIndexOf(".")+1);
        }
    }

}
