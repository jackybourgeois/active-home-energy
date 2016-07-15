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


import com.eclipsesource.json.JsonObject;
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.Status;
import org.activehome.context.data.DataPoint;
import org.activehome.energy.io.emulator.mysql.DataHelper;
import org.activehome.io.BackgroundAppliance;
import org.activehome.io.action.Command;
import org.activehome.mysql.HelperMySQL;
import org.activehome.time.TimeControlled;
import org.activehome.time.TimeStatus;
import org.activehome.tools.Convert;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EBackgroundApp extends BackgroundAppliance {

    /**
     * Last value sent, the current power.
     */
    protected double currentPower;
    /**
     * Scheduler playing the load values at the right time.
     */
    protected ScheduledThreadPoolExecutor stpe;
    @Param(defaultValue = "Background device emulator based on actual data.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.io.emulator")
    private String src;
    /**
     * Commands.
     */
    @Param(defaultValue = "START,STOP,STATUS")
    private String commands;
    /**
     * Metrics
     */
    @Param(defaultValue = "power.cons.bg.<compId>,status.app.<compId>")
    private String metrics;
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
     * metric id in the database.
     */
    @Param(optional = false)
    private String dbMetricId;
    /**
     * The maximum power the appliance can use
     */
    @Param(defaultValue = "170")
    private double maxPower;
    /**
     * Time maximum to be stopped before restarting automatically.
     */
    @Param(defaultValue = "900000")
    private long maxOffTime;
    /**
     * Data ready to send.
     */
    private LinkedList<DataPoint> data;
    /**
     * if stopped, time when the appliance has been stopped.
     */
    private long appStopTime;
    /**
     * if time is paused, time when the time has been paused
     */
    private long pauseTime;

    private long endDataLoad;
    /**
     * The current appliance status.
     */
    private Status status;
    /**
     * The duration for which the device has to stay ON.
     */
    private long lockTime;
    /**
     * What was the power at stop time, used to calculate missed energy.
     */
    private double powerAtStoppedTime;

    private Future nextLoadingFuture;
    private Future nextValFuture;

    /**
     * @param reqStr Command received as string
     */
    @Input
    public final void ctrl(final String reqStr) {
        JsonObject json = JsonObject.readFrom(reqStr);
        if (json.get("dest").asString().compareTo(getFullId()) == 0) {
            Request request = new Request(json);
            switch (Command.fromString(request.getMethod())) {
                case START:
                    startLoad();
                    break;
                case STOP:
                    stopLoad();
                    break;
                case STATUS:
                    publishStatus();
                    break;
                default:
            }
        }
    }

    // == == == Time life cycle == == ==

    @Override
    public final void onStartTime() {
        super.onStartTime();
        data = new LinkedList<>();
        endDataLoad = pauseTime = appStopTime = lockTime = -1;
        initExecutor();
        updateCurrentPower(getTic().getTS(), 0);
        loadData(getCurrentTime(), getCurrentTime() + TimeControlled.DAY);
        scheduleNextVal();
    }

    @Override
    public final void onPauseTime() {
        super.onPauseTime();
        pauseTime = getCurrentTime();
        nextLoadingFuture.cancel(true);
        nextValFuture.cancel(true);
    }

    @Override
    public final void onResumeTime() {
        super.onResumeTime();
        if (pauseTime != -1) {
            if (appStopTime != -1) {    // the appliance was stopped when time has been paused
                long execTime = (appStopTime + maxOffTime - pauseTime) / getTic().getZip();
                nextValFuture = stpe.schedule(this::startLoad, execTime, TimeUnit.MILLISECONDS);
            } else {
                if (!data.isEmpty()) {  // the appliance was running when time has been paused
                    DataPoint dp = data.getFirst();
                    long execTime = (dp.getTS() - pauseTime) / getTic().getZip();
                    nextValFuture = stpe.schedule(this::playNextValue, execTime, TimeUnit.MILLISECONDS);
                }
            }
            scheduleNextLoadingTime();
        }
        pauseTime = -1;
    }

    @Override
    public final void onStopTime() {
        super.onStopTime();
        stpe.shutdownNow();
    }

    // == == == Load life cycle == == ==

    /**
     *
     */
    public final void startLoad() {
        if (!status.equals(Status.ON)) {
            nextValFuture.cancel(true);
            logInfo("Start");
            long startTime = getCurrentTime();
            if (appStopTime != -1) {
                double energyMissedKWh = energyMissedWhenStopped(startTime);
                String metricId = data.get(0).getMetricId();
                distributeMissedEnergy(energyMissedKWh, startTime, metricId);
                updateStatus(Status.LOCKED_ON);
                scheduleNextVal();
            }
            appStopTime = -1;
        } else {
            logInfo("Already ON");
        }
    }


    /**
     *
     */
    public final void stopLoad() {
        if (status.equals(Status.ON)) {
            logInfo("Stopped");
            if (nextValFuture != null) {
                nextValFuture.cancel(true);
            }
            appStopTime = getCurrentTime();
            powerAtStoppedTime = currentPower;
            updateCurrentPower(appStopTime, 0);

            // schedule auto start after 'maxOffTime'
            // in case no one else restart it
            long delay = maxOffTime / getTic().getZip();
            nextValFuture = stpe.schedule(this::startLoad, delay, TimeUnit.MILLISECONDS);
        } else if (status.equals(Status.LOCKED_ON)) {
            logInfo("Cannot be stopped (Locked ON till " + strLocalTime(lockTime) + ")");
        } else if (status.equals(Status.OFF)) {
            logInfo("Already OFF");
        }
    }

    /**
     * Distribute the energy missed during the off time
     * by setting following data points at the max power.
     *
     * @param energyMissedKWh Energy missed during off time
     * @param startTime       timestamp of appliance restart
     * @param metricId        id for the new DataPoints
     */
    private void distributeMissedEnergy(final double energyMissedKWh,
                                        final long startTime,
                                        final String metricId) {
        // How much can we correct on the next slot?
        double correctionKWh = correctionOverTimeSlot(0);
        double remainingEnergy = energyMissedKWh;
        // if the next slot cannot correct what we've missed,
        // loop through the followings
        int i = 0;
        if (correctionKWh <= remainingEnergy) {
            if (correctionKWh > 0) {
                data.addFirst(new DataPoint(metricId,
                        startTime, maxPower + ""));
                remainingEnergy -= correctionKWh;
            }
            boolean lastBit = false;
            i++;
            while (remainingEnergy > 0 && !lastBit) {
                correctionKWh = correctionOverTimeSlot(i);
                if (correctionKWh > 0 && correctionKWh <= remainingEnergy) {
                    data.set(i, new DataPoint(metricId,
                            data.get(i).getTS(), maxPower + ""));
                    remainingEnergy -= correctionKWh;
                    i++;
                } else if (correctionKWh < 0) {
                    i++;
                } else {
                    lastBit = true;
                }
            }
            lockTime = data.get(i).getTS();
            logInfo("Locked on till " + strLocalTime(lockTime) + ")");
        }
        // last bit, calculate remaining time to stay at max power
        setRemainingTimeAtMaxPower(i, remainingEnergy, metricId);
    }

    /**
     * By how much energy we can increase the next slot
     * (distance between actual and max power).
     *
     * @param index index of the slot in data list
     * @return the amount of energy that can be corrected
     */
    private double correctionOverTimeSlot(final int index) {
        double power = maxPower - Double.valueOf(data.get(index).getValue());
        long duration = data.get(index + 1).getTS() - data.get(index).getTS();
        return Convert.watt2kWh(power, duration);
    }

    /**
     * Add a DataPoint after all the corrected DataPoint
     * to correct the very last part.
     *
     * @param index           Where to add the DataPoint
     * @param energyMissedKWh The remaining energy missed
     * @param metricId        The metric of the new DataPoint
     */
    private void setRemainingTimeAtMaxPower(final int index,
                                            final double energyMissedKWh,
                                            final String metricId) {
        if (energyMissedKWh > 0) {
            double powerValToDelay = Double.valueOf(data.get(index).getValue());
            double powerToAdd = (maxPower - powerValToDelay) / 1000.;
            long remainingTimeAtMaxPower =
                    (long) ((energyMissedKWh / powerToAdd) * TimeControlled.HOUR);

            data.set(index, new DataPoint(metricId,
                    data.get(index).getTS(), maxPower + ""));
            data.add(index + 1, new DataPoint(metricId, data.get(index).getTS()
                    + remainingTimeAtMaxPower, powerValToDelay + ""));
            lockTime = data.get(index + 1).getTS();
        }
    }

    /**
     * Send the next value of the running load.
     */
    private void playNextValue() {
        if (getTic().getStatus().equals(TimeStatus.RUNNING) && appStopTime == -1) {
            if (!data.isEmpty()) {
                DataPoint dp = data.pollFirst();
                updateCurrentPower(dp.getTS(), Double.valueOf(dp.getValue()));
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
            updateCurrentPower(dp.getTS(), Double.valueOf(dp.getValue()));
        }
        if (!data.isEmpty()) {
            DataPoint dp = data.getFirst();
            long execTime = (dp.getTS() - getCurrentTime()) / getTic().getZip();
            nextValFuture = stpe.schedule(this::playNextValue, execTime, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Load the data to play for a given period.
     *
     * @param startTS start timestamp of the period
     * @param endTS   end timestamp of the period
     * @return the list of DataPoint to play
     */
    private void loadData(final long startTS,
                          final long endTS) {
        String query = "SELECT `metricID`, UNIX_TIMESTAMP(d.`timestamp`)*1000 AS 'timestamp', `value`"
                + " FROM `" + tableName + "` d"
                + " WHERE UNIX_TIMESTAMP(d.`timestamp`)*1000 BETWEEN ? AND ? AND `metricID`=?"
                + " ORDER BY `timestamp`";
        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        LinkedList<DataPoint> dataPoints = new LinkedList<>();
        PreparedStatement prepStmt = null;
        ResultSet result = null;
        try {
            prepStmt = dbConnect.prepareStatement(query);
            prepStmt.setLong(1, startTS);
            prepStmt.setLong(2, endTS);
            prepStmt.setString(3, dbMetricId);
            result = prepStmt.executeQuery();

            Double prevVal = null;
            while (result.next()) {
                Double value = Double.valueOf(result.getString("value"));
                long ts = result.getLong("timestamp");
                if (prevVal == null || !prevVal.equals(value)) {
                    dataPoints.addLast(new DataPoint("power.cons.bg." + getId(),
                            ts, value + ""));
                    prevVal = value;
                }
            }
        } catch (SQLException exception) {
            logError("SQL error while extracting data: " + exception.getMessage());
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
     * Check appliance status based on onOffThreshold.
     */
    public final void checkStatus() {
        if ((status == null || status.equals(Status.ON))
                && currentPower <= getOnOffThreshold()) {
            updateStatus(Status.OFF);
        } else if ((status == null || status.equals(Status.OFF))
                && currentPower > getOnOffThreshold()) {
            updateStatus(Status.ON);
        } else if (status != null && status.equals(Status.LOCKED_ON)
                && getCurrentTime() > lockTime) {
            if (currentPower <= getOnOffThreshold()) {
                updateStatus(Status.OFF);
            } else {
                updateStatus(Status.ON);
            }
        }
    }

    /**
     * @param ts    timestamp of the power update
     * @param value new power rate
     */
    private void updateCurrentPower(final long ts,
                                    final double value) {
        currentPower = value;
        DataPoint powerDP = new DataPoint("power.cons.bg." + getId(),
                ts, value + "");
        Notif notif = new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), powerDP);
        sendNotif(notif);
        checkStatus();
    }

    /**
     * @param reqStr Request received from the Task Scheduler as string
     */
    @Override
    public final void toExecute(final String reqStr) {
        JsonObject json = JsonObject.readFrom(reqStr);
        if (json.get("dest").asString().compareTo(getFullId()) == 0) {
            switch (json.get("method").asString()) {
                case "startLoad":
                    startLoad();
                    break;
                default:
            }
        }
    }

    /**
     * @param startTime When the appliance restarted
     * @return Amount of energy lost during off time (kWh)
     */
    public final double energyMissedWhenStopped(final long startTime) {
        double power = powerAtStoppedTime;
        long duration = data.get(0).getTS() - appStopTime;
        double energyMissedKWh = Convert.watt2kWh(power, duration);

        while (data.get(1).getTS() < startTime) {
            power = Double.valueOf(data.get(0).getValue());
            duration = data.get(1).getTS() - data.get(0).getTS();
            energyMissedKWh += Convert.watt2kWh(power, duration);
            data.removeFirst();
        }

        power = Double.valueOf(data.getFirst().getValue());
        duration = startTime - data.getFirst().getTS();
        energyMissedKWh += Convert.watt2kWh(power, duration);

        return energyMissedKWh;
    }


    /**
     * Push notif with current status.
     */
    public final void publishStatus() {
        DataPoint dp = new DataPoint("status.app." + getId(),
                getCurrentTime(), status.name());
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), dp));
    }

    /**
     * @param newStatus The new status
     */
    public final void updateStatus(final Status newStatus) {
        status = newStatus;
        publishStatus();
    }

    private void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-emulator-bg-pool");
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

        nextLoadingFuture = stpe.schedule(() -> loadData(startTS, startTS + TimeControlled.DAY),
                execTime, TimeUnit.MILLISECONDS);
    }

}
