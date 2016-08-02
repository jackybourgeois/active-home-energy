package org.activehome.energy.solax.emulator;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Schedule;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MongoDBExtractor {

    private MongoCollection<Document> collection;
    private MongoClient client;
    private MongoDatabase database;

    public MongoDBExtractor(final String dbName,
                            final String collectionName) {
        client = new MongoClient();
        database = client.getDatabase(dbName);
        collection = database.getCollection(collectionName);
    }

    public Schedule extractSchedule(final long startTS,
                                    final long horizon,
                                    final long granularity,
                                    final String[] metrics) {
        String dateFieldName = "queryTime";
        String docName = "battery";
        Bson filter = Filters.and(
                Filters.gte(docName + "." + dateFieldName, new Date(startTS)),
                Filters.lte(docName + "." + dateFieldName, new Date(startTS + horizon)),
                Filters.eq(docName + ".id", userId));
        List<Document> docs = collection.find(filter)
                .sort(Sorts.ascending(docName + "." + dateFieldName))
                .into(new ArrayList<>());

        Schedule schedule = new Schedule("SolaxPrediction", startTS, horizon, granularity);
        MetricRecord genMR = new MetricRecord("power.gen", horizon);
        schedule.getMetricRecordMap().put(genMR.getMetricId(), genMR);
        MetricRecord consMR = new MetricRecord("power.cons", horizon);
        schedule.getMetricRecordMap().put(consMR.getMetricId(), consMR);
        for (Document doc : docs) {
            Document batDoc = doc.get(docName, Document.class);
            long ts = batDoc.getDate(dateFieldName).getTime();
            int gen = batDoc.getInteger("powerDC1") + batDoc.getInteger("powerDC2");
            int feedIn = batDoc.getInteger("feedInPower");
            double powerCons = batDoc.getInteger("gridPower") * 1.;
            if (feedIn > 0) {
                powerCons += feedIn;
            }
            consMR.addRecord(ts, powerCons + "", 1);
            genMR.addRecord(ts, gen + "", 1);
        }
    }

}
