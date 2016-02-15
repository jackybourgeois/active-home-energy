package org.activehome.energy.evaluator.co2;

/*
 * #%L
 * Active Home :: Energy :: Evaluator :: CO2
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
import org.activehome.com.ShowIfErrorCallback;
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
import org.kevoree.annotation.Start;

import java.util.HashMap;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EnvironmentalImpactEvaluator extends Evaluator {

    @Param(defaultValue = "Perform an evaluation of the environmental impact (CO2) of the household's electricity system.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.evaluator.co2")
    private String src;

    /**
     * The necessary bindings.
     */
    @Param(defaultValue = "getNotif>Evaluator.pushReport")
    private String bindingEnvironmentalImpactEvaluator;

    private EvaluationReport lastReport;

    @Override
    public void onInit() {
        super.onInit();
        lastReport = null;
    }

    @Override
    public void evaluate(final long startTS,
                         final long endTS,
                         final RequestCallback callback) {

        logInfo("evaluate: " + strLocalTime(startTS) + " to " + strLocalTime(endTS));

        String[] metrics = new String[]{"power.import#corrected,0", "grid.carbonIntensity#corrected,0"};
        Request ctxReq = new Request(getFullId(), getNode() + ".context", getCurrentTime(),
                "extractSchedule", new Object[]{startTS, endTS - startTS, HOUR, metrics});
        sendRequest(ctxReq, new RequestCallback() {
            @Override
            public void success(Object obj) {
                callback.success(
                        computeScheduleEnvironmentalImpact((Schedule) obj));
            }

            @Override
            public void error(Error error) {
                logError(error.toString());
                callback.error(error);
            }
        });
    }

    private EvaluationReport computeScheduleEnvironmentalImpact(Schedule schedule) {
        HashMap<String, String> reportedMetric = new HashMap<>();
        Schedule resultSchedule = new Schedule(schedule);

        MetricRecord carbonIntensityMR = evalCarbonIntensity(schedule);
        resultSchedule.getMetricRecordMap().put("environmentalImpact.elec.co2", carbonIntensityMR);
        double carbonIntensity = carbonIntensityMR.sum();
        reportedMetric.put("environmentalImpact.elec.co2", carbonIntensity + "");
        sendEvalToContext("environmentalImpact.elec.co2", schedule.getStart(), carbonIntensity + "",
                schedule.getHorizon(), carbonIntensityMR.getMainVersion());

        EvaluationReport report = new EvaluationReport(getId(),
                carbonIntensityMR.getMainVersion(), reportedMetric, resultSchedule);
        lastReport = report;
        publishReport(report);

        return report;
    }

    private void sendEvalToContext(String metricId, long start, String val, long duration,
                                   String version) {
        DiscreteDataPoint ddp = new DiscreteDataPoint(metricId, start, val,
                version, 0, 1, Convert.strDurationToMillisec(getDefaultHorizon()));
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), ddp));
    }

    private MetricRecord evalCarbonIntensity(final Schedule schedule) {
        MetricRecord importMR = schedule.getMetricRecordMap().get("power.import");
        MetricRecord ciMR = schedule.getMetricRecordMap().get("grid.carbonIntensity");
        MetricRecord evalMR = new MetricRecord("environmentalImpact.elec.co2.1d", importMR.getTimeFrame());
        if (ciMR.getRecords() != null && ciMR.getRecords().size() > 0
                && importMR.getRecords() != null && importMR.getRecords().size() > 0) {
            int indexImport = 1;
            int indexCI = 0;
            long prevTS = 0;
            Record ci = ciMR.getRecords().get(0);
            Record imp = importMR.getRecords().get(0);
            double prevImport = imp.getDouble();
            while (indexImport < importMR.getRecords().size()) {
                imp = importMR.getRecords().get(indexImport);
                // if current tariff is not the last and rest
                while (indexCI < ciMR.getRecords().size() - 1
                        && ciMR.getRecords().get(indexCI + 1).getTS() < imp.getTS()) {
                    double prevCI = ci.getDouble();
                    indexCI++;
                    ci = ciMR.getRecords().get(indexCI);
                    double energyKWh = Convert.watt2kWh(prevImport, ci.getTS() - prevTS);
                    evalMR.addRecord(prevTS, (energyKWh * prevCI) + "", importMR.getMainVersion(), 1);
                    prevTS = ci.getTS();
                }
                double energyKWh = Convert.watt2kWh(prevImport, imp.getTS() - prevTS);
                evalMR.addRecord(prevTS, (energyKWh * ci.getDouble()) + "", importMR.getMainVersion(), 1);
                prevTS = imp.getTS();
                prevImport = imp.getDouble();
                indexImport++;
            }

            if (schedule.getHorizon() > prevTS) {
                double energyKWh = Convert.watt2kWh(prevImport, schedule.getHorizon() - prevTS);
                evalMR.addRecord(prevTS, (energyKWh * ci.getDouble()) + "", importMR.getMainVersion(), 1);
            }
        }


        return evalMR;
    }

    @Input
    public void getNotif(String notifStr) {
        JsonObject jsonNotif = JsonObject.readFrom(notifStr);
        if (jsonNotif.get("src").asString().contains("EnergyEvaluator")) {
            Notif notif = new Notif(jsonNotif);
            if (notif.getContent() instanceof EvaluationReport) {
                EvaluationReport report = (EvaluationReport) notif.getContent();

                // if new energy report based on corrected values, redo the evaluate
                if (report.getVersion().equals("corrected")) {
                    if (lastReport != null) {
                        evaluate(lastReport.getSchedule().getStart(),
                                lastReport.getSchedule().getStart() +  lastReport.getSchedule().getHorizon(),
                                new ShowIfErrorCallback());
                    }
                }
            }
        }
    }

}
