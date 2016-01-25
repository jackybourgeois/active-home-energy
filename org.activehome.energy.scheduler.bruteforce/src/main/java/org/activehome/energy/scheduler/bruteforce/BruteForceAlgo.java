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


import org.activehome.context.data.Schedule;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Record;
import org.activehome.energy.library.EnergyHelper;
import org.activehome.energy.library.objective.ScheduleObjective;
import org.activehome.energy.library.oc.OperationalConstraint;
import org.activehome.energy.library.schedule.EnergySchedule;
import org.activehome.tools.Convert;
import org.kevoree.log.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.UUID;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class BruteForceAlgo {

    private Schedule schedule;
    private LinkedList<MetricRecord> loadMRs;
    private double[][] loadArray;
    private LinkedList<SchedulingObjective> objectives;
    private LinkedList<OperationalConstraint> constraints;


    private LinkedList<Thread> threads;
    private HashMap<UUID, Integer> nbSolMap;

    private HashMap<int[], double[]> dominant;

    private HashMap<UUID, HashMap<int[], double[]>> dominantMap;
    private HashMap<UUID, HashMap<int[], double[]>> otherSolMap;
    private UUID mergeThread;

    private boolean multiThread = true;
    private boolean keepOtherSol = true;
    private long execTime;

    public BruteForceAlgo(final BruteForceScheduler scheduler,
                          final Schedule schedule,
                          final LinkedList<MetricRecord> loadMRs,
                          final LinkedList<SchedulingObjective> objectives,
                          final LinkedList<OperationalConstraint> constraints) {
        this.schedule = schedule;
        this.loadMRs = loadMRs;
        this.objectives = objectives;
        this.constraints = constraints;
        prepareLoads();

        threads = new LinkedList<>();

        if (estimatedNbSolution() > 10000) {
            keepOtherSol = false;
            Log.info("Brute Force Algo: Non dominant solution will not be saved.");
        }
    }

    public void startScheduling() {
        long startExecution = new Date().getTime();

        UUID id = UUID.randomUUID();
        nbSolMap = new HashMap<>();
        nbSolMap.put(id, 0);

        dominantMap = new HashMap<>();
        dominantMap.put(id, new HashMap<>());
        otherSolMap = new HashMap<>();
        otherSolMap.put(id, new HashMap<>());

        int[] initialSolution = new int[loadMRs.size()];
        for (int i = 0; i < initialSolution.length; i++) {
            initialSolution[i] = 0;
        }

        browse(0, initialSolution, id);

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mergeThread = mergeDominantList();
        dominant = dominantMap.get(mergeThread);
        mergeOtherSol(mergeThread);

        execTime = new Date().getTime() - startExecution;
    }

    public final HashMap<int[], double[]> getOtherSolution() {
        return otherSolMap.get(mergeThread);
    }

    public final HashMap<int[], double[]> getDominantSolution() {
        return dominantMap.get(mergeThread);
    }

    /**
     * Browse all possible solution recursively.
     *
     * @param currentLoadIndex Index of load to shift
     * @param solution         Solution being built
     * @param threadId         Thread running this 'sub-browse'
     */
    public final void browse(final int currentLoadIndex,
                             final int[] solution,
                             final UUID threadId) {
        long granularity = schedule.getGranularity();
        MetricRecord metricRecord = loadMRs.get(currentLoadIndex);
        int end = schedule.getNbSlot() - (int) (metricRecord.getRecords()
                .getLast().getTS() / granularity) - 1;
        // go through all possible position of this load (from TS 0 to nbSlot - load lgt)
        for (int i = 0; i <= end; i++) {
            // keep track of the current solution
            solution[currentLoadIndex] = i;
            if (currentLoadIndex < solution.length - 1) {
                // multithreading at the first level
                if (multiThread && currentLoadIndex == 0) {
                    browseNextLevelInNewThread(solution, currentLoadIndex + 1);
                } else {
                    // call next recursion level
                    browse(currentLoadIndex + 1, solution, threadId);
                }
            } else {
                checkSolution(solution, threadId);
            }
        }
    }

    private void browseNextLevelInNewThread(int[] solution, int nextIndex) {
        UUID newId = UUID.randomUUID();
        dominantMap.put(newId, new HashMap<>());
        otherSolMap.put(newId, new HashMap<>());
        nbSolMap.put(newId, 0);
        int[] copySolution = Arrays.copyOf(solution, solution.length);
        Thread thread = new Thread(() -> {
            browse(nextIndex, copySolution, newId);
        }, "scheduling " + nextIndex + "-" + solution[nextIndex-1]);
        threads.add(thread);
        thread.start();
    }

    private void checkSolution(int[] solution, UUID threadId) {
        // check operational constraints, is it a valid solution?
        if (isSolutionCoherentWithOC(solution)) {
            // compute score and keep it if best
            double[] shiftableLoad = shiftAndNormalize(solution);
            double[] score = evaluateForEachObjective(solution, shiftableLoad);
            checkBest(solution, score, threadId);
            nbSolMap.put(threadId, nbSolMap.get(threadId) + 1);
            if (nbSolMap.get(threadId) % 1000000 == 0) {
                Log.info(nbSolMap.get(threadId)
                        + " solutions in stack " + threadId);
            }
        }
    }

    /**
     * Evaluate the solution for each objective.
     */
    public final double[] evaluateForEachObjective(final int[] solution,
                                                   final double[] shiftableLoad) {
        double[] scoreVector = new double[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            scoreVector[i] = objectives.get(i).evaluate(solution, shiftableLoad);
        }
        return scoreVector;
    }

    /**
     * Evaluate the solution for each objective.
     */
    public final double[] meaningfulEvalForEachObjective(final int[] solution,
                                                   final double[] shiftableLoad) {
        double[] scoreVector = new double[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            scoreVector[i] = objectives.get(i).meaningfulScoreEvaluation(solution, shiftableLoad);
        }
        return scoreVector;
    }

    public boolean isSolutionCoherentWithOC(final int[] solution) {
        for (OperationalConstraint oc : constraints) {
            if (!oc.isValid(solution)) return false;
        }
        return true;
    }

    public final void checkBest(final int[] solution,
                                final double[] score,
                                final UUID threadId) {
        HashMap<int[], double[]> toRemove = new HashMap<>();
        HashMap<int[], double[]> curDominantMap = dominantMap.get(threadId);
        HashMap<int[], double[]> curOtherSolMap = otherSolMap.get(threadId);
        boolean dominant = true;
        for (int[] domSol : curDominantMap.keySet()) {
            double[] domScore = curDominantMap.get(domSol);
            int supCount = 0;
            int equCount = 0;
            for (int i = 0; i < domScore.length; i++) { // tend to minimize
                if (score[i] < domScore[i]) {
                    supCount++;
                } else if (score[i] == domScore[i]) {
                    equCount++;
                }
            }
            if (supCount + equCount == domScore.length && supCount > 0) {
                toRemove.put(domSol, domScore);
            } else if (supCount == 0 && equCount < domScore.length) {
                dominant = false;
            }
        }
        // if dominant, add the new solution and transfer dominated to otherSol
        if (dominant) {
            curDominantMap.put(Arrays.copyOf(solution, solution.length), score);
            toRemove.forEach(curDominantMap::remove);
            if (keepOtherSol) {
                curOtherSolMap.putAll(toRemove);
            }
        } else {    // else: add to otherSol
            if (keepOtherSol) {
                curOtherSolMap.put(solution, score);
            }
        }
    }

    private UUID mergeDominantList() {
        UUID threadMerge = UUID.randomUUID();
        dominantMap.put(threadMerge, new HashMap<>());
        otherSolMap.put(threadMerge, new HashMap<>());
        dominantMap.keySet().stream().filter(id -> !id.equals(threadMerge))
                .forEach(id -> {
                    for (int[] solution : dominantMap.get(id).keySet()) {
                        checkBest(solution, dominantMap.get(id)
                                .get(solution), threadMerge);
                    }
                });
        return threadMerge;
    }

    private void mergeOtherSol(final UUID mergeId) {
        otherSolMap.put(mergeId, new HashMap<>());
        for (UUID id : otherSolMap.keySet()) {
            otherSolMap.get(mergeId).putAll(otherSolMap.get(id));
        }
    }

    public long estimatedNbSolution() {
        long nbSol = 1;
        for (MetricRecord mr : loadMRs) {
            int loadLgt = (int) ((mr.getlastTS() - mr.getStartTime()) / schedule.getGranularity()) + 1;
            nbSol *= (schedule.getNbSlot() - loadLgt);
        }
        return nbSol;
    }

    /**
     * Combine the load array into one array following the solution
     */
    public double[] shiftAndNormalize(final int[] solution) {
        double[] shiftedLoad = new double[schedule.getNbSlot()];
        for (int i = 0; i < shiftedLoad.length; i++) {
            shiftedLoad[i] = 0;
        }
        for (int i = 0; i < loadArray.length; i++) {
            double[] currentLoad = loadArray[i];
            for (int j = 0; j < currentLoad.length; j++) {
                shiftedLoad[solution[i] + j] += currentLoad[j];
            }
        }
        return shiftedLoad;
    }

    /**
     * Build loadArray which contains the normalized kWh of each slot of each load.
     * (Thus irregular length of array)
     */
    private void prepareLoads() {
        loadArray = new double[loadMRs.size()][];
        int i = 0;
        for (MetricRecord load : loadMRs) {
            loadArray[i] = EnergyHelper.normalizedLoad(load, load.getStartTime(),
                    load.getTimeFrame(), schedule.getGranularity());
            i++;
        }

    }

    public long getExecTime() {
        return execTime;
    }
}
