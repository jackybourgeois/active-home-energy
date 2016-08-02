package org.activehome.energy.battery.test;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.activehome.context.data.DataPoint;
import org.activehome.energy.battery.*;
import org.activehome.tools.file.FileHelper;
import org.bson.Document;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class OfflineTest {

    public static void main(String[] args) {
        MongoClient client = new MongoClient();
        MongoDatabase database = client.getDatabase("solar");
        MongoCollection<Document> collection = database.getCollection("solar2");

        double energyUnit = 100;
        BatteryInfo batteryInfo = new BatteryInfo(2500, 2500, 20, 100, 9.6, 0.82, 0.87);
        User user = new User("223c3bcf0bd16c30b5024838ab9257b5", 9.6, batteryInfo);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        try {
            Date start = df.parse("2016-05-04 00:00:00");
            Date end = df.parse("2016-05-10 00:00:00");
//            chartOldestYoungest(collection, user, start, end, energyUnit);
//            chartAutonomy(collection, user, start, end);
            chartSelfConsumption(collection, user, start, end);
        } catch (ParseException e) {

        }
    }

    public static void chartEnergyAge(final MongoCollection<Document> collection,
                                      final User user,
                                      final Date start,
                                      final Date end,
                                      final double energyUnit) {
        List<Document> docs = retrieve(collection, "battery", "queryTime", user.getId(), start, end);

        String dataStr = "['Time', 'Average current age', 'Average last hour']";

        EnergyAge energyAge = new EnergyAge(energyUnit, user.getBatteryInfo(), false);
        for (Document doc : docs) {
            Document batDoc = doc.get("battery", Document.class);
            long ts = batDoc.getDate("queryTime").getTime();
            double batteryPower = batDoc.getInteger("batteryPower");
            energyAge.updateAge(new DataPoint("batPower", ts, batteryPower + ""));
            double avgCurrentAge = energyAge.averageCurrentAge(ts);
            double avgLastHour = energyAge.averageLastHourAge(ts);
            dataStr += ", [new Date(" + ts + ")," + avgCurrentAge + "," + avgLastHour + "]";
        }

        String title = "Average battery age - " + user.getId();

        generateLineChart(title, dataStr, user.getId(), start, end);
    }

    public static void chartCurrentAge(final MongoCollection<Document> collection,
                                       final User user,
                                       final Date start,
                                       final Date end,
                                       final double energyUnit) {
        List<Document> docs = retrieve(collection, "battery", "queryTime", user.getId(), start, end);

        String dataStr = "['Time', 'Current age']";

        EnergyAge energyAge = new EnergyAge(energyUnit, user.getBatteryInfo(), true);
        long prevTS = -1;
        for (Document doc : docs) {
            Document batDoc = doc.get("battery", Document.class);
            long ts = batDoc.getDate("queryTime").getTime();
            double batteryPower = batDoc.getInteger("batteryPower");
            LinkedList<EnergyUnit> changes = energyAge.updateAge(new DataPoint("batPower", ts, batteryPower + ""));

            if (prevTS != -1 && changes.size() > 0) {
                long interval = (ts - prevTS) / changes.size();
                long time = prevTS + interval;
                for (EnergyUnit eUnit : changes) {
                    if (eUnit.isConsumed()) {
                        double currentAge = eUnit.getAge();
                        dataStr += ", [new Date(" + time + ")," + currentAge + "]";
                    }
                    time += interval;
                }
            }
            prevTS = ts;

        }

        String title = "Current age - " + user.getId();

        generateLineChart(title, dataStr, user.getId(), start, end);
    }

    public static void chartOldestYoungest(final MongoCollection<Document> collection,
                                           final User user,
                                           final Date start,
                                           final Date end,
                                           final double energyUnit) {
        List<Document> docs = retrieve(collection, "battery", "queryTime", user.getId(), start, end);

        String dataStr = "['Time', 'Oldest Unit', 'Youngest Units']";

        EnergyAge energyAge = new EnergyAge(energyUnit, user.getBatteryInfo(), false);
        for (Document doc : docs) {
            Document batDoc = doc.get("battery", Document.class);
            long ts = batDoc.getDate("queryTime").getTime();
            double batteryPower = batDoc.getInteger("batteryPower");
            energyAge.updateAge(new DataPoint("batPower", ts, batteryPower + ""));
            dataStr += ", [new Date(" + ts + "),";
            EnergyUnit oldestUnit = energyAge.oldestUnit();
            dataStr += oldestUnit != null ? ((ts - oldestUnit.getTsIn()) / 3600000.) + "," : "null,";
            EnergyUnit youngestUnit = energyAge.youngestUnit();
            dataStr += youngestUnit != null ? ((ts - youngestUnit.getTsIn()) / 3600000.) + "]" : "null]";
        }

        String title = "Oldest and Youngest unit - " + user.getId();

        generateLineChart(title, dataStr, user.getId(), start, end);
    }

    public static void chartAutonomy(final MongoCollection<Document> collection,
                                     final User user,
                                     final Date start,
                                     final Date end) {
        List<Document> docs = retrieve(collection, "battery", "queryTime", user.getId(), start, end);

        String dataStr = "['Time', 'Autonomy (based on current consumption)']";
        Autonomy autonomy = new Autonomy(user.getBatteryInfo());
        for (Document doc : docs) {
            Document batDoc = doc.get("battery", Document.class);
            long ts = batDoc.getDate("queryTime").getTime();
            int feedIn = batDoc.getInteger("feedInPower");
            double powerCons = batDoc.getInteger("gridPower") * 1.;
            if (feedIn > 0) {
                powerCons += feedIn;
            }
            autonomy.setCurrentConsumption(powerCons);
            autonomy.setCurrentSoCPercent(batDoc.getInteger("surplusEnergy"));
            dataStr += ", [new Date(" + ts + "," + autonomy.basedOnCurrentCons(ts) + "),";
        }

        String title = "Autonomy - " + user.getId();

        generateLineChart(title, dataStr, user.getId(), start, end);
    }

    public static void chartDelivery(final MongoCollection<Document> collection,
                                     final User user,
                                     final Date start,
                                     final Date end) {
        List<Document> docs = retrieve(collection, "battery", "queryTime", user.getId(), start, end);

        String dataStr = "['Time', 'Delivery time (based on current generation)']";
        Delivery delivery = new Delivery(user.getBatteryInfo());
        for (Document doc : docs) {
            Document batDoc = doc.get("battery", Document.class);
            long ts = batDoc.getDate("queryTime").getTime();
            double powerGen = batDoc.getInteger("powerDC1") + batDoc.getInteger("powerDC2");
            delivery.setGeneration(powerGen);
            delivery.setCurrentSoCPercent(batDoc.getInteger("surplusEnergy"));
            dataStr += ", [new Date(" + ts + "," + delivery.basedOnCurrentGen(ts) + "),";
        }

        String title = "Generation - " + user.getId();

        generateLineChart(title, dataStr, user.getId(), start, end);
    }

    public static void chartSelfConsumption(final MongoCollection<Document> collection,
                                            final User user,
                                            final Date start,
                                            final Date end) {
        List<Document> docs = retrieve(collection, "battery", "queryTime", user.getId(), start, end);

        String dataStr = "['Time', 'Self-Consumption (% Generation)', 'Consumption (W)', 'Feed-In (W)' , 'Generation (W)']";

        for (Document doc : docs) {
            Document batDoc = doc.get("battery", Document.class);
            long ts = batDoc.getDate("queryTime").getTime();
            double powerGen = batDoc.getInteger("powerDC1") + batDoc.getInteger("powerDC2");
            int feedIn = batDoc.getInteger("feedInPower");
            double powerCons = batDoc.getInteger("gridPower") * 1.;
            double self = powerCons;
            double selfGen;
            if (feedIn < 0) {
                powerCons += feedIn*-1;
                selfGen = 100;
            } else {
                selfGen = (powerGen-feedIn)/powerGen * 100.;
            }
            double selfCons = self/powerCons * 100.;
            if (selfCons<0) selfCons=0;
            if (selfGen<0) selfGen=0;

            dataStr += ", [new Date(" + ts + ")," + selfGen + "," + powerCons + "," + feedIn + "," + powerGen +  " ]";
        }

        String title = "Self-Consumption - " + user.getId();

        String options = "title: '" + title + "', \n" +
                "legend: { position: 'bottom' }\n, " +
                "series: {\n" +
                "          0: {targetAxisIndex: 0},\n" +
                "          1: {targetAxisIndex: 1},\n" +
                "          2: {targetAxisIndex: 1},\n" +
                "          3: {targetAxisIndex: 1},\n" +
                "          4: {targetAxisIndex: 0}\n" +
                "        },\n" +
                "        vAxes: {\n" +
                "          0: {title: 'Self-Consumption (%)'},\n" +
                "          1: {title: 'Power (W)'}\n" +
                "        }";

        generateLineChart(title, dataStr, options, user.getId(), start, end);
    }


    public static List<Document> retrieve(final MongoCollection<Document> collection,
                                          final String docName,
                                          final String dateFieldName,
                                          final String userId,
                                          final Date start,
                                          final Date end) {
        return collection.find(
                Filters.and(
                        Filters.gte(docName + "." + dateFieldName, start),
                        Filters.lte(docName + "." + dateFieldName, end),
                        Filters.eq(docName + ".id", userId)))
                .sort(Sorts.ascending(docName + "." + dateFieldName))
                .into(new ArrayList<>());
    }

    public static void generateLineChart(final String title,
                                         final String data,
                                         final String source,
                                         final Date startDate,
                                         final Date endDate) {

        String options = "title: '" + title + " - " + startDate + " - " + endDate + "', \n" +
                "legend: { position: 'bottom' }";
        generateLineChart(title, data, options, source, startDate, endDate);
    }

    public static void generateLineChart(final String title,
                                         final String data,
                                         final String options,
                                         final String source,
                                         final Date startDate,
                                         final Date endDate) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        String html = FileHelper.fileToString("line.html", ClassLoader.getSystemClassLoader());
        html = html.replace("${data}", data)
                .replace("${options}", options);

        FileHelper.save(html, title + "_" + df.format(startDate) + "_" + df.format(endDate) + ".html");
    }


}
