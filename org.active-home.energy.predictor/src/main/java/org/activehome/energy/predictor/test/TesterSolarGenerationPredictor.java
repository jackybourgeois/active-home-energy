package org.activehome.energy.predictor.test;

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


import com.eclipsesource.json.JsonObject;
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.RequestCallback;
import org.activehome.com.ScheduledRequest;
import org.activehome.com.error.Error;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Record;
import org.activehome.context.data.Trigger;
import org.activehome.test.ComponentTester;
import org.activehome.tools.file.FileHelper;
import org.activehome.context.data.UserInfo;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;
import org.kevoree.log.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class TesterSolarGenerationPredictor extends ComponentTester {

    @Param(defaultValue = "Mock up to test solar generation prediction.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.predictor")
    private String src;

    private boolean skyReady = false;
    private boolean testDataReady = false;
    private boolean testDone = false;

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        String[] metricArray = new String[]{"eval.predictor.rmsd",
                "weather.forecast.hourly.sky_cover"};
        Request subscriptionReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe", new Object[]{metricArray, getFullId()});

        UserInfo testUser = new UserInfo("tester", new String[]{"user"},
                "ah", "org.activehome.energy.emulator.energy.emulator.user.EUser");
        subscriptionReq.getEnviElem().put("userInfo", testUser);
        sendRequest(subscriptionReq, null);
    }

    /**
     * On start time, push a set of data to the context
     * and try to extract samples of these data.
     */
    @Override
    public final void onStartTime() {
        super.onStartTime();
        Trigger genTrigger = new Trigger("(^power\\.gen\\.)+(.*?)",
                "sum(power.gen.*)", "power.gen");
        Trigger genEnergyTrigger = new Trigger("^power\\.gen$",
                "($-1{power.gen}/1000)*(($ts{power.gen}-$ts-1{power.gen})/3600000)", "energy.gen");
        Trigger ctrlGenTrigger = new Trigger("^power\\.gen\\.solar",
                "(${time.dayTime,true}==true)?${triggerValue}:0", "");

        Request triggerReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "addTriggers",
                new Object[]{new Trigger[]{genTrigger, genEnergyTrigger, ctrlGenTrigger}});
        sendRequest(triggerReq, new RequestCallback() {
            @Override
            public void success(final Object o) {
                sendTestData();
            }

            @Override
            public void error(final Error error) {
                Log.error("Error while setting triggers: " + error);
            }
        });

    }

    @Override
    protected String logHeaders() {
        return "";
    }

    @Override
    protected JsonObject prepareNextTest() {
        if (!testDone) {
            JsonObject timeProp = new JsonObject();
            timeProp.set("startDate", startDate);
            timeProp.set("zip", 900);
            return timeProp;
        }
        return null;
    }

    /**
     * Push actual generation, export, import data
     * and forecast of sky coverage into the context
     * to test the predictions.
     */
    private void sendTestData() {
        BufferedReader br = null;
        String line;
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream("test_data.csv");
            br = new BufferedReader(new InputStreamReader(is));
            LinkedList<DataPoint> dataPoints = new LinkedList<>();
            while ((line = br.readLine()) != null) {
                String[] dp = line.split(",");
                if (dp[0].compareTo("Solarpv")==0) {
                    long ts = Long.valueOf(dp[2]) * 1000;
                    dataPoints.add(new DataPoint("power.gen." + dp[0], ts, dp[1]));
                }
            }
            sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(),
                    dataPoints.toArray(new DataPoint[dataPoints.size()])));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @param notifStr Notification from the context received as string
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0) {
            if (notif.getContent() instanceof String) {
                testDataReady = true;
                if (skyReady && !testDone) {
                    testDone = true;
                    testPrediction();
                }
            } else if (notif.getContent() instanceof MetricRecord
                    && (((MetricRecord) notif.getContent()).getMetricId()
                    .equals("weather.forecast.hourly.sky_cover"))) {
                skyReady = true;
                if (testDataReady && !testDone) {
                    testDone = true;
                    testPrediction();
                }
            } else if (notif.getContent() instanceof DataPoint) {
                logInfo(notif.getContent().toString());
            }
        }
    }

    private void testPrediction() {
        ScheduledRequest sr = new ScheduledRequest(getFullId(),
                getNode() + ".predictor", getCurrentTime(),
                "predict", new Object[]{getCurrentTime()+HOUR, DAY, HOUR}, getCurrentTime()+HOUR);
        sendToTaskScheduler(sr, new RequestCallback() {
            @Override
            public void success(Object result) {
                MetricRecord prediction = (MetricRecord) result;
                StringBuilder sb = new StringBuilder();
                sb.append(strLocalTime(prediction.getStartTime()));
                for (Record record : prediction.getRecords()) {
                    sb.append(",").append(record.getValue());
                }
                FileHelper.logln(sb.toString(), "solarPrediction.csv");
                testPrediction();
            }

            @Override
            public void error(Error error) {
                logError(error.getDetails());
                testPrediction();
            }
        });
    }

}