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
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Trigger;
import org.activehome.energy.io.emulator.mysql.DataHelper;
import org.activehome.energy.library.EnergyHelper;
import org.activehome.io.action.Command;
import org.activehome.mysql.HelperMySQL;
import org.activehome.test.ComponentTester;
import org.activehome.tools.Convert;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Output;
import org.kevoree.annotation.Param;
import org.kevoree.api.Port;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Test the EInteractiveApp component.
 *  - test 1: set appliance load and start
 *  - test 2: pause/resume simulation during load
 *  - test 3: pause/resume simulation between set and start
 *  - test 4: pause/resume the load
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class TesterEInteractive extends ComponentTester {

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
     * Name for the  background metric data.
     */
    @Param(optional = false)
    private String dbMetricId;

    /**
     * Sum energy consumption as they arrive from context notif.
     */
    private double sumEnergyConsumption;
    /**
     * Scheduler to request appliance start at a given time.
     */
    private ScheduledThreadPoolExecutor stpe;
    /**
     * false till all the test are done.
     */
    private int testNumber = 3;
    /**
     * MySQL date parser.
     */
    private static SimpleDateFormat dfMySQL;

    /**
     * On init, subscribe to energy.cons and power.inter.* .
     */
    @Override
    public final void onInit() {
        super.onInit();
        sumEnergyConsumption = 0;
        startTS = getTic().getTS();
        stpe = new ScheduledThreadPoolExecutor(1);

        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));

        String[] metricArray = new String[]{"energy.cons", "power.cons.inter.*"};
        Request subscriptionReq = new Request(getFullId(),
                getNode() + ".context",
                getCurrentTime(), "subscribe",
                new Object[]{metricArray, getFullId()});

        subscriptionReq.getEnviElem().put("userInfo", testUser());
        sendRequest(subscriptionReq, new ShowIfErrorCallback());
    }

    /**
     * On start time, schedule a pause.
     */
    @Override
    public final void onStartTime() {
        super.onStartTime();
        configureTriggers();
        scheduleInteractiveLoad();
    }

    @Override
    public final void onPauseTime() {
        super.onPauseTime();
        compareConsumption(startTS, getCurrentTime());
        stpe.schedule(this::resumeTime, PAUSE_DURATION, TimeUnit.MILLISECONDS);
    }

    /**
     * On stop time, extract data from db source
     * and compare with the sum energy.cons.
     */
    @Override
    public final void onStopTime() {
        logResults(compareConsumption(startTS, startTS + getTestDuration()));
        super.onStopTime();
    }

    /**
     * Get the interactive load for the given period
     * from the database and schedule the requests for
     * their starts.
     */
    private void scheduleInteractiveLoad() {

        LinkedList<MetricRecord> loadList = null;
        try {
            Connection dbConnect = HelperMySQL.connect(urlSQLSource);
            MetricRecord metricRecord = DataHelper.loadData(dbConnect, tableName,
                    getCurrentTime(), getCurrentTime() + DAY, dbMetricId);
            loadList = EnergyHelper.loadDetector(metricRecord, 0, 1);
        } catch (SQLException e) {
            logError("Database error: " + e.getMessage());
        } catch (ParseException e) {
            logError("Parsing error: " + e.getMessage());
        }

        if (loadList != null) {
            for (MetricRecord load : loadList) {
                long delay = (load.getStartTime() - getCurrentTime()) / ZIP;
                stpe.schedule(() -> setAppliance(load),
                        delay - MINUTE / ZIP, TimeUnit.MILLISECONDS);
                stpe.schedule(() -> ctrlApp(load.getMetricId(), Command.START),
                        delay, TimeUnit.MILLISECONDS);
                switch (testNumber) {
                    case 2: // pause simulation during load
                        stpe.schedule(this::pauseTime,
                                delay + (load.getTimeFrame() / 2) / ZIP,
                                TimeUnit.MILLISECONDS);
                        break;
                    case 3: // pause simulation between SET and START
                        stpe.schedule(this::pauseTime,
                                delay + (MINUTE / 2) / ZIP,
                                TimeUnit.MILLISECONDS);
                        break;
                    case 4: // pause the appliance load
                        stpe.schedule(() -> ctrlApp(load.getMetricId(), Command.PAUSE),
                                delay + (load.getTimeFrame() / 2) / ZIP,
                                TimeUnit.MILLISECONDS);
                        stpe.schedule(() -> ctrlApp(load.getMetricId(), Command.RESUME),
                                delay + ((load.getTimeFrame() / 2) + QUARTER) / ZIP,
                                TimeUnit.MILLISECONDS);
                        break;
                    default:
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
        if (toInteractive != null
                && toInteractive.getConnectedBindingsSize() > 0) {
            toInteractive.send(new Request(getFullId(),
                    getNode() + "." + applianceId,
                    getCurrentTime(), cmd.name()).toString(), null);
        }
    }

    /**
     * Send request to the context setting triggers.
     */
    private void configureTriggers() {
        // Sum interactive power into power.cons.inter
        Trigger[] triggers = new Trigger[3];
        triggers[0] = new Trigger("(^power\\.cons\\.inter\\.)+(.*?)",
                "sum(power.cons.inter.*)", "power.cons.inter");
        // Copy power.cons.inter into power.cons (only consumption of the test)
        triggers[1] = new Trigger("^power\\.cons\\.inter$",
                "${power.cons.inter,0}", "power.cons");
        // Convert power.cons into energy.cons
        triggers[2] = new Trigger("^power\\.cons$",
                "($-1{power.cons}/1000)*(($ts{power.cons}-$ts-1{power.cons})/3600000)",
                "energy.cons");

        Request triggerReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "addTriggers", new Object[]{triggers});
        sendRequest(triggerReq, new ShowIfErrorCallback());
    }

    /**
     * Compare actual consumption (directly from the database)
     * against the collected consumption from the context.
     *
     * @param start start time of the period to compare
     * @param end   end time of the period to compare
     * @return csv line result ready to store in a file
     */
    private String compareConsumption(final long start,
                                      final long end) {
        double consRef = consumedEnergyReference(start, end);
        logInfo("Test duration: " + (getTestDuration() / DAY) + " days");
        logInfo("Sum inter consumption from context:\t"
                + sumEnergyConsumption + " kWh");
        logInfo("Sum inter consumption reference:   \t" + consRef + " kWh");
        return startDate + "," + testNumber + ","
                + sumEnergyConsumption + "," + consRef;
    }

    @Override
    protected final String logHeaders() {
        return "start (local), test number, interactive consumption,"
                + " ref. interactive consumption";
    }

    @Override
    protected final JsonObject prepareNextTest() {
        testNumber++;
        if (testNumber < 5) {
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
//                    logInfo("DP after stop: " + strLocalTime(dp.getTS()) + " val: " + dp.getValue());
                    sumEnergyConsumption += Double.valueOf(dp.getValue());
                } else {
                    logInfo("DP after stop: " + strLocalTime(dp.getTS()));
                }
            }
        }
    }

    /**
     * Compute sum energy.cons.
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

            double interWatt = 0;
            long lastTS = -1;
            while (result.next()) {
                double value = Double.valueOf(result.getString("value"));
                long ts = dfMySQL.parse(
                        result.getString("timestamp")).getTime();
                if (lastTS == -1) {
                    lastTS = ts;
                }
                sumkWh += Convert.watt2kWh(interWatt, ts - lastTS);
                interWatt = value;
                lastTS = ts;
            }
        } catch (SQLException exception) {
            logError("SQL error while extracting"
                    + " import, export and generation references: "
                    + exception.getMessage());
        } catch (ParseException e) {
            logError("Parsing error: " + e.getMessage());
        } finally {
            HelperMySQL.closeDbConnection(dbConnect);
        }
        return sumkWh;
    }



}
