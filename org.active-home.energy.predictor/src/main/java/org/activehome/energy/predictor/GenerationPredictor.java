package org.activehome.energy.predictor;

/*
 * #%L
 * Active Home :: Energy :: Predictor
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


/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public final class GenerationPredictor {

//    private Calendar calendar = GregorianCalendar.getInstance();
//    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//    private boolean wbWinnerOnLastSlot;
//    private long lastEval;
//    private DecimalFormat tf = new DecimalFormat("0.000");
//
//    public GenerationPredictor() {
//        lastEval = 0;
//    }
//
//    /**
//     *
//     * @param start
//     * @param horizon
//     * @param granularity
//     */
//    public void predict(final double start,
//                        final double horizon,
//                        final double granularity) {
//        // forecast up to 2hrs in advance (recent past could be better till that)
//        if (fifteenMinSlotInAdvance < 8) {
//            // which algo was the best to predict the last 15 minutes of generation?
//            if (currentTS - lastEval > 900000) {
//                lastEval = currentTS;
//                double last15Gen = actualGeneration(currentTS - QUARTER);
//                double last15ForecastWB = weatherBased(currentTS - fifteenMinSlotInAdvance * QUARTER, fifteenMinSlotInAdvance);
//                double last15ForecastRP = recentPast(currentTS - fifteenMinSlotInAdvance * QUARTER);
//                // measure the diff between actual gen and the 2 prediction
//                wbWinnerOnLastSlot = !(Math.abs(last15Gen - last15ForecastRP) < Math.abs(last15Gen - last15ForecastWB));
//            }
//            if (!wbWinnerOnLastSlot) return recentPast(currentTS);
//        }
//        return weatherBased(currentTS, fifteenMinSlotInAdvance);
//    }
//
//    public double weatherBased(final long timestamp,
//                               final int fifteenMinSlotInAdvance) {
//        LinkedList<Double> energyDouble = historicalGeneration(household, timestamp + fifteenMinSlotInAdvance * 900000);
//        double skyForecast = skyForecast(timestamp, fifteenMinSlotInAdvance / 4);
//        if (energyDouble.size() > 0) return max(energyDouble) * (1 - skyForecast);
//        return -1;
//    }
//
//    public double recentPast(final long timestamp) {
//        return actualGeneration(household, timestamp - QUARTER);
//    }
//
//    public double skyForecast(final long timestamp,
//                              final int hourInAdvance) {
//        if (hourInAdvance > 0) {
//            try {
//                ResultSet result = dbConnect.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)
//                        .executeQuery("SELECT `timestamp`, `values` FROM weather_forecast_hourly wfh " +
//                                " WHERE `metricID`='sky_cover' AND `timestamp`<= '" + df.format(new Date(timestamp)) + "'" +
//                                " ORDER BY `timestamp` DESC LIMIT 1");
//                if (result.next()) {
//                    // no weather prediction after 35 hour in advance, we take the last prediction
//                    if (hourInAdvance > 34) hourInAdvance = 34;
//                    return Double.valueOf(result.getString("values").split(",")[hourInAdvance - 1]) / 100;
//                }
//            } catch (SQLException e) {
//                logger.error("SQL error: " + e.getMessage());
//            }
//        }
//        return 0;
//    }
//
//    public double actualGeneration(Household household, long timestamp) {
//        int startTimeSlot = (int) ((timestamp % 86400000) / ScheduledLoad.RESOLUTION);
//        Day currentDay = household.getDayList().getLast();
//        double sum = 0;
//        for (int i = startTimeSlot; i < startTimeSlot + 15; i++) {
//            sum += currentDay.getDetailedGeneration()[i];
//        }
//        return sum;
//    }
//
//    public LinkedList<Double> historicalGeneration(Household household, long timestamp) {
//        int startTimeSlot = (int) ((timestamp % 86400000) / ScheduledLoad.RESOLUTION);
//        LinkedList<Double> _dataList = new LinkedList<>();
//        for (int numDay = 0; numDay < household.getDayList().size() - 1; numDay++) {
//            double sum = 0;
//            for (int i = startTimeSlot; i < startTimeSlot + 15 && i < household.getDayList().get(numDay).getDetailedGeneration().length; i++) {
//                sum += household.getDayList().get(numDay).getDetailedGeneration()[i];
//            }
//            _dataList.addLast(sum);
//        }
//        return _dataList;
//    }
//
//    public LinkedList<Data> historicalWeather(long timestamp, int nbPrevDay) {
//        LinkedList<Data> dataList = new LinkedList<>();
//        calendar.setTime(new Date(timestamp));
//        int startHour = calendar.get(Calendar.HOUR_OF_DAY);
//        int endHour = startHour < 23 ? startHour + 1 : 0;
//        int startMinute = ((int) (calendar.get(Calendar.MINUTE) / 15)) * 15;
//        int endMinute = startMinute < 45 ? startHour + 15 : 0;
//        try {
//            ResultSet result = dbConnect.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)
//                    .executeQuery("SELECT `timestamp`, AVG(`values`) AS 'value' FROM weather_forecast_hourly wfh " +
//                            " WHERE `metricID`='sky_cover'" +
//                            " AND HOUR(`timestamp`) BETWEEN " + startHour + " AND " + endHour +
//                            " AND MINUTE(`timestamp`) BETWEEN " + startMinute + " AND " + endMinute +
//                            " AND (`timestamp` BETWEEN '" + df.format(new Date(timestamp - DAY)) + "'" +
//                            "                   AND '" + df.format(new Date(timestamp)) + "') " +
//                            " GROUP BY  ( 4 * HOUR( `timestamp` ) + FLOOR( MINUTE( `timestamp` ) / 15 )) " +
//                            " ORDER BY `timestamp`");
//            while (result.next()) {
//                dataList.addLast(new Data("generation_forecast", result.getTimestamp("timestamp").getTime(), result.getDouble("value") / 100));
//            }
//        } catch (SQLException e) {
//            logger.error("SQL error: " + e.getMessage());
//        }
//        return dataList;
//    }
//
//    public Double max(LinkedList<Double> valList) {
//        double max = valList.getFirst();
//        for (Double val : valList) {
//            if (val > max) max = val;
//        }
//        return max;
//    }
//
//
//    public double forecast(final int account,
//                           final long timestamp,
//                           final int fifteenMinSlotInAdvance) {
//        // forecast up to 2hrs in advance (recent past could be better till that)
//        if (fifteenMinSlotInAdvance < 8) {
//            // which algo was the best to predict the last 15 minutes of generation?
//            if (timestamp - lastEval > QUARTER) {
//                lastEval = timestamp;
//                double last15Gen = actualGeneration(timestamp - QUARTER, account);
//                double last15ForecastWB = forecastWeatherBased(timestamp - (fifteenMinSlotInAdvance + 1) * 900000, fifteenMinSlotInAdvance, 10, account);
//                double last15ForecastRP = forecastRecentPast(timestamp - (fifteenMinSlotInAdvance + 1) * 900000, account);
//                // measure the diff between actual gen and the 2 prediction
//                wbWinnerOnLastSlot = !(Math.abs(last15Gen - last15ForecastRP) < Math.abs(last15Gen - last15ForecastWB));
//            }
//            if (!wbWinnerOnLastSlot) return forecastRecentPast(timestamp, account);
//        }
//        return forecastWeatherBased(timestamp, fifteenMinSlotInAdvance, 10, account);
//    }
//
//    public double forecastWeatherBased(long timestamp, int fifteenMinSlotInAdvance, int prevDayHistory, int account) {
//        LinkedList<Double> energyList = historicalGeneration(timestamp + fifteenMinSlotInAdvance * 900000, prevDayHistory, account);
//        //LinkedList<Data> weatherData = historicalWeather(timestamp+hourInAdvance*3600000, prevDayHistory);
//        double skyForecast = skyForecast(timestamp, fifteenMinSlotInAdvance / 4);
//        if (energyList.size() > 0) return max(energyList) * (1 - skyForecast);
//        return -1;
//    }
//
//    public double forecastRecentPast(long timestamp, int account) {
//        return actualGeneration(timestamp - 900000, account);
//    }
//
//    public double actualGeneration(long timestamp, int account) {
//        try {
//            ResultSet result = dbConnect.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)
//                    .executeQuery("SELECT `timestamp`, AVG(`value`) AS 'value' FROM raw_data_" + account + " rd " +
//                            " JOIN `metrics` m ON m.metricID=rd.metricID AND m.userID='" + account + "'" +
//                            " WHERE m.`type`='GEN'" +
//                            " AND (`timestamp` BETWEEN '" + df.format(new Date(timestamp)) + "'" +
//                            "                   AND '" + df.format(new Date(timestamp + 900000)) + "') " +
//                            " ORDER BY `timestamp`");
//            if (result.next()) {
//                return result.getDouble("value");
//            }
//        } catch (SQLException e) {
//            logger.error("SQL error: " + e.getMessage());
//        }
//        return 0;
//    }
//
//    /*
//     * provide list of 15-minute energy generation history
//     * @param timestamp starting timestamp (time)
//     * @param nbPrevDay nb previous days provided
//     * @return
//     */
//    public LinkedList<Double> historicalGeneration(long timestamp, int nbPrevDay, int account) {
//        LinkedList<Double> dataList = new LinkedList<>();
//        calendar.setTime(new Date(timestamp));
//        int hour = calendar.get(Calendar.HOUR_OF_DAY);
//        int startMinute = ((int) (calendar.get(Calendar.MINUTE) / 15)) * 15;
//        int endMinute = startMinute < 45 ? startMinute + 15 : 59;
//        SunsetSunrise ss = new SunsetSunrise(52.041404, -0.72878, new Date(timestamp), 0);
//        try {
//            ResultSet result = dbConnect.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)
//                    .executeQuery("SELECT `timestamp`, AVG(`value`) AS 'value' FROM raw_data_" + account + " rd " +
//                            " JOIN `metrics` m ON m.metricID=rd.metricID AND m.userID='" + account + "'" +
//                            " WHERE m.`type`='GEN'" +
//                            " AND HOUR(`timestamp`)=" + hour +
//                            " AND MINUTE(`timestamp`) BETWEEN " + startMinute + " AND " + endMinute +
//                            " AND (`timestamp` BETWEEN '" + df.format(new Date(timestamp - nbPrevDay * 86400000)) + "'" +
//                            "                   AND '" + df.format(new Date(timestamp)) + "') " +
//                            " GROUP BY  DAY( `timestamp` )" +
//                            " ORDER BY `timestamp`");
//            while (result.next()) {
//                if (ss.isDaytime()) {
//                    dataList.addLast(result.getDouble("value"));
//                } else {
//                    dataList.addLast(0.);
//                }
//            }
//        } catch (SQLException e) {
//            logger.error("SQL error: " + e.getMessage());
//        }
//        return dataList;
//    }
//
//
//    public Data max(LinkedList<Data> dataList) {
//        Data max = dataList.getFirst();
//        for (Data data : dataList) {
//            if (data.getValue() > max.getValue()) {
//                max = data;
//            }
//        }
//        return max;
//    }


}
