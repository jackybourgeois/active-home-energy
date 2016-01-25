package org.activehome.energy.io.emulator.test;

/*
 * #%L
 * Active Home :: Energy :: IO :: Emulator
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


import com.eclipsesource.json.JsonObject;
import org.activehome.com.*;
import org.activehome.com.error.Error;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Trigger;
import org.activehome.mysql.HelperMySQL;
import org.activehome.test.ComponentTester;
import org.activehome.tools.Convert;
import org.activehome.context.data.UserInfo;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class TesterEMeter extends ComponentTester {

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
     * Metric for electricity import data
     */
    @Param(optional = false)
    private String importMetric;
    /**
     * Metric for electricity export data
     */
    @Param(optional = false)
    private String exportMetric;
    /**
     * Metric for electricity generation data
     */
    @Param(optional = false)
    private String generationMetric;

    private double sumEnergyConsumption;
    private ScheduledThreadPoolExecutor stpe;

    private static final int[] nbDayArray = {1, 3, 7, 10, 14};
    private static final int[] zipArray = {3600,1800,900,300};
    private static final int[] nbPausePerDayArray = {0,1,2};
    private static final long PAUSE_DURATION = 15000;

    private int itDay = 0;
    private int itZip = 0;
    private int itPause = -1;

    /**
     * MySQL date parser.
     */
    private static SimpleDateFormat dfMySQL;

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        super.onInit();
        sumEnergyConsumption = 0;
        startTS = getTic().getTS();
        stpe = new ScheduledThreadPoolExecutor(1);

        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));

        String[] metricArray = new String[]{"energy.cons"};
        Request subscriptionReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe", new Object[]{metricArray, getFullId()});

        UserInfo testUser = new UserInfo("tester", new String[]{"user"},
                "ah", "org.activehome.energy.emulator.energy.emulator.user.EUser");
        subscriptionReq.getEnviElem().put("userInfo", testUser);
        sendRequest(subscriptionReq, null);
    }

    /**
     * On start time, schedule a pause
     */
    @Override
    public final void onStartTime() {
        super.onStartTime();
        configureTriggers();

        if (nbPausePerDayArray[itPause]>0) {
            int nbSplit = (nbPausePerDayArray[itPause]+1) * nbDayArray[itDay];
            long splitDuration = getTestDuration() / nbSplit;
            for (int i=1;i<nbSplit;i++) {
                ScheduledRequest sr = new ScheduledRequest(getFullId(),
                        getNode() + ".timekeeper", getCurrentTime(),
                        "pauseTime", startTS + splitDuration*i);
                sendToTaskScheduler(sr, new ShowIfErrorCallback());
            }
        }
    }

    private void configureTriggers() {
        Trigger genTrigger = new Trigger("(^power\\.gen\\.)+(.*?)",
                "sum(power.gen.*)", "power.gen");
        Trigger consTrigger = new Trigger("^(power\\.import|power\\.export|power\\.gen)$",
                "${power.import,0}+${power.gen,0}-${power.export,0}", "power.cons");
        Trigger consEnergyTrigger = new Trigger("^power\\.cons$",
                "($-1{power.cons}/1000)*(($ts{power.cons}-$ts-1{power.cons})/3600000)", "energy.cons");
        Trigger genEnergyTrigger = new Trigger("^power\\.gen$",
                "($-1{power.gen}/1000)*(($ts{power.gen}-$ts-1{power.gen})/3600000)", "energy.gen");
        Trigger impEnergyTrigger = new Trigger("^power\\.import$",
                "($-1{power.import}/1000)*(($ts{power.import}-$ts-1{power.import})/3600000)", "energy.import");
        Trigger expEnergyTrigger = new Trigger("^power\\.export$",
                "($-1{power.export}/1000)*(($ts{power.export}-$ts-1{power.export})/3600000)", "energy.export");

        Request triggerReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "addTriggers",
                new Object[]{new Trigger[]{genTrigger, consTrigger, consEnergyTrigger, genEnergyTrigger,
                        impEnergyTrigger, expEnergyTrigger, /*ctrlGenTrigger, ctrlExportTrigger*/}});
        sendRequest(triggerReq, new RequestCallback() {
            @Override
            public void success(final Object result) {

            }

            @Override
            public void error(final Error error) {
                logError("Error while setting triggers: " + error);
            }
        });
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
        logInfo("Sum consumption from context:\t" + sumEnergyConsumption + " kWh");
        logInfo("Sum consumption reference:   \t" + consRef + " kWh");
        return startDate + ","
                + nbDayArray[itDay] + ","
                + nbPausePerDayArray[itPause]
                + "," + zipArray[itZip]
                + "," + sumEnergyConsumption + "," + consRef;
    }

    @Override
    public final void onPauseTime() {
        super.onPauseTime();
        compareConsumption(startTS, getCurrentTime());
        stpe.schedule(this::resumeTime, PAUSE_DURATION, TimeUnit.MILLISECONDS);
    }

    @Override
    protected final String logHeaders() {
        return "start (local), Num. days, Num. pause, zip, consumption, ref. consumption";
    }

    @Override
    protected final JsonObject prepareNextTest() {
        if (itPause!=nbPausePerDayArray.length-1) {
            itPause++;
        } else if (itZip!=zipArray.length-1) {
            itPause = 0;
            itZip++;
        } else if (itDay!=nbDayArray.length-1) {
            itPause = 0;
            itZip = 0;
            itDay++;
        } else {
            return null;
        }
        JsonObject timeProp = new JsonObject();
        timeProp.set("startDate", startDate);
        timeProp.set("zip", zipArray[itZip]);
        setTestDuration(nbDayArray[itDay] + "d");
        return timeProp;
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
//                    System.out.println(Double.valueOf(dp.getValue()) + "\t" + sumEnergyConsumption + " \t  time status: " + getTic().getStatus() );
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
                + " AND (`metricID`=? OR `metricID`=? OR `metricID`=?) "
//                + " AND (`metricID`=?) "
                + " ORDER BY `timestamp`";

        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        double sumkWh = 0;
        try {
            PreparedStatement prepStmt = dbConnect.prepareStatement(query);
            prepStmt.setString(1, dfMySQL.format(startTS));
            prepStmt.setString(2, dfMySQL.format(endTS));
            prepStmt.setString(3, importMetric);
            prepStmt.setString(4, exportMetric);
            prepStmt.setString(5, generationMetric);
            ResultSet result = prepStmt.executeQuery();

            double impWatt = 0;
            double genWatt = 0;
            double expWatt = 0;
            long lastTS = -1;
            while (result.next()) {
                double value = Double.valueOf(result.getString("value"));
                long ts = dfMySQL.parse(result.getString("timestamp")).getTime();
                if (lastTS == -1) {
                    lastTS = ts;
                }
                String metric = result.getString("metricID");

                if (ts > lastTS) {
                    sumkWh += Convert.watt2kWh(impWatt + genWatt - expWatt, ts - lastTS);
                }
                if (metric.equals(importMetric)) {
                    impWatt = value;
                } else if (metric.equals(exportMetric)) {
                    if (expWatt > genWatt) expWatt = genWatt;
                    expWatt = value;
                } else if (metric.equals(generationMetric)) {
                    genWatt = value;
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

}
