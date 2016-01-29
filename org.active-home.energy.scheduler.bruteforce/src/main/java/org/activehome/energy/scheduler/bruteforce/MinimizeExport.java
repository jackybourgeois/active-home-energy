package org.activehome.energy.scheduler.bruteforce;

/*
 * #%L
 * Active Home :: Energy :: Scheduler :: Brute Force
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


import org.activehome.com.RequestCallback;
import org.activehome.com.error.Error;
import org.activehome.com.error.ErrorType;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.Device;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.helper.ModelHelper;
import org.activehome.energy.library.EnergyHelper;
import org.activehome.tools.Convert;
import org.kevoree.api.handler.UUIDModel;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class MinimizeExport extends SchedulingObjective {
    /**
     * The full original schedule.
     */
    private Schedule schedule;
    /**
     * The scheduler looking for alternatives.
     */
    private BruteForceScheduler scheduler;
    /**
     * Details of each negotiable load.
     */
    private LinkedList<MetricRecord> negotiableLoads;
    /**
     * Any consumption that is not negotiable but
     * needs to be considered in the computation.
     */
    private double[] baseLoad;
    /**
     * Maximum score that can be reach by any
     * solution of this schedule.
     */
    private double max;
    /**
     * Minimum score that can be reach by any
     * solution of this schedule.
     */
    private double min;

    private double[] generation;

    public MinimizeExport(final Schedule schedule,
                          final LinkedList<MetricRecord> negotiableLoads,
                          final double[] baseLoad,
                          final BruteForceScheduler scheduler) {

        this.schedule = schedule;
        this.negotiableLoads = negotiableLoads;
        this.baseLoad = baseLoad;
        this.scheduler = scheduler;
    }

    /**
     * Look for the required variables in the schedule,
     * otherwise ask the context.
     *
     * @param callback
     */
    @Override
    public void getReady(RequestCallback callback) {
        HashMap<String, Device> mgMap = scheduler.findRunningDevice("MicroGeneration");
        LinkedList<MetricRecord> mgLoads = new LinkedList<>();
        for (String mgId : mgMap.keySet()) {
            mgLoads.add(schedule.getMetricRecordMap().get("power.gen" + mgId.substring(mgId.lastIndexOf("."))));
        }

        // if we found all required metrics, prepare them and callback success
        if (mgLoads.size() == mgMap.size()) {
            generation = new double[schedule.getNbSlot()];
            for (int i = 0; i < generation.length; i++) {
                generation[i] = 0;
            }
            for (MetricRecord load : mgLoads) {
                double[] normalizedLoad = EnergyHelper.normalizedLoad(
                        load, schedule.getStart(), schedule.getHorizon(), schedule.getGranularity());
                for (int i = 0; i < generation.length; i++) {
                    generation[i] += normalizedLoad[i];
                }
            }
            initMinMax();
            callback.success(true);
        } else {
            callback.error(new Error(ErrorType.METHOD_ERROR,
                    "Enable to found all required metrics"));
        }
    }

    private void initMinMax() {

        // min: in the best case there is no export (exp(0))
        min = 1;
        // max: in the worse case all the generation is exported
        for (double aGeneration : generation) {
            max += Math.exp(aGeneration);
        }

        System.out.println("min: " + min + " max: " + max);
    }

    /**
     * Provide a score between 0 (best) and 1 (worse)
     *
     * @param solution the time slot of each load start
     * @param load     the sum of negotiable loads for each slot
     * @return the normalized score
     */
    @Override
    public double evaluate(final int[] solution,
                           final double[] load) {
        double score = 1;
        for (int i = 0; i < load.length; i++) {
            double cons = load[i] + baseLoad[i];
            if (generation[i] > cons) {
                score += Math.exp(generation[i] - cons);
            }
        }
        return normScore(score);
    }

    /**
     * return energy exported (kWh)
     */
    @Override
    public double meaningfulScoreEvaluation(final int[] solution,
                                             final double[] load) {
        double score = 0;
        for (int i = 0; i < load.length; i++) {
            double cons = load[i] + baseLoad[i];
            if (generation[i] > cons) {
                score += Convert.watt2kWh(generation[i] - cons, schedule.getGranularity());
            }
        }
        return score;
    }

    /**
     * Normalize the score between 0 and 1
     * @param score
     * @return
     */
    public double normScore(double score) {
        return (score - min) / (max - min);
    }

}
