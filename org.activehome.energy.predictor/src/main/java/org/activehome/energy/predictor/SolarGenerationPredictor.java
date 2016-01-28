package org.activehome.energy.predictor;

/*
 * #%L
 * Active Home :: Energy :: Predictor
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
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.RequestCallback;;
import org.activehome.com.error.Error;
import org.activehome.com.error.ErrorType;
import org.activehome.context.com.ContextRequest;
import org.activehome.context.com.ContextResponse;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.SampledRecord;
import org.activehome.predictor.Predictor;
import org.activehome.context.data.UserInfo;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;

import java.util.LinkedList;
import java.util.Map;

/**
 * Predict the energy solar generation
 * for a given period and granularity (in kWh/slot).
 * <p>
 * Algorithm:
 * - look back at the history data to determine the maximum
 * generation possible for each slot
 * - multiply this max with the sky coverage forecast
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class SolarGenerationPredictor extends Predictor {

    @Param(defaultValue = "Predict the solar generation.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor/docs/solarGenerationPredictor.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor/docs/solarGenerationPredictor.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor/docs/solarGenerationPredictor.kevs")
    private String demoScript;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor")
    private String src;

    /**
     * Number of days to use for the prediction.
     */
    @Param(defaultValue = "5")
    private int numOfDay;
    /**
     * Number of days to use for the prediction.
     */
    @Param(defaultValue = "weather.forecast.skyCover")
    private String skyCoverMetric;
    /**
     * Last update of sky forecast received from the context.
     */
    private MetricRecord latestSkyCoverForecast;

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        latestSkyCoverForecast = null;
        String[] metricArray = new String[]{skyCoverMetric};
        Request subscriptionReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe",
                new Object[]{metricArray, getFullId()});
        sendRequest(subscriptionReq, null);
    }

    /**
     * Predict the overall consumption of the given time frame.
     *
     * @param startTS     Start time-stamp of the time frame
     * @param duration    Duration of the time frame
     * @param granularity Duration of each time slot
     * @param callback    where we send the result
     */
    @Override
    public final void predict(final long startTS,
                              final long duration,
                              final long granularity,
                              final RequestCallback callback) {
        ContextRequest ctxReq = new ContextRequest(new String[]{"energy.gen"},
                isDiscrete(), startTS - DAY * numOfDay,
                granularity, DAY, numOfDay, "", "MAX");
        Request ctxRequest = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "extractSampleData",
                new Object[]{ctxReq, (int) (duration / granularity), granularity});
        sendRequest(ctxRequest, new RequestCallback() {
            @Override
            public void success(final Object result) {
                MetricRecord predictionMR = new MetricRecord(getMetric(), duration);
                predictionMR.setRecording(false);

                // check we have sky cover forecast over the entire prediction period
//                if (latestSkyCoverForecast != null) {
//                    if (latestSkyCoverForecast.getStartTime() <= startTS
//                            && latestSkyCoverForecast.getStartTime()
//                            + latestSkyCoverForecast.getTimeFrame() >= startTS + duration) {
//                        int skyCoverOffset = 0;
//                        SampledRecord sky = (SampledRecord) latestSkyCoverForecast.getRecords().get(0);
//                        for (Map.Entry<Integer, Schedule> entry
//                                : ((ContextResponse) result).getResultMap().entrySet()) {
//                            double genStart = entry.getValue().get(0).getStartTime();
//                            SampledRecord gen = (SampledRecord) entry.getValue().get(0).getRecords().get(0);
//                            while (sky.getTS() + sky.getDuration() < genStart
//                                    && skyCoverOffset < latestSkyCoverForecast.getRecords().size() - 1) {
//                                skyCoverOffset++;
//                                sky = (SampledRecord) latestSkyCoverForecast.getRecords().get(skyCoverOffset);
//                            }
//                            double skyFactor = 1 - (sky.getValue() / 100.);
//                            predictionMR.addRecord(startTS + entry.getKey() * granularity,
//                                    granularity, gen.getValue()*skyFactor);
//                        }
//                        addPredictionToHistory(predictionMR);
//                        callback.success(predictionMR);
//                        sendNotif(new Notif(getFullId(), getNode() + ".context",
//                                getCurrentTime(), predictionMR));
//                    } else {
//                        callback.error(new Error(ErrorType.METHOD_ERROR,
//                                "Sky cover forecast does not cover"
//                                + " the entire period to predict"));
//                    }
//                } else {
//                    callback.error(new Error(ErrorType.METHOD_ERROR, "Missing sky cover forecast."));
//                }

            }

            @Override
            public void error(final Error error) {
                callback.error(error);
            }
        });
    }

    /**
     * @param notifStr The notif received from the context
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0
                && notif.getContent() instanceof MetricRecord) {
            MetricRecord mr = (MetricRecord) notif.getContent();
            if (mr.getMetricId().equals(skyCoverMetric)) {
                latestSkyCoverForecast = mr;
//                StringBuilder sb = new StringBuilder();
//                for (Record record : latestSkyCoverForecast.getRecords()) {
//                    sb.append(record.getValue()).append(",");
//                }
//                logInfo("sky forecast: " + sb);
            }
        }
    }

}
