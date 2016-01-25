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
import org.activehome.com.RequestCallback;
import org.activehome.com.error.*;
import org.activehome.com.error.Error;
import org.activehome.context.data.MetricRecord;
import org.activehome.mysql.HelperMySQL;
import org.activehome.predictor.Predictor;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EGridCarbonIntensityPredictor extends Predictor {

    /**
     * Source of the data.
     */
    @Param
    private String urlSQLSource;
    @Param(defaultValue = "prediction.carbonIntensity")
    private String metricID;

    @Param(defaultValue = "610")
    private double biomass;
    @Param(defaultValue = "910")
    private double coal;
    @Param(defaultValue = "16")
    private double nuclear;
    @Param(defaultValue = "392")
    private double dutch_int;
    @Param(defaultValue = "83")
    private double french_int;
    @Param(defaultValue = "479")
    private double ocgt;
    @Param(defaultValue = "610")
    private double oil;
    @Param(defaultValue = "360")
    private double gas;
    @Param(defaultValue = "0")
    private double hydro;
    @Param(defaultValue = "0")
    private double wind;
    @Param(defaultValue = "699")
    private double ni_int;
    @Param(defaultValue = "300")
    private double eire_int;
    @Param(defaultValue = "0")
    private double net_pumped;

    /**
     * MySQL date parser.
     */
    private static SimpleDateFormat dfMySQL;
    private static DecimalFormat df;

    @Override
    public final void onInit() {
        super.onInit();
        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));
        df = new DecimalFormat("#.#####");
    }

    /**
     * Predict the grid carbon intensity for the given time frame.
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
        try {
            MetricRecord predictionMR = loadPrediction(startTS,
                    startTS + duration, granularity);
            addPredictionToHistory(predictionMR);
            callback.success(predictionMR);
            sendNotif(new Notif(getFullId(), getNode() + ".context",
                    getCurrentTime(), predictionMR));
        } catch (SQLException e) {
            String msg = "SQL error while predicting carbon intensity: " + e.getMessage();
            logError(msg);
            callback.error(new Error(ErrorType.METHOD_ERROR, msg));
        } catch (ParseException e) {
            String msg = "Parsing error while predicting carbon intensity: " + e.getMessage();
            logError(msg);
            callback.error(new Error(ErrorType.METHOD_ERROR, msg));
        }
    }

    private MetricRecord loadPrediction(final long startTS,
                                        final long endTS,
                                        final long granularity)
            throws SQLException, ParseException {

        ResultSet result = executeQuery(startTS, endTS);
        MetricRecord predictionMR = new MetricRecord(metricID);
        HashMap<String, Double> shareMap = new HashMap<String, Double>();
        int indexSlot = 0;
        while (result.next()) {
            double val = result.getDouble("value");
            String metric = result.getString("metricID");
            long ts = dfMySQL.parse(result.getString("ts")).getTime();

            // value for the next time slot,
            // compute intensity for the previous slot
            if (startTS + granularity * indexSlot < ts) {
                predictionMR.addRecord(startTS + granularity * indexSlot,
                        granularity, df.format(share2intensity(shareMap)), 1);
                indexSlot++;
            }

            shareMap.put(metric, val);
        }
        return predictionMR;
    }

    private ResultSet executeQuery(long startTS, long endTS)
            throws SQLException, ParseException {
        String query = "SELECT * FROM uk_grid " +
                " WHERE `ts` BETWEEN ? AND ? ORDER BY `ts`";

        Connection dbConnect = HelperMySQL.connect(urlSQLSource);

        PreparedStatement prepStmt = dbConnect.prepareStatement(query);
        prepStmt.setString(1, dfMySQL.format(new Date(startTS)));
        prepStmt.setString(2, dfMySQL.format(new Date(endTS)));
        return prepStmt.executeQuery();
    }

    private double share2intensity(Map<String, Double> shareMap) {
        return biomass * shareMap.get("biomass")
                + coal * shareMap.get("coal")
                + nuclear * shareMap.get("nuclear")
                + dutch_int * shareMap.get("dutch_int")
                + french_int * shareMap.get("french_int")
                + ocgt * shareMap.get("ocgt")
                + oil * shareMap.get("oil")
                + gas * shareMap.get("gas")
                + hydro * shareMap.get("hydro")
                + wind * shareMap.get("wind")
                + ni_int * shareMap.get("ni_int")
                + eire_int * shareMap.get("eire_int")
                + net_pumped * shareMap.get("net_pumped");
    }

}
