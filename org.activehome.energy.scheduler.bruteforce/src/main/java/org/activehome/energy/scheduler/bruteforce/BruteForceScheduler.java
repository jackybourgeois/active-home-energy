package org.activehome.energy.scheduler.bruteforce;

/*
 * #%L
 * Active Home :: Energy :: Scheduler :: Brute Force
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
import org.activehome.com.*;
import org.activehome.com.error.Error;
import org.activehome.com.error.ErrorType;
import org.activehome.context.data.*;
import org.activehome.context.data.Device;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.helper.ModelHelper;
import org.activehome.energy.library.EnergyHelper;
import org.activehome.energy.library.oc.LoadSequence;
import org.activehome.energy.library.oc.OneLoadAtATime;
import org.activehome.energy.library.oc.OperationalConstraint;
import org.activehome.service.RequestHandler;
import org.activehome.service.Service;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;
import org.kevoree.api.handler.UUIDModel;
import org.kevoree.log.Log;
import org.kevoree.ContainerRoot;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.UUIDModel;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.pmodeling.api.ModelCloner;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class BruteForceScheduler extends Service implements RequestHandler {

    @Param(defaultValue = "Use exhaustive search to find alternative schedule of interactive appliance's loads.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.scheduler.bruteforce/docs/bruteForceScheduler.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.scheduler.bruteforce/docs/bruteForceScheduler.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.scheduler.bruteforce/docs/bruteForceScheduler.kevs")
    private String demoScript;

    @Param(defaultValue = "/activehome-energy/tree/master/org.activehome.energy.scheduler.bruteforce")
    private String src;

    @Param(defaultValue = "pushRequest>TimeKeeper.getRequest,"
            + "getResponse>TimeKeeper.pushResponse,"
            + "getNotif>Context.pushNotif,"
            + "getNotif>Context.pushDataToSystem")
    private String bindingScheduler;

    /**
     * To get a local copy of Kevoree model.
     */
    private ModelCloner cloner;

    @Override
    protected RequestHandler getRequestHandler(Request request) {
        return this;
    }

    @Start
    public void start() {
        super.start();
        KevoreeFactory kevFactory = new DefaultKevoreeFactory();
        cloner = kevFactory.createModelCloner();
    }

    @Override
    public final void onInit() {
        Request request = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe",
                new Object[]{new String[]{"schedule.prediction.power"}, getFullId()});
        sendRequest(request, new ShowIfErrorCallback());
    }

    /**
     * Search the best version of the given schedule
     * based on the objectives taken from the context.
     *
     * @param schedule the schedule to look at
     * @param callback alternative Schedule or Error
     */
    public final void schedule(final Schedule schedule,
                               final RequestCallback callback) {
        new Thread(() -> {
            HashMap<String, Device> negotiables = negotiableDeviceMap();
            LinkedList<MetricRecord> loads =
                    extractNegotiableLoads(schedule, negotiables);
            if (loads.size() == 0) {
                callback.error(new Error(ErrorType.NOTHING_TO_SCHEDULE,
                        "No events, nothing to schedule."));
            } else {
                if (getTic() != null && getTic().getZip() > 1) {
                    controlTime("pause");
                }
                double[] baseload = extractBaseLoad(schedule, negotiables);
                ObjectiveBuilder oBuilder = new ObjectiveBuilder(this, loads, schedule, baseload);
                BruteForceScheduler scheduler = this;
                oBuilder.build(new RequestCallback() {
                    @Override
                    public void success(Object obj) {
                        LinkedList<SchedulingObjective> objectives =
                                (LinkedList<SchedulingObjective>) obj;
                        LinkedList<OperationalConstraint> constraints =
                                prepareConstraints(loads, negotiables,
                                        schedule.getNbSlot(), schedule.getGranularity());

                        BruteForceAlgo algo = new BruteForceAlgo(scheduler, schedule,
                                loads, objectives, constraints);
                        algo.startScheduling();
                        HashMap<int[], double[]> dominantMap = algo.getDominantSolution();

                        if (dominantMap != null) {
                            logInfo("Optimisation done, nb dominant=" + dominantMap.size());
                        } else {
                            logInfo("No solution (Nothing to shift)");
                        }

                        // select best score/solution
                        boolean first = first = true;
                        int[] bestSolution = null;
                        double[] bestScore = null;

                        if (dominantMap != null) {
                            for (Map.Entry entry : dominantMap.entrySet()) {
                                if (first) {
                                    bestSolution = (int[]) entry.getKey();
                                    bestScore = (double[]) entry.getValue();
                                    first = false;
                                }
                            }

                            System.out.println("nb loads: " + loads.size());
                            for (MetricRecord load : loads) {
                                System.out.println(load.getMetricId() + " => records: " + load.getRecords().size() + " "
                                        + strLocalTime(load.getStartTime()));
                            }

                            LinkedList<MetricRecord> solutionMR =
                                    solutionToMR(negotiables, loads, bestSolution, schedule);

                            int[] originSolution = toSolution(loads, schedule);
                            double[] originMeaningfulScore = algo.meaningfulEvalForEachObjective(originSolution,
                                    algo.shiftAndNormalize(originSolution));
                            double[] bestMeaningfulScore = algo.meaningfulEvalForEachObjective(bestSolution,
                                    algo.shiftAndNormalize(bestSolution));

                            Episode episode = buildEpisode(schedule, loads, objectives,
                                    bestSolution, originSolution, bestMeaningfulScore, originMeaningfulScore);

                            Schedule alterSchedule = buildAlternativeSchedule(
                                    schedule, solutionMR, objectives, episode,
                                    bestMeaningfulScore, originMeaningfulScore);


                            if (getTic() != null && getTic().getZip() > 1) {
                                controlTime("resume");
                            }

                            callback.success(alterSchedule);
                            sendNotif(new Notif(getFullId(), getNode() + ".context",
                                    getCurrentTime(), alterSchedule));
                        } else {
                            callback.error(new Error(ErrorType.METHOD_ERROR,
                                    "No solution, the dominant map is null."));
                        }
                    }

                    @Override
                    public void error(Error error) {
                        callback.error(error);
                    }
                });
            }
        },  getFullId() + " schedule").start();

    }

    /**
     * For each schedule received, generate an alternative schedule.
     *
     * @param notifStr the Notif as string
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0
                && notif.getContent() instanceof Schedule) {
            Schedule schedule = (Schedule) notif.getContent();
            schedule(schedule, new ShowIfErrorCallback());
        }
    }

    private LinkedList<MetricRecord> solutionToMR(final HashMap<String, Device> negotiables,
                                                  final LinkedList<MetricRecord> loads,
                                                  final int[] solution,
                                                  final Schedule schedule) {
        LinkedList<MetricRecord> solutionMR = new LinkedList<>();

        for (String key : negotiables.keySet()) {
            String metricId = key.substring(key.lastIndexOf(".") + 1);
            MetricRecord mr = new MetricRecord("power.cons.inter." + metricId,
                    schedule.getHorizon(), "alternative", schedule.getStart(), "0", 0);
            for (int i = 0; i < loads.size(); i++) {
                MetricRecord load = loads.get(i);
                if (load.getMetricId().substring(load.getMetricId().lastIndexOf(".") + 1).equals(metricId)) {
                    load.getRecords();
                    long newStart = schedule.getStart() + schedule.getGranularity() * solution[i];
                    mr.mergeRecords(new MetricRecord(newStart, load), "prediction", "alternative", true);
                }
            }
            if (mr != null) {
                solutionMR.add(mr);
            }
        }

        return solutionMR;
    }

    /**
     * Build the list of constraints
     */
    private LinkedList<OperationalConstraint> prepareConstraints(
            final LinkedList<MetricRecord> loads,
            final HashMap<String, Device> devices,
            final int nbSlot,
            final long granularity) {
        HashMap<String, OperationalConstraint> constraints = new HashMap<>();

        int i = 0;
        for (MetricRecord load : loads) {
            String deviceName = load.getMetricId().substring(load.getMetricId().lastIndexOf(".") + 1);
            if (!constraints.containsKey(load.getMetricId())) {
                constraints.put(deviceName, new OneLoadAtATime(nbSlot, granularity, deviceName));
            }
            constraints.get(deviceName).addLoad(load, i);

            String sequences = devices.get(getNode() + "." + deviceName)
                    .getAttributeMap().get("sequences");
            if (sequences != null) {
                for (String sequenceName : sequences.split(",")) {
                    if (!sequenceName.equals("")) {
                        if (!constraints.containsKey("seq_" + sequenceName)) {
                            constraints.put("seq_" + sequenceName,
                                    new LoadSequence("seq_" + sequenceName, granularity));
                        }
                        constraints.get("seq_" + sequenceName).addLoad(load, i);
                    }
                }
            }

            i++;
        }

        LinkedList<OperationalConstraint> ocList = new LinkedList<>(constraints.values());
        return ocList;
    }


    /**
     * Go through all MetricRecord of the schedule and extract
     * the loads from the negotiable devices.
     *
     * @param schedule the schedule to scan
     * @return list of loads (can be multiple from the same device)
     */
    private LinkedList<MetricRecord> extractNegotiableLoads(final Schedule schedule,
                                                            final HashMap<String, Device> negotiableDeviceMap) {
        LinkedList<MetricRecord> loadList = new LinkedList<>();
        for (MetricRecord mr : schedule.getMetricRecordMap().values()) {
            String deviceName = mr.getMetricId().substring(mr.getMetricId().lastIndexOf("."));
            if (negotiableDeviceMap.containsKey(getNode() + deviceName)) {
                loadList.addAll(EnergyHelper.loadDetector(mr,
                        negotiableDeviceMap.get(getNode() + deviceName), schedule.getGranularity()));
            }
        }
        return loadList;
    }

    /**
     * build a normalized load summing all non negotiable consumption.
     */
    private double[] extractBaseLoad(final Schedule schedule,
                                     final HashMap<String, Device> negotiableDeviceMap) {

        HashMap<String, Device> applianceMap = findRunningDevice("Appliance");
        double[] bl = new double[schedule.getNbSlot()];
        for (int i = 0; i < bl.length; i++) {
            bl[i] = 0;
        }
        for (String key : schedule.getMetricRecordMap().keySet()) {
            if (key.startsWith("power.cons.")) {
                String devName = getNode() + key.substring(key.lastIndexOf("."));
                if (!negotiableDeviceMap.containsKey(devName)) {
                    double[] devCons = EnergyHelper.normalizedLoad(schedule.getMetricRecordMap().get(key),
                            schedule.getStart(), schedule.getHorizon(), schedule.getGranularity());
                    for (int i = 0; i < bl.length; i++) {
                        bl[i] += devCons[i];
                    }
                }
            }
        }

        for (int i = 0; i < bl.length; i++) {
            System.out.print(bl[i] + ",");
        }
        System.out.println();
        return bl;
    }

    private Schedule buildAlternativeSchedule(final Schedule schedule,
                                              final LinkedList<MetricRecord> loads,
                                              final LinkedList<SchedulingObjective> objectives,
                                              final Episode episode,
                                              final double[] bestScore,
                                              final double[] originScore) {
        Schedule alternativeSchedule = new Schedule("schedule.alternative.power", schedule.getStart(),
                schedule.getHorizon(), schedule.getGranularity());

        alternativeSchedule.setEpisode(episode);

        for (int i = 0; i < bestScore.length; i++) {
            String metric = "objective." + objectives.get(i).getClass().getSimpleName() + ".score";
            MetricRecord objScore = new MetricRecord(metric, schedule.getHorizon());
            objScore.addRecord(schedule.getStart(), schedule.getHorizon(), bestScore[i] + "", "alternative", 1);
            objScore.addRecord(schedule.getStart(), schedule.getHorizon(), originScore[i] + "", "prediction", 1);
            alternativeSchedule.getMetricRecordMap().put(metric, objScore);
        }

        for (MetricRecord load : loads) {
            alternativeSchedule.getMetricRecordMap().put(load.getMetricId(), load);
        }

        return alternativeSchedule;
    }

    public Episode buildEpisode(final Schedule schedule,
                                final LinkedList<MetricRecord> loads,
                                final LinkedList<SchedulingObjective> objectives,
                                final int[] bestSolution,
                                final int[] originSolution,
                                final double[] bestScore,
                                final double[] originScore) {
        LinkedList<Event> events = new LinkedList<>();
        LinkedList<Transformation> transformations = new LinkedList<>();
        for (int i = 0; i < loads.size(); i++) {
            MetricRecord load = loads.get(i);
            HashMap<String, MetricRecord> mrMap = new HashMap<>();
            mrMap.put("power", load);
            events.add(new Event(load.getStartTime(), load.getTimeFrame(), mrMap, false));

            long shift = (bestSolution[i] - originSolution[i]) * schedule.getGranularity();
            transformations.add(new Transformation(Transformation.TransformationType.SHIFT, shift));
        }

        HashMap<String, Double> impacts = new HashMap<>();
        for (int i = 0; i < bestScore.length; i++) {
            impacts.put(objectives.get(i).getClass().getSimpleName(), bestScore[i] - originScore[i]);
        }

        Episode episode = new Episode(events);
        AlternativeEpisode alternativeEpisode = new AlternativeEpisode("alternative", transformations, impacts);
        episode.addAlternative(alternativeEpisode.getVersion(), alternativeEpisode);

        return episode;
    }

    /**
     * Look at the Kevoree model to find InteractiveAppliance
     * which are negotiables and update the negotiableDeviceMap.
     */
    private HashMap<String, Device> negotiableDeviceMap() {
        UUIDModel model = getModelService().getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());
        HashMap<String, Device> deviceMap = ModelHelper.findAllRunningDevice("InteractiveAppliance",
                new String[]{context.getNodeName()}, localModel);
        HashMap<String, Device> negotiableDeviceMap = new HashMap<>();
        deviceMap.keySet().stream()
                .filter(deviceId -> deviceMap.get(deviceId).isNegotiable())
                .forEach(deviceId -> {
                    negotiableDeviceMap.put(deviceId, deviceMap.get(deviceId));
                });
        return negotiableDeviceMap;
    }

    public int[] toSolution(LinkedList<MetricRecord> loads,
                            Schedule schedule) {
        int nbLoad = 0;
        int[] solution = new int[loads.size()];
        for (int i = 0; i < loads.size(); i++) {
            long fromStart = loads.get(i).getStartTime() - schedule.getStart();
            solution[i] = (int) (fromStart / schedule.getGranularity());
        }
        return solution;
    }

    /**
     * Look at the Kevoree model to find a specific type of component.
     *
     * @param type component type
     * @return map of device
     */
    public HashMap<String, Device> findRunningDevice(final String type) {
        UUIDModel model = getModelService().getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());
        return ModelHelper.findAllRunningDevice(type,
                new String[]{context.getNodeName()}, localModel);
    }

    private void controlTime(final String cmd) {
        Request request = new Request(getFullId(), getNode() + ".timekeeper",
                getCurrentTime(), cmd + "Time");
        logInfo("send request " + cmd);
        sendRequest(request, new ShowIfErrorCallback());
    }

}
