package org.activehome.energy.library.schedule;

/*
 * #%L
 * Active Home :: Energy :: Library
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


import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Record;
import org.activehome.energy.library.EnergyHelper;
import org.activehome.energy.library.record.GridRecord;
import org.activehome.energy.library.record.LoadRecord;
import org.activehome.energy.library.record.TariffRecord;
import org.activehome.energy.library.record.UserPreferenceRecord;
import org.activehome.energy.library.objective.ScheduleObjective;
import org.activehome.energy.library.oc.LoadSequence;
import org.activehome.energy.library.oc.OneLoadAtATime;
import org.activehome.energy.library.oc.OperationalConstraint;
import org.kevoree.log.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public abstract class EnergySchedule extends Schedule {

    protected LoadRecord load;
    protected GridRecord grid;
    protected TariffRecord tariff;
    protected UserPreferenceRecord userPreference;

    protected LinkedList<OperationalConstraint> opConstraints;
    protected ArrayList<ScheduleObjective> objectives;

    private double[][] cacheInteractiveLoad = null;

    public EnergySchedule(final String theName,
                          final long theStart,
                          final long theHorizon,
                          final long theGranularity) {
        super(theName, theStart, theHorizon, theGranularity);

        load = new LoadRecord();
        grid = new GridRecord();
        tariff = new TariffRecord();
        userPreference = new UserPreferenceRecord();

        opConstraints = new LinkedList<>();
        objectives = new ArrayList<>();
    }

    public EnergySchedule(final EnergySchedule schedule) {
        super(schedule);

        load = schedule.getLoad();
        grid = schedule.getGrid();
        tariff = schedule.getTariff();
        userPreference = schedule.getUserPreference();

        opConstraints = schedule.getOperationalConstraints();
        objectives = schedule.getObjectiveList();
    }

    public EnergySchedule(final JsonObject json) {
        super(json);

        load = new LoadRecord(json.get("load").asObject());
        grid = new GridRecord(json.get("grid").asObject());
        tariff = new TariffRecord(json.get("tariff").asObject());
        userPreference = new UserPreferenceRecord(json.get("preference").asObject());

        opConstraints = new LinkedList<>();
        for (JsonValue val : json.get("opConstraints").asArray()) {
            switch (val.asObject().get("type").asString()) {
                case "org.activehome.energy.library.oc.OneLoadAtATime":
                    opConstraints.add(new OneLoadAtATime(val.asObject()));
                    break;
                case "org.activehome.energy.library.oc.LoadSequence":
                    opConstraints.add(new LoadSequence(val.asObject()));
                    break;
                default:
            }
        }

        JsonArray array = json.get("objectives").asArray();
        String[] objectiveArray = new String[array.size()];
        for (int i = 0; i < array.size(); i++) objectiveArray[i] = array.get(i).asString();
        initObjectives(objectiveArray);
    }

    public LoadRecord getLoad() {
        return load;
    }

    public GridRecord getGrid() {
        return grid;
    }

    public TariffRecord getTariff() {
        return tariff;
    }

    public UserPreferenceRecord getUserPreference() {
        return userPreference;
    }

    public void setLoad(LoadRecord theLoad) {
        load = theLoad;
    }

    public void setGrid(GridRecord theGrid) {
        grid = theGrid;
    }

    public void setTariff(TariffRecord theTariff) {
        tariff = theTariff;
    }

    public void setUserPreference(UserPreferenceRecord theUserPreference) {
        userPreference = theUserPreference;
    }

    public double[][] getCacheInteractiveLoad() {
        if (cacheInteractiveLoad == null) cacheInteractive();
        return cacheInteractiveLoad;
    }


    public ArrayList<ScheduleObjective> getObjectiveList() {
        return objectives;
    }

    public LinkedList<OperationalConstraint> getOperationalConstraints() {
        return opConstraints;
    }

    public double[] normalizedBaseLoad() {
        double[] baseLoad = new double[getNbSlot()];
        for (int i = 0; i < baseLoad.length; i++) baseLoad[i] = 0;
        for (MetricRecord loadMR : load.getBackgroundLoadMap().values()) {
            double[] norm = normalizedLoad(loadMR);
            for (int i = 0; i < baseLoad.length; i++) baseLoad[i] += norm[i];
        }
        return baseLoad;
    }

    public double[] normalizedGeneration() {
        double[] generation = new double[getNbSlot()];
        for (int i = 0; i < generation.length; i++) generation[i] = 0;
        for (MetricRecord loadMR : load.getGenerationMap().values()) {
            double[] norm = normalizedLoad(loadMR);
            for (int i = 0; i < generation.length; i++) generation[i] += norm[i];
        }
        return generation;
    }

    /**
     * Convert instant power into energy
     */
    public double[] normalizedLoad(final MetricRecord loadMR) {
        return EnergyHelper.normalizedLoad(loadMR, getStart(), getHorizon(), getGranularity());
    }

    public double[] shiftAndNormalize(final Integer[] solution) {
        if (cacheInteractiveLoad == null) cacheInteractive();
        double[] shiftedLoad = new double[getNbSlot()];
        for (int i = 0; i < shiftedLoad.length; i++) shiftedLoad[i] = 0;
        for (int i = 0; i < cacheInteractiveLoad.length; i++) {
            double[] currentLoad = cacheInteractiveLoad[i];
            for (int j = 0; j < currentLoad.length; j++) {
                shiftedLoad[solution[i] + j] += currentLoad[j];
            }
        }
        return shiftedLoad;
    }

    private void cacheInteractive() {
        int nbLoad = 0;
        for (String device : load.getInteractives().keySet()) nbLoad += load.getInteractives().get(device).size();
        cacheInteractiveLoad = new double[nbLoad][];
        int i = 0;
        for (String key : load.getInteractives().keySet()) {
            for (MetricRecord loadMR : load.getInteractives().get(key)) {
                double[] data = new double[(int) ((loadMR.getlastTS() - loadMR.getStartTime()) / getGranularity()) + 1];
                int indexSlot = 0;
                double power = loadMR.getRecords().getFirst().getDouble();
                long lastTS = getStart();
                for (Record record : loadMR.getRecords()) {
                    //System.out.println("new record: " + indexSlot + " - " + power);
                    long currentTS = getStart() + record.getTS();
                    long nextSlotStart = getStart() + (indexSlot + 1) * getGranularity();
                    //System.out.println(currentTS + " - " + nextSlotStart);
                    if (currentTS < nextSlotStart) {   // before next slot, add energy the new val
                        //System.out.println("Add " + (power/1000.*(currentTS-lastTS)/3600000.));
                        data[indexSlot] += power / 1000. * (currentTS - lastTS) / 3600000.;
                        lastTS = currentTS;
                    } else {
                        while (currentTS > nextSlotStart) {
                            //System.out.println(indexSlot + " - " + power);
                            //System.out.println("Add " + (power/1000.*(nextSlotStart-lastTS)/3600000.));
                            data[indexSlot] += power / 1000. * (nextSlotStart - lastTS) / 3600000.;
                            lastTS = nextSlotStart;
                            indexSlot++;
                            nextSlotStart = getStart() + (indexSlot + 1) * getGranularity();
                        }
                        //System.out.println("Add " + (power/1000.*(currentTS-lastTS)/3600000.));
                        data[indexSlot] += power / 1000. * (currentTS - lastTS) / 3600000.;
                    }
                    power = record.getDouble();
                }
                cacheInteractiveLoad[i] = data;
                i++;
            }
        }

    }

    public double[] solutionToShiftableLoad(final int[] solution) {
        //System.out.println("sol to shiftable load");
        double[] shiftableLoad = null;
        int i = 0;
        for (String key : load.getInteractives().keySet()) {
            for (MetricRecord mr : load.getInteractives().get(key)) {
                long newStart = getStart() + getGranularity() * solution[i];
                double[] norm = normalizedLoad(new MetricRecord(newStart, mr));
                if (shiftableLoad == null) {
                    shiftableLoad = norm;
                } else {
                    for (int j = 0; j < shiftableLoad.length; j++) shiftableLoad[j] += norm[j];
                }
                i++;
            }
        }
        return shiftableLoad;
    }

    public int[] toSolution() {
        int nbLoad = 0;
        for (String device : load.getInteractives().keySet()) nbLoad += load.getInteractives().get(device).size();
        int[] solution = new int[nbLoad];
        int i = 0;
        for (String key : load.getInteractives().keySet()) {
            for (MetricRecord mr : load.getInteractives().get(key)) {
                solution[i] = (int) ((mr.getStartTime() - getStart()) / getGranularity());
                i++;
            }
        }
        return solution;
    }

    public void initObjectives(final String[] objectiveArray) {
        ArrayList<ScheduleObjective> scheduleObjectiveList = new ArrayList<>();
        for (String objectiveName : objectiveArray) {
            try {
                Class<?> clazz = Class.forName("org.activehome.energy.library.objective." + objectiveName);
                Constructor<?> constructor = clazz.getConstructor();
                ScheduleObjective objective = (ScheduleObjective) constructor.newInstance();
                objective.setSchedule(this);
                scheduleObjectiveList.add(objective);
            } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException
                    | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
                Log.error(e.getMessage());
            }
        }
        this.objectives = scheduleObjectiveList;
    }

    public boolean isSolutionCoherentWithOC(final int[] solution) {
        for (OperationalConstraint oc : opConstraints) {
            if (!oc.isValid(solution)) return false;
        }
        return true;
    }

    public void setOperationalConstraints(final LinkedList<OperationalConstraint> operationalConstraints) {
        opConstraints = operationalConstraints;
    }

    public long estimatedNbSolution() {
        long nbSol = 1;
        for (String key : load.getInteractives().keySet()) {
            for (MetricRecord mr : load.getInteractives().get(key)) {
                int loadLgt = (int) ((mr.getlastTS() - mr.getStartTime()) / getGranularity()) + 1;
                nbSol *= (getNbSlot() - loadLgt);
            }
        }
        return nbSol;
    }

    public JsonObject toJson() {
        JsonObject json = super.toJson();

        json.add("type", EnergySchedule.class.getName());

        json.add("load", load.toJson());
        json.add("grid", grid.toJson());
        json.add("tariff", tariff.toJson());
        json.add("preference", userPreference.toJson());

        JsonArray objectives = new JsonArray();
        for (ScheduleObjective obj : this.objectives) {
            objectives.add(obj.getClass().getSimpleName());
        }
        json.add("objectives", objectives);

        JsonArray ocArray = new JsonArray();
        for (OperationalConstraint oc : opConstraints) ocArray.add(oc.toJson());
        json.add("opConstraints", ocArray);

        return json;
    }

}
