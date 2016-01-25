package org.activehome.energy.io.emulator;

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


import org.activehome.com.Notif;
import org.activehome.context.data.DataPoint;
import org.activehome.energy.io.emulator.mysql.DataHelper;
import org.activehome.io.MicroGeneration;
import org.activehome.mysql.HelperMySQL;
import org.activehome.time.TimeControlled;
import org.activehome.time.TimeStatus;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

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
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EMicroGeneration extends MicroGeneration {
    /**
     * Commands.
     */
    @Param(defaultValue = "")
    private String commands;
    /**
     * Metrics
     */
    @Param(defaultValue = "power.gen.<compId>")
    private String metrics;
    /**
     * Source of the data.
     */
    @Param
    private String urlSQLSource;
    /**
     * Table that contains the data.
     */
    @Param
    private String tableName;
    /**
     * metric id in the database.
     */
    @Param
    private String dbMetricId;
    /**
     * SQL date format.
     */
    private SimpleDateFormat dfMySQL;
    /**
     * if time is paused, time when the time has been paused
     */
    private long pauseTime;

    private long endDataLoad;

    /**
     * Last value sent, the current power.
     */
    private double currentPower;
    private ScheduledThreadPoolExecutor stpe;
    private LinkedList<DataPoint> data;

    @Override
    public final void start() {
        super.start();
        currentPower = 0;
        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public final void toExecute(final String reqStr) {
    }

    @Override
    public final void onStartTime() {
        super.onStartTime();
        endDataLoad = pauseTime = -1;
        data = new LinkedList<>();
        initExecutor();
        DataPoint dp = new DataPoint(metrics.replace("<compId>", getId()),
                getCurrentTime(), currentPower + "");
        updateCurrentPower(dp);
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
            long execTime = (data.getFirst().getTS() - pauseTime) / getTic().getZip();
            stpe.schedule(this::playNextValue, execTime, TimeUnit.MILLISECONDS);
        }
        scheduleNextLoadingTime();
        pauseTime = -1;
    }

    @Override
    public final void onStopTime() {
        super.onStopTime();
        stpe.shutdownNow();
    }

    @Override
    public void fromAPI(final String msgStr) {
    }

    private void sendNotif(final long sequenceNumber,
                           final String sequence,
                           final DataPoint dp) {
        Notif notif = new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), dp);
        notif.setSequence(sequence);
        notif.setSequenceNumber(sequenceNumber);
        sendNotif(notif);
    }

    /**
     * Load the data to play for a given period.
     *
     * @param startTS start timestamp of the period
     * @param endTS   end timestamp of the period
     * @return the list of DataPoint to play
     */
    public final void loadData(final long startTS,
                               final long endTS) {
        String query = "SELECT `metricID`, `timestamp`, `value`"
                + " FROM `" + tableName + "` d"
                + " WHERE `timestamp` BETWEEN ? AND ? AND `metricID`=?"
                + " ORDER BY `timestamp`";
        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        LinkedList<DataPoint> dataPoints = new LinkedList<>();
        PreparedStatement prepStmt = null;
        ResultSet result = null;

        try {
            prepStmt = dbConnect.prepareStatement(query);
            prepStmt.setString(1, dfMySQL.format(startTS));
            prepStmt.setString(2, dfMySQL.format(endTS));
            prepStmt.setString(3, dbMetricId);
            result = prepStmt.executeQuery();
            String metricId = metrics.replace("<compId>", getId());
            int i = 0;
            while (result.next()) {
                String value = result.getString("value");
                long ts = dfMySQL.parse(result.getString("timestamp")).getTime();
                dataPoints.addLast(new DataPoint(metricId, ts, value));
                i++;
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

    private void playNextValue() {
        if (getTic().getStatus().equals(TimeStatus.RUNNING)) {
            if (!data.isEmpty()) {
                updateCurrentPower(data.pollFirst());
                scheduleNextVal();
            }
        }
    }

    /**
     * Schedule the next value to play.
     */
    private void scheduleNextVal() {
        while (!data.isEmpty() && data.getFirst().getTS() < getCurrentTime()) {
            updateCurrentPower(data.pollFirst());
        }
        if (!data.isEmpty()) {
            DataPoint dp = data.getFirst();
            long execTime = (dp.getTS() - getCurrentTime()) / getTic().getZip();
            stpe.schedule(this::playNextValue, execTime, TimeUnit.MILLISECONDS);
        }
    }

    private void updateCurrentPower(final DataPoint dp) {
        currentPower = Double.valueOf(dp.getValue());
        sendNotif(-1, "", dp);
    }

    private void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-emulator-mg-pool");
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