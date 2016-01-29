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
import org.activehome.test.ComponentTester;
import org.kevoree.annotation.*;
import org.kevoree.api.ModelService;

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
public class TesterEGrid extends ComponentTester {

    /**
     * Access to the Kevoree model
     */
    @KevoreeInject
    private ModelService modelService;

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
     * Name for the  import metric data
     */
    @Param(optional = false)
    private String dbMetricImport;
    /**
     * Name for the  export metric data
     */
    @Param(optional = false)
    private String dbMetricExport;
    /**
     * Name for the generation metric data
     */
    @Param(optional = false)
    private String dbMetricGeneration;

    private ScheduledThreadPoolExecutor stpe;
    private boolean testDone = false;
    private LinkedList<String> appliances;

    /**
     * MySQL date parser.
     */
    private static SimpleDateFormat dfMySQL;

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        super.onInit();
        startTS = getTic().getTS();
        stpe = new ScheduledThreadPoolExecutor(1);

        dfMySQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dfMySQL.setTimeZone(TimeZone.getTimeZone("UTC"));

        String[] metricArray = new String[]{"grid.carbonIntensity"};
        Request subscriptionReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe", new Object[]{metricArray, getFullId()});

        subscriptionReq.getEnviElem().put("userInfo", testUser());
        sendRequest(subscriptionReq, new ShowIfErrorCallback());
    }

    /**
     * On stop time, extract data from db source
     * and compare with the sum energy.cons
     */
    @Override
    public final void onStopTime() {
        //logResults(compareConsumption(startTS, startTS + getTestDuration()));
        super.onStopTime();
    }

    @Override
    public final void onPauseTime() {
        super.onPauseTime();
        stpe.schedule(this::resumeTime, 30, TimeUnit.SECONDS);
    }

    @Override
    protected final String logHeaders() {
        return "";
    }

    @Override
    protected final JsonObject prepareNextTest() {
        if (!testDone) {
            testDone = true;
            JsonObject timeProp = new JsonObject();
            timeProp.set("startDate", startDate);
            timeProp.set("zip", 1800);
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
                if (dp.getMetricId().equals("grid.carbonIntensity")
                        && dp.getTS() <= startTS + getTestDuration()) {
                    System.out.println(dp.getValue());
                }
            }
        }
    }
}