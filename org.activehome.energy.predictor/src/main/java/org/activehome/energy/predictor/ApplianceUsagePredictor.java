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


import org.activehome.com.Request;
import org.activehome.context.data.Device;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.helper.ModelHelper;
import org.activehome.service.RequestHandler;
import org.activehome.service.Service;
import org.kevoree.ContainerRoot;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.UUIDModel;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.pmodeling.api.ModelCloner;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ArffSaver;


import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TimeZone;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public final class ApplianceUsagePredictor
        extends Service implements RequestHandler {

    @Param(defaultValue = "Predict the appliance usage.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor/docs/applianceUsagePredictor.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor/docs/applianceUsagePredictor.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor/docs/applianceUsagePredictor.kevs")
    private String demoScript;

    @Param(defaultValue = "/activehome-energy/tree/master/org.activehome.energy.predictor")
    private String src;

    /**
     * The Kevoree model.
     */
    @KevoreeInject
    private ModelService modelService;


    private LinkedList<String> interactiveSrcList;

    private Instances trainInstances;
    private Instances testInstances;

    /**
     * The map of devices considered for the balancing.
     */
    private HashMap<String, Device> deviceMap;
    /**
     * The Kevoree model cloner.
     */
    private ModelCloner cloner;

    /**
     *
     */
    @Start
    public void start() {
        super.start();
        KevoreeFactory kevFactory = new DefaultKevoreeFactory();
        cloner = kevFactory.createModelCloner();
    }

    private ApplianceUsagePredictor() {

        updateSource();

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            long start = df.parse("2013-05-01 00:00:00").getTime();

            //_train = buildIntances(start, 120);
            testInstances = buildIntances(start + 10368000000L, 30);

            ArffSaver saver = new ArffSaver();
            saver.setInstances(trainInstances);
            saver.setFile(new File("test.arff"));
            saver.writeBatch();

            trainInstances.setClassIndex(trainInstances.numAttributes() - 1);
            for (int i = 0; i < trainInstances.size(); i++) {
                if (trainInstances.get(i).dataset() == null) {
                    System.out.println("no dataset for: " + i);
                }
            }

            testInstances.setClassIndex(testInstances.numAttributes() - 1);

            MultilayerPerceptron tree = new MultilayerPerceptron();
            // train classifier
            Classifier cls = new MultilayerPerceptron();
            cls.buildClassifier(trainInstances);

            // evaluate classifier and print some statistics
            Evaluation eval = new Evaluation(trainInstances);
            eval.evaluateModel(cls, testInstances);
            System.out.println(eval.toSummaryString("\nResults\n======\n", false));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Get the handler that will execute the request.
     *
     * @param request The received request
     * @return The request handler
     */
    @Override
    protected RequestHandler getRequestHandler(final Request request) {
        return null;
    }

    private Instances initInstances() {
        ArrayList<Attribute> attr = new ArrayList<>();
        String[] labelAttr = new String[] {"H-1", "H-2", "H-3", "H-4", "H-5", "H-6", "H-7", "H-8", "H-9",
                "H-10", "H-11", "H-12", "H-13", "H-14", "H-15", "H-16", "H-17", "H-18", "H-19", "H-20",
                "H-21", "H-22", "H-23", "H-24",
                "0-1", "1-2", "2-3", "3-4", "4-5", "5-6", "6-7", "7-8", "8-9", "9-10", "10-11", "11-12",
                "12-13", "13-14", "14-15", "15-16", "16-17", "17-18", "18-19", "19-20", "20-21", "21-22",
                "22-23", "23-24",
                "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
                "Winter", "Spring", "Summer", "Autumn", "Started"};
        for (String str : labelAttr) {
            Attribute newAttr = new Attribute(str);
            attr.add(newAttr);
        }

        return new Instances("Training", attr, 365 * 24);
    }

    private double[] generateContextVector(final String metric, final long loadStartHour, final boolean started) {
//        long periodStart = loadStartHour - DAY;
        double[] contextVector = new double[60];
//
//        TreeMap<String, MetricRecord> map = _dataLoader.loadMetricRecord(metric, periodStart, loadStartHour);
//
//        if (map.size() == 0) {                                  // no data at all = no consumption
//            for (int i = 0; i < 24; i++) {
//                contextVector[i] = 0;
//            }
//        } else {
//            double[] data = EnergyHelper.normalizedLoad(map.get(metric), periodStart, DAY, HOUR);
//            for (int i = data.length - 1; i >= 0; i--) {
//                contextVector[i] = data[i] > 0 ? 1 : 0;
//            }
//        }
//
//        // hour of day
//        int hod = (int) ((loadStartHour % DAY) / DAY);
//        for (int i = 0; i < 24; i++) {
//            contextVector[i + 24] = i == hod ? 1 : 0;
//        }
//
//        // day of week
//        Calendar calendar = new GregorianCalendar();
//        calendar.setTime(new Date(loadStartHour));
//        int dow = calendar.get(Calendar.DAY_OF_WEEK);
//        for (int i = 0; i < 7; i++) {
//            contextVector[i + 48] = i == dow - 1 ? 1 : 0;
//        }
//
//        // seasons
//        int doy = calendar.get(Calendar.DAY_OF_YEAR);
//        int season = 0;
//        if (doy >= 79 && doy < 172) season = 1;
//        else if (doy >= 172 && doy < 266) season = 2;
//        else if (doy >= 266 && doy < 355) season = 3;
//        for (int i = 0; i < 4; i++) contextVector[i + 55] = i == season ? 1 : 0;
//
//        contextVector[59] = started ? 1 : 0;

        return contextVector;
    }

    private Instances buildIntances(final long start,
                                    final int nbDay) {
        Instances instances = initInstances();

//        for (int days = 0; days < nbDay; days++) {
//            //System.out.println(new Date(start));
//            LinkedList<MetricRecord> loadList = generateInteractiveLoadList(start, DAY);
//            long midnight = start - (start % DAY);
//            int index = 0;
//            for (MetricRecord load : loadList) {
//                long loadStartHour = load.getStart() - (load.getStart() % HOUR);
//
//                while (midnight + index * HOUR < loadStartHour) {
//                    double[] contextVector = generateContextVector(load.getMetricId(),
//                            midnight + index * HOUR, false);
//
//                    for (int i = 0; i < contextVector.length; i++) {
//                        System.out.print((int) contextVector[i]);
//                        if (i != contextVector.length - 1) System.out.print(",");
//                    }
//                    System.out.println();
//
//                    Instance newInstance = new BinarySparseInstance(1, contextVector);
//                    newInstance.setDataset(instances);
//                    instances.add(newInstance);
//                    index++;
//                }
//
//                double[] contextVector = generateContextVector(load.getMetricId(), loadStartHour, true);
//
//                for (int i = 0; i < contextVector.length; i++) {
//                    System.out.print((int) contextVector[i]);
//                    if (i != contextVector.length - 1) System.out.print(",");
//                }
//                System.out.println();
//
//                Instance newInstance = new BinarySparseInstance(1, contextVector);
//                newInstance.setDataset(instances);
//                instances.add(newInstance);
//            }
//
//            while (midnight + index * HOUR < midnight + DAY) {
//                double[] contextVector = generateContextVector(_metric, midnight + index * HOUR, false);
//
//                for (int i = 0; i < contextVector.length; i++) {
//                    System.out.print((int) contextVector[i]);
//                    if (i != contextVector.length - 1) System.out.print(",");
//                }
//                System.out.println();
//
//                Instance newInstance = new BinarySparseInstance(1, contextVector);
//                newInstance.setDataset(instances);
//                instances.add(newInstance);
//                index++;
//            }
//
//            start += DAY;
//        }
        return instances;
    }

    /**
     * Look at the Kevoree model to find running appliances.
     */
    private void updateSource() {
        UUIDModel model = modelService.getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());

        deviceMap = ModelHelper.findAllRunningDevice("EInteractiveApp",
                new String[]{context.getNodeName()}, localModel);
    }

    private LinkedList<MetricRecord> generateInteractiveLoadList(final long start, final long horizon) {
        updateSource();
        HashMap<String, MetricRecord> metricRecordMap = new HashMap<>();
//
//        for (String source : _interactiveSrcList) {
//            metricRecordMap.putAll(_dataLoader.loadMetricRecord(source, start, start + horizon));
//        }

        LinkedList<MetricRecord> loadList = new LinkedList<>();
//        for (String key : metricRecordMap.keySet()) {
//            loadList.addAll(EnergyHelper.loadDetector(metricRecordMap.get(key)));
//        }

        return loadList;
    }

//    public static void main(final String[] args){
//        Properties props = System.getProperties();
//        props.setProperty("activehome.tmp", System.getProperty("java.io.tmpdir") + "/activehome");
//
//        new ApplianceUsagePredictor("wm", "48");
//
//    }

}
