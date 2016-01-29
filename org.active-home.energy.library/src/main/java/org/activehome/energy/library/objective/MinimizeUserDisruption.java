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
import org.activehome.energy.library.record.UserPreferenceRecord;
import org.activehome.energy.library.schedule.EnergySchedule;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class MinimizeUserDisruption implements ScheduleObjective {

    private EnergySchedule schedule;
    private UserPreferenceRecord userPreference;

    private double max;

    @Override
    public final void setSchedule(final EnergySchedule theSchedule) {
        schedule = theSchedule;
        userPreference = schedule.getUserPreference();
        computeRange();
    }

    @Override
    public final String getDescription() {
        return "Minimize User Disruption";
    }

    @Override
    public final double evaluate(final int[] solution,
                                 final double[] shiftableLoad) {
        double score = 0;
        int i = 0;
        for (String device : schedule.getLoad().getInteractives().keySet()) {
            for (MetricRecord mr : schedule.getLoad().getInteractives().get(device)) {
                String[] pref = schedule.normalize(userPreference.getUsagePreference().get(mr.getMetricId()));
                int lgt = (int) ((mr.getlastTS() - mr.getStartTime()) / schedule.getGranularity()) + 1;
                for (int j = solution[i]; j < solution[i] + lgt; j++) {
                    score += Double.valueOf(pref[j]);
                }
                i++;
            }
        }
        return 1 - (score / max);
    }

    public void computeRange() {
        max = 0;
        for (String device : schedule.getLoad().getInteractives().keySet()) {
            for (MetricRecord mr : schedule.getLoad().getInteractives().get(device)) {
                max += (int) ((mr.getlastTS() - mr.getStartTime()) / schedule.getGranularity()) + 1;
            }
        }
    }
}


