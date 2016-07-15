package org.activehome.energy.emulator.solax;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.activehome.com.Notif;
import org.activehome.context.data.DataPoint;
import org.activehome.io.IO;
import org.activehome.time.TimeControlled;
import org.activehome.time.TimeStatus;
import org.bson.Document;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

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
public class SolaxInverterEmulator extends IO {

    @Param(defaultValue = "Load data from Solax historical data")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.emulator.solax")
    private String src;

    /**
     * Commands.
     */
    @Param(defaultValue = "")
    private String commands;
    /**
     * Label of the metric sent to the context.
     */
    @Param(defaultValue = "<compId>.gridPower,<compId>.feedInPower,<compId>.surplusEnergy," +
            "<compId>.powerDC1,<compId>.powerDC2")
    private String metrics;


    @Param(defaultValue = "solar")
    private String mongoDBName;
    @Param(defaultValue = "solar2")
    private String mongoDBCollection;
    @Param(defaultValue = "userId")
    private String userId;

    private MongoCollection<Document> collection;


    /**
     * if time is paused, time when the time has been paused
     */
    private long pauseTime;
    private long endDataLoad;

    private ScheduledThreadPoolExecutor stpe;
    private LinkedList<DataPoint[]> data;

    @Override
    public final void start() {
        super.start();
        MongoClient client = new MongoClient();
        MongoDatabase database = client.getDatabase(mongoDBName);
        collection = database.getCollection(mongoDBCollection);
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
        loadData(getCurrentTime(), getCurrentTime() + TimeControlled.DAY);
        scheduleNextVal();
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
        if (endDataLoad == -1) {
            execTime = 0;
        } else {
            execTime = (endDataLoad - 4 * HOUR - getCurrentTime()) / getTic().getZip();
        }
        stpe.schedule(() -> loadData(endDataLoad, endDataLoad + TimeControlled.DAY),
                execTime, TimeUnit.MILLISECONDS);
    }

    public void loadData(final long start,
                                   final long end) {
        String dateFieldName = "queryTime";
        String docName = "battery";
        List<Document> docs = collection.find(
                Filters.and(
                        Filters.gte(docName + "." + dateFieldName, start),
                        Filters.lte(docName + "." + dateFieldName, end),
                        Filters.eq(docName + ".id", userId)))
                .sort(Sorts.ascending(docName + "." + dateFieldName))
                .into(new ArrayList<>());

        if (data==null) {
            data = new LinkedList<>();
        }

        for (Document doc : docs) {
            Document batDoc = doc.get(docName, Document.class);
            long ts = batDoc.getDate(dateFieldName).getTime();
            DataPoint[] dpArray = new DataPoint[6];
            dpArray[0] = new DataPoint(getId() + ".gridPower", ts, batDoc.getInteger("gridPower")+"");
            dpArray[1] = new DataPoint(getId() + ".feedInPower", ts, batDoc.getInteger("feedInPower")+"");
            dpArray[2] = new DataPoint(getId() + ".powerDC1", ts, batDoc.getInteger("powerDC1")+"");
            dpArray[3] = new DataPoint(getId() + ".powerDC2", ts, batDoc.getInteger("powerDC2")+"");
            dpArray[4] = new DataPoint(getId() + ".batteryPower", ts, batDoc.getInteger("batteryPower")+"");
            dpArray[5] = new DataPoint(getId() + ".surplusPower", ts, batDoc.getInteger("surplusEnergy")+"");
            data.addLast(dpArray);
        }
    }

}
