package org.activehome.energy.evaluator.energy;

/*
 * #%L
 * Active Home :: Energy :: Evaluator :: Energy
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
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.RequestCallback;
import org.activehome.com.error.Error;
import org.activehome.context.data.DiscreteDataPoint;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Record;
import org.activehome.evaluator.EvaluationReport;
import org.activehome.evaluator.Evaluator;
import org.activehome.tools.Convert;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EnergyEvaluator extends Evaluator {

    @Param(defaultValue = "Perform an evaluation of electricity flows in the household.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.evaluator.energy/docs/energy.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.evaluator.energy/docs/energyEvaluator.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.evaluator.energy/docs/demo.kevs")
    private String demoScript;

    @Param(defaultValue = "/activehome-energy/tree/master/org.activehome.energy.evaluator.energy")
    private String src;

    /**
     * The necessary bindings.
     */
    @Param(defaultValue = "getNotif>Context.pushNotif")
    private String bindingEnergyEvaluator;

    private RequestCallback waitingCallback;
    private long waitingStart;
    private long waitingEnd;

    private static final String[] METRICS_TO_REPORT = new String[]{"power.import", "power.export", "power.gen", "power.cons",
            "power.cons.bg", "power.cons.inter", "power.cons.bg.baseload"};

    @Override
    public void evaluate(final long startTS,
                         final long endTS,
                         final RequestCallback callback) {

        logInfo("evaluate: " + strLocalTime(startTS) + " to " + strLocalTime(endTS));

        Object[] params = new Object[]{startTS, endTS - startTS, HOUR, new String[]{"power.gen.SolarPV"}};
        Request ctxReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "extractSchedule", params);
        sendRequest(ctxReq, new RequestCallback() {
            @Override
            public void success(Object obj) {
                Schedule schedule = (Schedule) obj;
                MetricRecord mrPV = schedule.getMetricRecordMap().get("power.gen.SolarPV");
                if (mrPV != null && checkSolarPV(mrPV)) {
                    waitingCallback = callback;
                    waitingStart = startTS;
                    waitingEnd = endTS;
                } else {
                    getScheduleCorrectedScheduleAndReport(startTS, endTS, callback);
                }
            }

            @Override
            public void error(Error error) {
                logError(error.toString());
                callback.error(error);
            }
        });
    }

    private void getScheduleCorrectedScheduleAndReport(long start, long end, RequestCallback callback) {
        String[] metrics = new String[METRICS_TO_REPORT.length];
        for (int i=0;i<METRICS_TO_REPORT.length;i++) metrics[i] = METRICS_TO_REPORT[i] + "#corrected,0";
        Object[] params = new Object[]{start, end - start, HOUR, metrics};
        Request ctxReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "extractSchedule", params);
        sendRequest(ctxReq, new RequestCallback() {
            @Override
            public void success(Object obj) {
                callback.success(computeScheduleEnergy((Schedule) obj));
            }

            @Override
            public void error(Error error) {
                logError(error.toString());
                callback.error(error);
            }
        });
    }

    private EvaluationReport computeScheduleEnergy(Schedule schedule) {
        HashMap<String, String> reportedMetric = new HashMap<>();
        Schedule resultSchedule = new Schedule(schedule);
        for (String metric : METRICS_TO_REPORT) {
            if (schedule.getMetricRecordMap().get(metric).getRecords() != null) {
                evalAndNotifify(schedule, metric, resultSchedule, reportedMetric);
            } else {
                reportedMetric.put(metric.replace("power.", "energy."), "0");
            }
        }
        return new EvaluationReport(reportedMetric, resultSchedule);
    }

    private void evalAndNotifify(Schedule schedule,
                                 String metric,
                                 Schedule resultSchedule,
                                 HashMap<String, String> reportedMetric) {
        MetricRecord mr = evalMetric(schedule, metric, metric.replace("power.", "energy."));
        resultSchedule.getMetricRecordMap().put(metric.replace("power.", "energy."), mr);
        String metricId = metric.replace("power.", "energy." + getDefaultHorizon() + ".");
        String value = mr.sum() + "";
        reportedMetric.put(metricId, value);
        DiscreteDataPoint ddp = new DiscreteDataPoint(metricId, schedule.getStart(), value,
                mr.getMainVersion(), 0, 1,
                Convert.strDurationToMillisec(getDefaultHorizon()));
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), ddp));
    }

    private MetricRecord evalMetric(final Schedule schedule,
                                    final String rateMetric,
                                    final String outputMetric) {
        MetricRecord rateMR = schedule.getMetricRecordMap().get(rateMetric);
        MetricRecord evalMR = new MetricRecord(outputMetric, rateMR.getTimeFrame());
        long prevTS = -1;
        double prevRate = 0;
        int slot = 1;
        double sumCurrentSlot = 0;

        for (Record rate : rateMR.getRecords()) {
            if (prevTS != -1) {
                if (rate.getTS() < slot * schedule.getGranularity()) {
                    sumCurrentSlot += Convert.watt2kWh(prevRate, rate.getTS() - prevTS);
                } else {
                    sumCurrentSlot += Convert.watt2kWh(prevRate,
                            slot * schedule.getGranularity() - prevTS);
                    evalMR.addRecord((slot - 1) * schedule.getGranularity(),
                            schedule.getGranularity(), sumCurrentSlot + "", 1);
                    sumCurrentSlot = Convert.watt2kWh(prevRate,
                            rate.getTS() - slot * schedule.getGranularity());
                    slot++;
                }
            }
            prevTS = rate.getTS();
            prevRate = rate.getDouble();
        }

        sumCurrentSlot += Convert.watt2kWh(prevRate,
                slot * schedule.getGranularity() - prevTS);
        evalMR.addRecord((slot - 1) * schedule.getGranularity(),
                schedule.getGranularity(), sumCurrentSlot + "", 1);
        slot++;
        while (schedule.getNbSlot() >= slot) {
            evalMR.addRecord((slot - 1) * schedule.getGranularity(),
                    Convert.watt2kWh(prevRate, schedule.getGranularity()) + "", 1);
            slot++;
        }

        return evalMR;
    }

    /**
     * Build frequency map and remove records
     * that appear more than 30 of all records.
     *
     * @param solarMR
     * @return true if values have been corrected
     */
    private boolean checkSolarPV(MetricRecord solarMR) {

        HashMap<Double, Integer> freqMap = new HashMap<>();
        for (Record record : solarMR.getRecords()) {
            if (freqMap.containsKey(record.getDouble())) {
                freqMap.put(record.getDouble(), freqMap.get(record.getDouble()) + 1);
            } else {
                freqMap.put(record.getDouble(), 1);
            }
        }

        double thirtyPrecent = solarMR.getRecords().size() / 5;
        LinkedList<Double> toRemove = freqMap.keySet().stream()
                .filter(val -> Double.valueOf(freqMap.get(val)) > thirtyPrecent)
                .collect(Collectors.toCollection(LinkedList::new));
        if (toRemove.size() > 0) {
            MetricRecord correctedMR = new MetricRecord(
                    solarMR.getMetricId(), solarMR.getTimeFrame());
            for (Record record : solarMR.getRecords()) {
                boolean addValue = true;
                for (double val : toRemove) {
                    if (val == record.getDouble()) {
                        addValue = false;
                    }
                }
                if (addValue) {
                    correctedMR.addRecord(solarMR.getStartTime() + record.getTS(),
                            record.getValue(), "corrected", record.getConfidence());
                }
            }

            // if corrected version, send it to the context
            sendNotif(new Notif(getFullId(), getNode() + ".context",
                    getCurrentTime(), correctedMR));
            return true;
        }
        return false;
    }

    @Input
    public void getNotif(String notifStr) {
        JsonObject jsonNotif = JsonObject.readFrom(notifStr);
        if (jsonNotif.get("dest").asString().equals(getFullId())) {
            Notif notif = new Notif(jsonNotif);
            if (notif.getContent() instanceof String
                    && notif.getContent().equals("power.gen.SolarPV")) {
                getScheduleCorrectedScheduleAndReport(waitingStart, waitingEnd, waitingCallback);
            }
        }
    }

}
