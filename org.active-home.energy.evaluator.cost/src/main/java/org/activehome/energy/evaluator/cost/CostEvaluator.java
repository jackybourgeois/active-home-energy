package org.activehome.energy.evaluator.cost;

/*
 * #%L
 * Active Home :: Energy :: Evaluator :: Cost
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
import org.activehome.com.*;
import org.activehome.com.error.Error;
import org.activehome.context.data.DiscreteDataPoint;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Record;
import org.activehome.energy.library.etp.EnergyTariffPolicy;
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
public class CostEvaluator extends Evaluator {

    @Param(defaultValue = "Perform an evaluation of the financial cost of electricity for the household.")
    private String description;
    @Param(defaultValue = "/activehome-energy/tree/master/org.activehome.energy.evaluator.cost")
    private String src;

    /**
     * The necessary bindings.
     */
    @Param(defaultValue = "getNotif>Evaluator.pushReport")
    private String bindingCostEvaluator;

    /**
     * Import energy tariff policy
     */
    @Param(defaultValue = "org.activehome.energy.library.etp.LinearETP")
    private String importETP;
    /**
     * Export energy tariff policy
     */
    @Param(defaultValue = "org.activehome.energy.library.etp.LinearETP")
    private String exportETP;
    /**
     * Generation energy tariff policy
     */
    @Param(defaultValue = "org.activehome.energy.library.etp.LinearETP")
    private String generationETP;

    private EnergyTariffPolicy impETP;
    private EnergyTariffPolicy expETP;
    private EnergyTariffPolicy genETP;

    private EvaluationReport lastReport;

    @Start
    public void start() {
        super.start();
        try {
            impETP = (EnergyTariffPolicy) Class.forName(importETP).newInstance();
            expETP = (EnergyTariffPolicy) Class.forName(exportETP).newInstance();
            genETP = (EnergyTariffPolicy) Class.forName(generationETP).newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

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

        String[] metrics = new String[]{"power.import#corrected,0", "power.export#corrected,0", "power.gen#corrected,0",
                "tariff.elec.import#corrected,0", "tariff.elec.export#corrected,0", "tariff.elec.generation#corrected,0"};
        Object[] params = new Object[]{startTS, endTS - startTS, HOUR, metrics};

        Request ctxReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "extractSchedule", params);

        sendRequest(ctxReq, new RequestCallback() {
            @Override
            public void success(Object obj) {
                callback.success(computeScheduleCost((Schedule) obj));
            }

            @Override
            public void error(Error error) {
                logError(error.toString());
                callback.error(error);
            }
        });
    }

    private EvaluationReport computeScheduleCost(Schedule schedule) {
        HashMap<String, String> reportedMetric = new HashMap<>();
        Schedule resultSchedule = new Schedule(schedule);

        MetricRecord importMR = evalMetric(schedule, impETP, "power.import",
                "tariff.elec.import", "cost.elec.import");
        resultSchedule.getMetricRecordMap().put("cost.elec.import", importMR);
        double importCost = importMR.sum();
        reportedMetric.put("cost.elec.import.1d", importCost + "");
        sendEvalToContext("cost.elec.import.1d", schedule.getStart(), importCost + "",
                schedule.getHorizon(), importMR.getMainVersion());

        MetricRecord generationMR = evalMetric(schedule, genETP, "power.gen",
                "tariff.elec.generation", "benefits.elec.generation");
        resultSchedule.getMetricRecordMap().put("benefits.elec.generation", generationMR);
        double generationBenefits = generationMR.sum();
        reportedMetric.put("benefits.elec.generation.1d", generationBenefits + "");
        sendEvalToContext("benefits.elec.generation.1d", schedule.getStart(), generationBenefits + "",
                schedule.getHorizon(), generationMR.getMainVersion());

        MetricRecord exportMR = evalMetric(schedule, expETP, "power.export",
                "tariff.elec.export", "benefits.elec.export");
        resultSchedule.getMetricRecordMap().put("benefits.elec.export", exportMR);
        double exportBenefits = exportMR.sum();
        reportedMetric.put("benefits.elec.export.1d", exportBenefits + "");
        sendEvalToContext("benefits.elec.export.1d", schedule.getStart(), exportBenefits + "",
                schedule.getHorizon(), exportMR.getMainVersion());

        double cost = importCost - generationBenefits - exportBenefits;
        reportedMetric.put("cost.elec.1d", cost + "");
        sendEvalToContext("cost.elec.1d", schedule.getStart(), cost + "",
                schedule.getHorizon(), exportMR.getMainVersion());

        EvaluationReport report = new EvaluationReport(getId(),
                generationMR.getMainVersion(), reportedMetric, resultSchedule);
        lastReport = report;
        publishReport(report);

        return report;
    }

    private MetricRecord evalMetric(final Schedule schedule,
                                    final EnergyTariffPolicy etp,
                                    final String rateMetric,
                                    final String tariffMetric,
                                    final String outputMetric) {
        MetricRecord rateMR = schedule.getMetricRecordMap().get(rateMetric);
        MetricRecord tariffMR = schedule.getMetricRecordMap().get(tariffMetric);
        MetricRecord evalMR = new MetricRecord(outputMetric, rateMR.getTimeFrame());
        if (rateMR.getRecords() == null || rateMR.getRecords().size() == 0) {
            logError("No record for metric " + rateMetric);
        } else if (tariffMR.getRecords() == null || tariffMR.getRecords().size() == 0) {
            logError("No record for metric " + tariffMetric);
        } else {
            int indexRate = 1;
            int indexTariff = 0;
            long prevTS = 0;
            Record tariff = tariffMR.getRecords().get(indexTariff);
            Record rate = rateMR.getRecords().get(0);
            double prevRate = rate.getDouble();
            while (indexRate < rateMR.getRecords().size()) {
                rate = rateMR.getRecords().get(indexRate);
                // if current tariff is not the last and rest
                while (indexTariff < tariffMR.getRecords().size() - 1
                        && tariffMR.getRecords().get(indexTariff + 1).getTS() < rate.getTS()) {
                    double prevTariff = tariff.getDouble();
                    indexTariff++;
                    tariff = tariffMR.getRecords().get(indexTariff);
                    long duration = tariff.getTS() - prevTS;
                    double energyKWh = Convert.watt2kWh(prevRate, duration);
                    evalMR.addRecord(prevTS, duration, etp.calculate(energyKWh, prevTariff) + "",
                            rateMR.getMainVersion(), 1);
                    prevTS = tariff.getTS();
                }
                long duration = rate.getTS() - prevTS;
                double energyKWh = Convert.watt2kWh(prevRate, duration);
                evalMR.addRecord(prevTS, duration, etp.calculate(energyKWh, tariff.getDouble()) + "",
                        rateMR.getMainVersion(), 1);
                prevTS = rate.getTS();
                prevRate = rate.getDouble();
                indexRate++;
            }

            if (schedule.getHorizon() > prevTS) {
                long duration = schedule.getHorizon() - prevTS;
                double energyKWh = Convert.watt2kWh(prevRate, duration);
                evalMR.addRecord(prevTS, duration, etp.calculate(energyKWh, tariff.getDouble()) + "",
                        rateMR.getMainVersion(), 1);
            }
        }

        return evalMR;
    }

    private void sendEvalToContext(String metricId, long start, String val, long duration,
                                   String version) {
        DiscreteDataPoint ddp = new DiscreteDataPoint(metricId, start, val,
                version, 0, 1, Convert.strDurationToMillisec(getDefaultHorizon()));
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), ddp));
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
                                lastReport.getSchedule().getStart() + lastReport.getSchedule().getHorizon(),
                                new ShowIfErrorCallback());
                    }
                }
            }
        }
    }


}
