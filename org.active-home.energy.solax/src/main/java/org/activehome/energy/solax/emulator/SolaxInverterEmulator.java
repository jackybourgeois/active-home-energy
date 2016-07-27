package org.activehome.energy.solax.emulator;

/*
 * #%L
 * Active Home :: Energy :: Emulator :: Solax
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


import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.activehome.com.Notif;
import org.activehome.com.Status;
import org.activehome.context.data.DataPoint;
import org.activehome.energy.solax.SolaxInverter;
import org.activehome.io.IO;
import org.activehome.time.TimeControlled;
import org.activehome.time.TimeStatus;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Stop;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class SolaxInverterEmulator extends SolaxInverter {

    @Param(defaultValue = "Load data from Solax historical data")
    private String description;

    @Param(defaultValue = "solar")
    private String mongoDBName;
    @Param(defaultValue = "solar2")
    private String mongoDBCollection;
    @Param(optional = false)
    private String userId;

    private MongoCollection<Document> collection;
    private MongoClient client;
    private MongoDatabase database;

    /**
     * if time is paused, time when the time has been paused
     */
    private long pauseTime;
    private long endDataLoad;

    private Status status = null;

    private ScheduledThreadPoolExecutor stpe;
    private LinkedList<DataPoint[]> data;

    @Override
    public final void start() {
        super.start();
        client = new MongoClient();
        database = client.getDatabase(mongoDBName);
        collection = database.getCollection(mongoDBCollection);
    }

    @Stop
    public final void stop() {
        if (client != null) {
            client.close();
            database = null;
        }
    }

    @Override
    public final void toExecute(final String reqStr) {
    }

    public final void onInit() {
        super.onInit();
        endDataLoad = pauseTime = -1;
        data = new LinkedList<>();
        loadData(getCurrentTime(), getCurrentTime()+DAY);
    }

    @Override
    public final void onStartTime() {
        super.onStartTime();
        initExecutor();
        scheduleNextVal();
        scheduleNextLoadingTime();
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
            long execTime = (data.getFirst()[0].getTS() - pauseTime) / getTic().getZip();
            stpe.schedule(this::playNextValues, execTime, TimeUnit.MILLISECONDS);
        }
        scheduleNextLoadingTime();
        pauseTime = -1;
    }

    @Override
    public final void onStopTime() {
        stpe.shutdownNow();
    }


    @Override
    public void fromAPI(final String msgStr) {
    }

    private void playNextValues() {
        if (getTic().getStatus().equals(TimeStatus.RUNNING)) {
            if (!data.isEmpty()) {
                sendNotif(data.pollFirst());
                scheduleNextVal();
            }
        }
    }

    /**
     * Schedule the next value to play.
     */
    private void scheduleNextVal() {
        while (!data.isEmpty() && data.getFirst()[0].getTS() < getCurrentTime()) {
            playNextValues();
        }
        if (!data.isEmpty()) {
            DataPoint[] dpArray = data.getFirst();
            long execTime = (dpArray[0].getTS() - getCurrentTime()) / getTic().getZip();
            stpe.schedule(this::playNextValues, execTime, TimeUnit.MILLISECONDS);
        }
    }

    private void sendNotif(final DataPoint[] dpArray) {
        Notif notif = new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), dpArray);
        sendNotif(notif);

    }

    private void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-emulator-solax-pool");
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

    private void loadData(final long start,
                         final long end) {
        System.out.println(new Date(start) + " to " + new Date(end));
        String dateFieldName = "queryTime";
        String docName = "battery";
        Bson filter = Filters.and(
                Filters.gte(docName + "." + dateFieldName, new Date(start)),
                Filters.lte(docName + "." + dateFieldName, new Date(end)),
                Filters.eq(docName + ".id", userId));
        List<Document> docs = collection.find(filter)
                .sort(Sorts.ascending(docName + "." + dateFieldName))
                .into(new ArrayList<>());

        if (data == null) {
            data = new LinkedList<>();
        }


        for (Document doc : docs) {
            Document batDoc = doc.get(docName, Document.class);
            long ts = batDoc.getDate(dateFieldName).getTime();
            int gen1 = batDoc.getInteger("powerDC1");
            int gen2 = batDoc.getInteger("powerDC2");
            int feedIn = batDoc.getInteger("feedInPower");
            double powerCons = batDoc.getInteger("gridPower") * 1.;
            if (feedIn > 0) {
                powerCons += feedIn;
            }
            int availabilityPercent = batDoc.getInteger("surplusEnergy");
            double availability = availabilityPercent * getStorageCapacity() / 100.;
            int batteryPower = batDoc.getInteger("batteryPower");
            Status status = getStatus(batteryPower);

            DataPoint[] dpArray = new DataPoint[7];
            dpArray[0] = new DataPoint("power.cons", ts, powerCons + "");
            dpArray[1] = new DataPoint("power.gen." + getId() + "1", ts, gen1 + "");
            dpArray[2] = new DataPoint("power.gen." + getId() + "2", ts, gen2 + "");
            dpArray[3] = new DataPoint("storage.availabilityKWh", ts, availability + "");
            dpArray[4] = new DataPoint("storage.availabilityPercent", ts, availabilityPercent + "");
            dpArray[5] = new DataPoint("storage.status", ts, status + "");
            dpArray[6] = new DataPoint("power.storage", ts, batteryPower + "");
            data.addLast(dpArray);
        }

        endDataLoad = end;

        logInfo("load data, found: " + docs.size());
        logInfo("load data, total: " + data.size());
    }

    private Status getStatus(final int batteryPower) {
        if (batteryPower > 0) {
            return Status.CHARGING;
        } else if (batteryPower < 0) {
            return Status.DISCHARGING;
        }
        return Status.IDLE;
    }

}
