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


import com.eclipsesource.json.ParseException;
import org.activehome.com.Notif;
import org.activehome.context.data.DataPoint;
import org.activehome.energy.io.emulator.mysql.DataHelper;
import org.activehome.io.IO;
import org.activehome.mysql.HelperMySQL;
import org.activehome.time.TimeControlled;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EGrid extends IO {

    @Param(defaultValue = "Emulate the electricity grid status: percentage and carbon intensity of each source based on actual data.")
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
    @Param(defaultValue = "<compId>.co2.biomass,<compId>.co2.coal,<compId>.co2.nuclear,<compId>.co2.dutch_int," +
            "<compId>.co2.french_int,<compId>.co2.ocgt,<compId>.co2.oil,<compId>.co2.gas," +
            "<compId>.co2.hydro,<compId>.co2.wind,<compId>.co2.ni_int,<compId>.co2.eire_int," +
            "<compId>.co2.net_pumped,<compId>.share.biomass,<compId>.share.coal,<compId>.share.nuclear," +
            "<compId>.share.dutch_int,<compId>.share.french_int,<compId>.share.ocgt,<compId>.share.oil," +
            "<compId>.share.gas,<compId>.share.hydro,<compId>.share.wind,<compId>.share.ni_int," +
            "<compId>.share.eire_int,<compId>.share.net_pumped")
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
     * if time is paused, time when the time has been paused
     */
    private long pauseTime;

    private long endDataLoad;

    private ScheduledThreadPoolExecutor stpe;
    private HashMap<String, Integer> co2Map;
    private LinkedList<DataPoint> data;

    @Override
    public final void onStartTime() {
        super.onStartTime();
        endDataLoad = pauseTime = -1;
        data = new LinkedList<>();
        initExecutor();
        co2Map = generateCO2Map();
        loadData(getCurrentTime(), getCurrentTime() + DAY);
        scheduleNextUpdate();
    }

    @Override
    public final void onPauseTime() {
        pauseTime = getCurrentTime();
        stpe.shutdownNow();
        initExecutor();
    }

    @Override
    public final void onResumeTime() {
        if (pauseTime != -1 && !data.isEmpty()) {
            long execTime = (data.getFirst().getTS() - pauseTime) / getTic().getZip();
            stpe.schedule(this::updateValues, execTime, TimeUnit.MILLISECONDS);
        }
        scheduleNextLoadingTime();
        pauseTime = -1;
    }


    @Override
    public final void onStopTime() {
        stpe.shutdownNow();
        data = null;
    }

    private void updateValues() {
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), data.pollFirst()));
        scheduleNextUpdate();
    }

    private void scheduleNextUpdate() {
        long nextTS = data.getFirst().getTS();
        long execTime = (nextTS - getCurrentTime()) / getTic().getZip();
        stpe.schedule(this::updateValues, execTime, TimeUnit.MILLISECONDS);
    }

    public void loadData(final long startTS,
                         final long endTS) {
        String query = "SELECT `value`, `metricID`, UNIX_TIMESTAMP(`ts`)*1000 AS 'ts'"
                + " FROM " + tableName
                + " WHERE UNIX_TIMESTAMP(`ts`)*1000 BETWEEN ? AND ? ORDER BY `ts`";

        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        LinkedList<DataPoint> newData = new LinkedList<>();
        PreparedStatement prepStmt = null;
        ResultSet result = null;

        try {
            prepStmt = dbConnect.prepareStatement(query);
            prepStmt.setLong(1, startTS);
            prepStmt.setLong(2, endTS);
            result = prepStmt.executeQuery();

            long currentTS = 0;
            double carbonIntensity = 0;

            while (result.next()) {
                double val = result.getDouble("value") / 100.;
                String metricId = result.getString("metricID");
                long ts = result.getLong("ts");
                if (currentTS != 0 && currentTS != ts) {
                    newData.addLast(new DataPoint("grid.carbonIntensity", currentTS, carbonIntensity + ""));
                    currentTS = ts;
                    carbonIntensity = 0;
                } else if (currentTS == 0) {
                    currentTS = ts;
                }

                if (val > 0 && co2Map.containsKey(metricId)) {
                    carbonIntensity += val * co2Map.get(metricId);
                }
            }
            if (carbonIntensity != 0) {
                newData.addLast(new DataPoint("grid.carbonIntensity", currentTS, carbonIntensity + ""));
            }
        } catch (SQLException e) {
            logError(e.getMessage());
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } finally {
            DataHelper.closeStatement(prepStmt, this);
            DataHelper.closeResultSet(result, this);
        }

        if (data != null) {
            data.addAll(newData);
        } else {
            data = newData;
        }

        endDataLoad = endTS;
        scheduleNextLoadingTime();
    }

    public HashMap<String, Integer> generateCO2Map() {
        HashMap<String, Integer> co2Map = new HashMap<>();
        co2Map.put("biomass", 610);
        co2Map.put("coal", 910);
        co2Map.put("nuclear", 16);
        co2Map.put("dutch_int", 392);
        co2Map.put("french_int", 83);
        co2Map.put("ocgt", 479);
        co2Map.put("oil", 610);
        co2Map.put("gas", 360);
        co2Map.put("hydro", 0);
        co2Map.put("wind", 0);
        co2Map.put("ni_int", 699);
        co2Map.put("eire_int", 300);
        co2Map.put("net_pumped", 0);
        return co2Map;
    }

    private void sendNotifCO2(String metric, String value) {
        DataPoint dp = new DataPoint(getId() + ".co2." + metric, getCurrentTime(), value);
        sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(), dp));
    }

    @Override
    public void fromAPI(final String msgStr) {
    }

    private void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-emulator-grid-pool");
        });
    }

    private void scheduleNextLoadingTime() {
        long execTime;
        if (endDataLoad == -1) {
            execTime = 0;
        } else {
            execTime = (endDataLoad - 4 * HOUR - getCurrentTime()) / getTic().getZip();
        }
        stpe.schedule(() -> loadData(endDataLoad, endDataLoad + TimeControlled.DAY),
                execTime, TimeUnit.MILLISECONDS);
    }

}



