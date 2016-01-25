package org.activehome.energy.library.objective;

/*
 * #%L
 * Active Home :: Energy :: Library
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


import java.util.ArrayList;
import java.util.Map;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class HelperObjective {

    public static ArrayList<int[]> sortSolutionByObjective(final Map<int[], double[]> solutions,
                                                               final ArrayList<ScheduleObjective> scheduleObjectiveList) {
        ArrayList<int[]> sortedSolutionList = new ArrayList<>();
        for (int[] sol : solutions.keySet()) {
            double[] score = solutions.get(sol);
            boolean isBetter = false;
            boolean isEqual;
            int i = 0;
            while (!isBetter && i < sortedSolutionList.size()) {
                int j = 0;
                isEqual = true;
                while (!isBetter && isEqual && j < scheduleObjectiveList.size()) {
                    if (score[j] > sortedSolutionList.get(i)[j]) isBetter = true;
                    else if (score[j] < sortedSolutionList.get(i)[j]) isEqual = false;
                    j++;
                }
                i++;
            }
            if (isBetter) {
                sortedSolutionList.add(i - 1, sol);
            } else {
                sortedSolutionList.add(i, sol);
            }
        }

        return sortedSolutionList;
    }


}
