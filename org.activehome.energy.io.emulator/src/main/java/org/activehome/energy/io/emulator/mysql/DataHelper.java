package org.activehome.energy.io.emulator.mysql;

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


import org.activehome.context.data.MetricRecord;
import org.activehome.io.Appliance;
import org.activehome.io.IO;
import org.kevoree.log.Log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class DataHelper {

    /**
     * Extract a load for a given period.
     *
     * @param dbConnect access to the database
     * @param startTS   start timestamp of the period
     * @param endTS     end timestamp of the period
     * @param metricId  id of the metric
     * @return the details the load as MetricRecord
     */
    public static MetricRecord loadData(final Connection dbConnect,
                                        final String tableName,
                                        final long startTS,
                                        final long endTS,
                                        final String metricId)
            throws ParseException, SQLException {

        String query = "SELECT `metricID`, `timestamp`, `value` "
                + " FROM `" + tableName + "` "
                + " WHERE (`timestamp` BETWEEN ? AND ?) "
                + " AND `metricID`=? ORDER BY `timestamp`";

        SimpleDateFormat dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));

        MetricRecord metricRecord = new MetricRecord(metricId, endTS - startTS);
        String prevVal = null;
        PreparedStatement prepStmt = dbConnect.prepareStatement(query);
        prepStmt.setString(1, dfMySQL.format(startTS));
        prepStmt.setString(2, dfMySQL.format(endTS));
        prepStmt.setString(3, metricId);
        ResultSet result = prepStmt.executeQuery();
        while (result.next()) {
            String val = result.getString("value");
            if (prevVal == null || !val.equals(prevVal)) {
                long ts = dfMySQL.parse(
                        result.getString("timestamp")).getTime();
                metricRecord.addRecord(ts, val, 1);
                prevVal = val;
            }
        }

        return metricRecord;
    }


    public static void closeStatement(final PreparedStatement prepStmt,
                                      final IO io) {
        if (prepStmt != null) {
            try {
                prepStmt.close();
            } catch (SQLException e) {
                Log.error("[" + io.getFullId() + "] Closing statement: " + e.getMessage());
            }
        }
    }

    public static void closeResultSet(final ResultSet resultSet,
                                      final IO io) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                Log.error("[" + io.getFullId() + "] Closing resultSet: " + e.getMessage());
            }
        }
    }

}
