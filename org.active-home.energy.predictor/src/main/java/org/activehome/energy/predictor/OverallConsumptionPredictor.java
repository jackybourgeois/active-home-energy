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


import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.RequestCallback;
import org.activehome.com.error.Error;
import org.activehome.context.com.ContextRequest;
import org.activehome.context.com.ContextResponse;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.MetricRecord;
import org.activehome.predictor.Predictor;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

import java.util.LinkedList;
import java.util.Map;

/**
 * Predict the overall energy consumption
 * for a given period and granularity (in kWh/slot).
 *
 * Algorithm:
 * - look back at the last $numOfDay days and compute
 * the average for each slot
 * TODO distinguish type of days (week day/weekend)
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class OverallConsumptionPredictor extends Predictor {

    @Param(defaultValue = "Predict the overall electricity consumption.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.predictor")
    private String src;

    /**
     * Number of days to use for the prediction.
     */
    @Param(defaultValue = "5")
    private int numOfDay;


    /**
     * Predict the overall consumption of the given time frame.
     *
     * @param startTS     Start time-stamp of the time frame
     * @param duration    Duration of the time frame
     * @param granularity Duration of each time slot
     * @param callback    where we send the result
     */
    public final void predict(final long startTS,
                              final long duration,
                              final long granularity,
                              final RequestCallback callback) {
        ContextRequest ctxReq = new ContextRequest(new String[]{"energy.cons"},
                isDiscrete(), startTS - DAY * numOfDay,
                granularity, DAY, numOfDay, "", "AVG");
        Request ctxRequest = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "extractSampleData",
                new Object[]{ctxReq, (int) (duration / granularity), granularity});
        sendRequest(ctxRequest, new RequestCallback() {
            @Override
            public void success(final Object result) {
//                MetricRecord predictionMR = new MetricRecord(getMetric(), duration);
//                predictionMR.setRecording(false);
//                for (Map.Entry<Integer, Schedule> entry
//                        : ((ContextResponse) result).getResultMap().entrySet()) {
//                    predictionMR.addRecord(startTS + entry.getKey() * granularity,
//                            granularity,
//                            entry.getValue().get(0).getRecords().get(0).getValue());
//                }
//                addPredictionToHistory(predictionMR);
//                callback.success(predictionMR);
//                sendNotif(new Notif(getFullId(), getNode() + ".context",
//                        getCurrentTime(), predictionMR));
            }

            @Override
            public void error(final Error error) {
                callback.error(error);
            }
        });
    }
}
