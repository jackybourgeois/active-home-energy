package org.activehome.energy.preference;

/*
 * #%L
 * Active Home :: Energy :: Preference
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


/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class Preference {

    /**
     * Generate the matrix of user preferences composed of [nb device] rows and [nb time slot] cols
     * Values from 0 (avoid the device) to 1 (use the device), 0.5 means 'The user does not care about this device'
     *
     * @return
     */
//    public static UserPreferenceRecord generatePreference(final long start,
//                                                          final long horizon,
//                                                          final long granularity,
//                                                          final Device[] deviceArray,
//                                                          final DataLoader dl) {
//        UserPreferenceRecord up = new UserPreferenceRecord();
//
//        for (Device device : deviceArray) {
//            String deviceName = device.getID();
//            if (up.getUsagePreference().get(deviceName) == null) {
//                double[] appUsage = null;
//                int nbDay = 7;
//                for (int i = 1; i <= nbDay; i++) {
//                    long periodStart = start - i * horizon;
//                    MetricRecord mr = dl.loadMetricRecord(deviceName,
//                            periodStart, periodStart + horizon).get(deviceName);
//                    if (mr == null) mr = new MetricRecord(deviceName, horizon, periodStart, 0);
//                    if (i == 1) {
//                        appUsage = EnergyHelper.normalizedLoad(mr, periodStart, horizon, granularity);
//                    } else if (appUsage != null) {
//                        double[] dayUsage = EnergyHelper.normalizedLoad(mr, periodStart, horizon, granularity);
//                        for (int j = 0; j < appUsage.length; j++) appUsage[j] += dayUsage[j];
//                    }
//                }
//                if (appUsage != null) {
//                    MetricRecord mr = new MetricRecord(deviceName, horizon, start, appUsage[0] / nbDay);
//
//                    double max = appUsage[0] / nbDay;
//                    double min = appUsage[0] / nbDay;
//                    for (int i = 1; i < appUsage.length; i++) {
//                        appUsage[i] = appUsage[i] / nbDay;
//                        if (appUsage[i] > max) max = appUsage[i];
//                        else if (appUsage[i] < min) min = appUsage[i];
//                    }
//                    for (int i = 1; i < appUsage.length; i++) {
//                        if (max > 0) {
//                            mr.addRecord(start + i * granularity, (appUsage[i] - min) / (max - min));
//                        } else {
//                            mr.addRecord(start + i * granularity, 0);
//                        }
//                    }
//                    up.getUsagePreference().put(deviceName, mr);
//                }
//            }
//        }
//
//        return up;
//    }


}
