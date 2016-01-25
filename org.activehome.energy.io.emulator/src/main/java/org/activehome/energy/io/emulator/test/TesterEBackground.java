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
import org.activehome.context.com.ContextRequest;
import org.activehome.context.com.ContextResponse;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Trigger;
import org.activehome.mysql.HelperMySQL;
import org.activehome.test.ComponentTester;
import org.activehome.tools.Convert;
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
public class TesterEBackground extends ComponentTester {

    /**
     * Default duration of simulation pause.
     */
    private static final long PAUSE_DURATION = 15000;
    /**
     * Default compression time of simulation.
     */
    private static final long ZIP = 1800;

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
     * Name for the  background metric data
     */
    @Param(optional = false)
    private String dbMetricId;

    private double sumEnergyConsumption;
    private ScheduledThreadPoolExecutor stpe;
    private boolean testDone = false;

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

        String[] metricArray = new String[]{"energy.cons","power.cons.bg.*"};
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
    }

    private void configureTriggers() {
        Trigger bgTrigger = new Trigger("(^power\\.cons\\.bg\\.)+(.*?)",
                "sum(power.cons.bg.*)", "power.cons.bg");
        Trigger consTrigger = new Trigger("^power\\.cons\\.bg$",
                "${power.cons.bg,0}", "power.cons");
        Trigger consEnergyTrigger = new Trigger("^power\\.cons$",
                "($-1{power.cons}/1000)*(($ts{power.cons}-$ts-1{power.cons})/3600000)", "energy.cons");

        Request triggerReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "addTriggers",
                new Object[]{new Trigger[]{bgTrigger, consTrigger, consEnergyTrigger}});
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
        logInfo("Sum bg consumption from context:\t" + sumEnergyConsumption + " kWh");
        logInfo("Sum bg consumption reference:   \t" + consRef + " kWh");
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
                } else {
                    logInfo("DP after stop: " + strLocalTime(dp.getTS()));
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
        String query = "SELECT `metricID`, `timestamp`, `value`"
                + " FROM `" + tableName + "` "
                + " WHERE `timestamp` BETWEEN ? AND ? "
                + " AND `metricID`=? "
                + " ORDER BY `timestamp`";

        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        double sumkWh = 0;
        try {
            PreparedStatement prepStmt = dbConnect.prepareStatement(query);
            prepStmt.setString(1, dfMySQL.format(startTS));
            prepStmt.setString(2, dfMySQL.format(endTS));
            prepStmt.setString(3, dbMetricId);
            ResultSet result = prepStmt.executeQuery();

            double bgWatt = 0;
            long lastTS = -1;
            while (result.next()) {
                double value = Double.valueOf(result.getString("value"));
                long ts = dfMySQL.parse(result.getString("timestamp")).getTime();
                if (lastTS == -1) {
                    lastTS = ts;
                }
                sumkWh += Convert.watt2kWh(bgWatt, ts - lastTS);
                bgWatt = value;
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

}
