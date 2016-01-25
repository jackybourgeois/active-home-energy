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


import org.activehome.com.RequestCallback;
import org.activehome.com.error.Error;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.MetricRecord;
import org.kevoree.log.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class ObjectiveBuilder {

    private final static String[] DEFAUT_OBCTIVES = new String[]{"MinimizeExport"};

    private LinkedList<SchedulingObjective> objectives;
    private LinkedList<MetricRecord> loads;
    private BruteForceScheduler scheduler;
    private Schedule schedule;
    private double[] baseload;
    private int indexToBeReady;

    public ObjectiveBuilder(final BruteForceScheduler scheduler,
                            final LinkedList<MetricRecord> loads,
                            final Schedule schedule,
                            final double[] baseload) {
        this.scheduler = scheduler;
        this.loads = loads;
        this.schedule = schedule;
        this.baseload = baseload;
    }

    public void build(RequestCallback callback) {
        prepareObjectives();
        if (objectives.size() > 0) {
            indexToBeReady = 0;
            getObjectivesReady(callback);
        }

    }

    private LinkedList<SchedulingObjective> prepareObjectives() {
        objectives = new LinkedList<>();
        for (String objName : DEFAUT_OBCTIVES) {
            SchedulingObjective obj = instantiateObjective(objName,
                    schedule, loads, baseload);
            if (obj != null) {
                objectives.add(obj);
            }
        }
        return objectives;
    }

    private void getObjectivesReady(final RequestCallback callback) {
        objectives.get(indexToBeReady).getReady(new RequestCallback() {
            @Override
            public void success(Object o) {
                indexToBeReady++;
                if (indexToBeReady<objectives.size()) {
                    getObjectivesReady(callback);
                } else {
                    callback.success(objectives);
                }
            }

            @Override
            public void error(Error error) {
                callback.error(error);
            }
        });
    }

    /**
     * Instantiate a SchedulingObjective base on the name
     *
     * @param objectiveName class name of the objective
     * @param schedule      the schedule to improve
     * @param negotiables   the list of negotiable, loads we can move
     * @param baseload      the load that cannot be move but needs to be taken into account
     * @return instance of SchedulingObjective or null
     */
    public SchedulingObjective instantiateObjective(final String objectiveName,
                                                    final Schedule schedule,
                                                    final LinkedList<MetricRecord> negotiables,
                                                    final double[] baseload) {
        SchedulingObjective objective = null;
        try {
            Class<?> clazz = Class.forName("org.activehome.energy.scheduler.bruteforce." + objectiveName);
            Constructor<?> constructor = clazz.getConstructor(schedule.getClass(),
                    negotiables.getClass(), baseload.getClass(), scheduler.getClass());
            objective = (SchedulingObjective) constructor.
                    newInstance(schedule, negotiables, baseload, scheduler);
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException
                | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            Log.error(e.getMessage());
        }
        return objective;
    }

}
