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



import org.activehome.energy.library.schedule.EnergySchedule;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class MinimizeUserCosts implements ScheduleObjective {

    private double[] baseLoad;
    private double[] generation;

    private double[] importTariff;
    private double[] exportTariff;
    private double[] generationTariff;

    private double min;
    private double max;


    @Override
    public String getDescription() {
        return "Cost Objective";
    }


    @Override
    public void setSchedule(EnergySchedule schedule) {
        baseLoad = schedule.normalizedBaseLoad();

        generation = schedule.normalizedGeneration();

        importTariff = schedule.normalizeAsDouble(schedule.getTariff().getImport());
        exportTariff = schedule.normalizeAsDouble(schedule.getTariff().getExport());
        generationTariff = schedule.normalizeAsDouble(schedule.getTariff().getGeneration());

        computeRange(schedule);
    }


    @Override
    public double evaluate(int[] solution, double[] shiftableLoad) {
        //System.out.println("start eval cost");
        double cost = 0.;
        double exp, imp;
//        System.out.println();
        for (int i=0;i<shiftableLoad.length;i++) {
            double cons = shiftableLoad[i]+ baseLoad[i];
//            System.out.print(cons + " , ");
            //System.out.println("cons: " + cons + " gen: " + generation[i]);
            if (generation[i]>cons) {
                exp = generation[i]-cons;
                imp=0;
            } else {
                imp = cons- generation[i];
                exp=0;
            }
            cost += importTariff[i]*imp- exportTariff[i]*exp- generationTariff[i]* generation[i];
//            System.out.print(cost + " , ");
            //System.out.println("cost: " + cost + " impTariff: " + importTariff[i]);
        }

        // normalize cost
        //System.out.println("min=" + min + " max=" + max);

        /*
        DecimalFormat d = new DecimalFormat("#.###");

        System.out.println("cost: " + d.format(cost) + " normalized: " + d.format((cost-min) / (max-min)));
        //for (double shiftable : shiftableLoad) System.out.print(String.format("%5d",shiftable) + "\t");
        System.out.print("shift: ");
        for (double shift : shiftableLoad) {
            System.out.format("%5f", shift);
            System.out.print("\t");
        }
        System.out.println();
        System.out.print("base:  ");
        for (double base : baseLoad) {
            System.out.format("%5f", base);
            System.out.print("\t");
        }
        System.out.println();
        System.out.print("gen:   ");
        for (double gen : generation) {
            System.out.format("%5f", gen);
            System.out.print("\t");
        }
        System.out.println();
        System.out.print("impt:  ");
        for (double impt : importTariff) {
            System.out.format("%5f", impt);
            System.out.print("\t");
        }
        System.out.println();
        System.out.print("min:   ");
        for (int i=0;i<baseLoad.length;i++) {
            if (generation[i] > baseLoad[i]) {
                exp = generation[i] - baseLoad[i];
                imp = 0;
            } else {
                imp = baseLoad[i] - generation[i];
                exp=0;
            }
            System.out.format("%5f", importTariff[i]*imp-exportTariff[i]*exp-generationTariff[i]*generation[i]);
            System.out.print("\t");
        }
        System.out.println();
        System.out.print("cost:  ");
        for (int i=0;i<baseLoad.length;i++) {
            double cons = shiftableLoad[i]+baseLoad[i];
            if (generation[i]>cons) {
                exp = generation[i]-cons;
                imp=0;
            } else {
                imp = cons-generation[i];
                exp=0;
            }
            System.out.format("%5f", importTariff[i]*imp-exportTariff[i]*exp-generationTariff[i]*generation[i]);
            System.out.print("\t");
        }
        System.out.println();*/

        cost = (cost- min) / (max - min);
        return cost;
    }

    void computeRange(EnergySchedule schedule) {

        // min = only baseload and gen
        double exp,imp;
        for (int i=0;i< baseLoad.length;i++) {
            if (generation[i] > baseLoad[i]) {
                exp = generation[i] - baseLoad[i];
                imp=0;
            } else {
                imp = baseLoad[i] - generation[i];
                exp=0;
            }
            min += importTariff[i]*imp- exportTariff[i]*exp- generationTariff[i]* generation[i];
        }

        // max = min + sum of all interactive loads when import tariff is the highest
        max = 0;
        for (double[] load : schedule.getCacheInteractiveLoad()) {
            for (double val : load) max += val;
        }

        double maxImpTariff = importTariff[0];
        for (double val : importTariff) if (maxImpTariff<val) maxImpTariff=val;
        //System.out.println("max imp tariff: " + maxImpTariff + " max energy: " + max);
        max = max * maxImpTariff;

        if (min >0) max += min;
    }

}
