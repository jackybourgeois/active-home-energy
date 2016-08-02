package org.activehome.energy.solax.emulator;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.activehome.com.RequestCallback;
import org.activehome.com.Status;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.SnapShot;
import org.activehome.predictor.Predictor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

import java.util.*;

@ComponentType
public class SolaxPredictorEmulator extends Predictor {

    @Param(defaultValue = "Load data from Solax historical data to emulate predictions")
    private String description;

    @Param(defaultValue = "solar")
    private String mongoDBName;
    @Param(defaultValue = "solar2")
    private String mongoDBCollection;
    @Param(optional = false)
    private String userId;

    private MongoDBExtractor dataExtractor;

    @Override
    public void start() {
        super.start();
        dataExtractor = new MongoDBExtractor(mongoDBName,mongoDBCollection);
    }

    @Override
    public void predict(final long startTS,
                        final long horizon,
                        final long granularity,
                        final RequestCallback callback) {
        String[] metrics = {"battery.powerDC1","battery.powerDC2","battery.feedInPower","battery.gridPower"};
        Schedule schedule = dataExtractor.extractSchedule(startTS, horizon, granularity, metrics);

        HashMap<String, MetricRecord> metricMap = schedule.getMetricRecordMap();
        MetricRecord consMR = new MetricRecord("power.cons");
        MetricRecord genMR = new MetricRecord("power.gen");
        SnapShot snapshot = new SnapShot(schedule);
        while (snapshot.next()) {
            double feedIn = Double.valueOf(snapshot.getCurrentDP("battery.feedInPower", "0").getValue());
            double gridPower = Double.valueOf(snapshot.getCurrentDP("battery.gridPower", "0").getValue());
            double powerDC1 = Double.valueOf(snapshot.getCurrentDP("battery.powerDC1", "0").getValue());
            double powerDC2 = Double.valueOf(snapshot.getCurrentDP("battery.powerDC2", "0").getValue());
            double powerCons = gridPower;
            if (feedIn<0) {
                powerCons -= feedIn;
            }
            consMR.addRecord(snapshot.getTS(), powerCons + "", "prediction", 1);
            genMR.addRecord(snapshot.getTS(), (powerDC1 + powerDC2) + "", "prediction", 1);
        }
        metricMap.put("power.cons", consMR);
        metricMap.put("power.gen", genMR);
        metricMap.remove("battery.gridPower");
        metricMap.remove("battery.feedInPower");
        metricMap.remove("battery.powerDC1");
        metricMap.remove("battery.powerDC2");
        callback.success(schedule);
    }



}
