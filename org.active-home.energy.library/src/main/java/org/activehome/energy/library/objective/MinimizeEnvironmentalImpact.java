package org.activehome.energy.library.objective;

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


import org.activehome.context.data.MetricRecord;
import org.activehome.energy.library.schedule.EnergySchedule;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class MinimizeEnvironmentalImpact implements ScheduleObjective {

    private double[] baseLoad;
    private double[] generation;

    private double[][] gridShare;
    private double[][] gridCO2;

    private double min;
    private double max;

    @Override
    public final void setSchedule(final EnergySchedule schedule) {
        baseLoad = schedule.normalizedBaseLoad();
        generation = schedule.normalizedGeneration();

        gridShare = new double[schedule.getGrid().getShareMap().size()][];
        int index = 0;
        for (MetricRecord record : schedule.getGrid().getShareMap().values()) {
            gridShare[index] = schedule.normalizeAsDouble(record);
            index++;
        }

        gridCO2 = new double[schedule.getGrid().getCO2Map().size()][];
        index = 0;
        for (MetricRecord record : schedule.getGrid().getCO2Map().values()) {
            gridCO2[index] = schedule.normalizeAsDouble(record);
            index++;
        }

        computeRange(schedule);
    }

    @Override
    public final String getDescription() {
        return "Environmental Impact Objective";
    }

    @Override
    public final double evaluate(final int[] solution,
                                 final double[] shiftableLoad) {
        double cost = 0;
        for (int i = 0; i < shiftableLoad.length; i++) {
            double imp;
            double cons = shiftableLoad[i] + baseLoad[i];
            //System.out.println("cons: " + cons);
            //System.out.println("gen: " + generation[i]);

            // co2 only on import
            if (generation[i] < cons) {
                imp = cons - generation[i];
                //System.out.println("imp: " + imp);

                //String opStr = "cost =";
                for (int j = 0; j < gridShare.length; j++) {
                    //opStr += " " + gridCO2[j][i] +"x"+(gridShare[j][i]/100)+"x"+imp;
                    cost += gridCO2[j][i] * (gridShare[j][i] / 100) * imp;

                    //double actual = (gridCO2[j][i]*(gridShare[j][i]/100)*imp);
                    //double min = (gridCO2[j][i]*(gridShare[j][i]/100)*(baseLoad[i]-generation[i]));
                }
                //System.out.println(opStr);
            }
        }

        // normalize cost
        //System.out.println("min=" + min + " max=" + max);
        cost = (cost - min) / (max - min);
        return cost;
    }

    public void computeRange(final EnergySchedule schedule) {

        // min = only baseload and gen (no interactive load: underestimated but easier...)
        double imp = 0;
        for (int i = 0; i < baseLoad.length; i++) {
            if (generation[i] < baseLoad[i]) {
                imp = baseLoad[i] - generation[i];
                for (int j = 0; j < gridShare.length; j++) {
                    min += gridCO2[j][i] * (gridShare[j][i] / 100) * imp;
                }
            }
        }

        // max : min + interactive loads at maximum co2 emission rate
        max = imp;
        for (double[] load : schedule.getCacheInteractiveLoad()) {
            for (double val : load) max += val;
        }

        double maxCo2 = 0;
        for (int i = 0; i < baseLoad.length; i++) {
            double sum = 0;
            for (int j = 0; j < gridShare.length; j++) {
                sum += gridCO2[j][i] * (gridShare[j][i] / 100);
            }
            if (sum > maxCo2) maxCo2 = sum;
        }

        max = max * maxCo2 + min;
    }

}
