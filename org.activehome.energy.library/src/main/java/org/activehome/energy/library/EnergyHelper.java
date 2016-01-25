package org.activehome.energy.library;

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


import org.activehome.context.data.Event;
import org.activehome.context.data.Device;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Record;
import org.activehome.context.data.SampledRecord;
import org.activehome.tools.Convert;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class EnergyHelper {

    public static final double DEFAULT_ON_OFF_THRESHOLD = 0;
    public static final long DEFAULT_MAX_DURATION_WITHOUT_CHANGE = 900000;
    public static final long DEFAULT_MIN_DURATION = 600000;

    public static LinkedList<MetricRecord> loadDetector(final MetricRecord metricRecord,
                                                        final double onOffThreshold,
                                                        final long granularity) {
        return loadDetector(metricRecord, onOffThreshold,
                DEFAULT_MAX_DURATION_WITHOUT_CHANGE, DEFAULT_MIN_DURATION, granularity);
    }

    public static LinkedList<MetricRecord> loadDetector(final MetricRecord metricRecord,
                                                        final Device device,
                                                        final long granularity) {
        double onOffThreshold = DEFAULT_ON_OFF_THRESHOLD;
        if (device.getAttributeMap().containsKey("onOffThreshold")) {
            onOffThreshold = Double.valueOf(device.getAttributeMap().get("onOffThreshold"));
        }
        long maxDurationWithoutChange = DEFAULT_MAX_DURATION_WITHOUT_CHANGE;
        if (device.getAttributeMap().containsKey("maxDurationWithoutChange")) {
            maxDurationWithoutChange = Long.valueOf(device.getAttributeMap().get("maxDurationWithoutChange"));
        }
        long minDuration = DEFAULT_MIN_DURATION;
        if (device.getAttributeMap().containsKey("minDuration")) {
            minDuration = Long.valueOf(device.getAttributeMap().get("minDuration"));
        }

        return loadDetector(metricRecord, onOffThreshold, maxDurationWithoutChange,
                minDuration, granularity);
    }

    /**
     * Build a list of Event out of a metric record.
     *
     * @param metricRecord
     * @param onOffThreshold
     * @param maxDurationWithoutChange
     * @param minimumDuration
     * @return
     */
    public static LinkedList<Event> energyEvents(final MetricRecord metricRecord,
                                                 final double onOffThreshold,
                                                 final long maxDurationWithoutChange,
                                                 final long minimumDuration,
                                                 final long granularity) {
        LinkedList<MetricRecord> loads = loadDetector(metricRecord,
                onOffThreshold, maxDurationWithoutChange, minimumDuration, granularity);
        LinkedList<Event> events = new LinkedList<>();
        for (MetricRecord load : loads) {
            HashMap<String, MetricRecord> map = new HashMap<>();
            map.put("power", load);
            events.add(new Event(load.getStartTime(), load.getTimeFrame(), map, load.isRecording()));
        }
        return events;
    }

    /**
     * Transform a MetricRecord into a list of smaller one's (for each load)
     *
     * @param srcMR
     * @return
     */
    public static LinkedList<MetricRecord> loadDetector(final MetricRecord srcMR,
                                                        final double onOffThreshold,
                                                        final long maxDurationWithoutChange,
                                                        final long minimumDuration,
                                                        final long granularity) {
        LinkedList<MetricRecord> loadList = new LinkedList<>();
        MetricRecord currentLoad = null;

        long granularityAndMax = maxDurationWithoutChange;
        if (maxDurationWithoutChange < granularity) {
            granularityAndMax += granularity;
        }
        LinkedList<Record> records = srcMR.getRecords();
        if (records != null && records.size() > 0) {
            long lastChangeTS = 0;
            double lastChangeVal = 0;
            for (int i = 0; i < records.size(); i++) {
                Record record = records.get(i);
                double val = record.getDouble();

                if (currentLoad == null && val > onOffThreshold) {    // start new load
                    currentLoad = new MetricRecord(srcMR.getMetricId());
                    if (record instanceof SampledRecord) {
                        currentLoad.addRecord(srcMR.getStartTime() + record.getTS(),
                                ((SampledRecord) record).getDuration(),
                                record.getValue(), srcMR.getMainVersion(), record.getConfidence());
                    } else {
                        currentLoad.addRecord(srcMR.getStartTime() + record.getTS(),
                                record.getValue(), srcMR.getMainVersion(), record.getConfidence());
                    }
                } else if (currentLoad != null && i < records.size() - 1
                        && ((records.get(i + 1).getTS() - records.get(i).getTS() > granularityAndMax)
                        || (records.get(i + 1).getDouble() <= onOffThreshold
                        && records.get(i + 1).getTS() - lastChangeTS > maxDurationWithoutChange))) {
                    // end of load
                    if (val > onOffThreshold) {
                        if (record instanceof SampledRecord) {
                            currentLoad.addRecord(srcMR.getStartTime() + record.getTS(),
                                    ((SampledRecord) record).getDuration(),
                                    record.getValue(), record.getConfidence());
                        } else {
                            currentLoad.addRecord(srcMR.getStartTime() + record.getTS(),
                                    record.getValue(), record.getConfidence());
                        }
                    }

                    while (currentLoad.getRecords().size()>0 && currentLoad.getRecords().getLast().getDouble() <= onOffThreshold) {
                        currentLoad.getRecords().pollLast();
                    }

                    if (currentLoad.getlastTS() - currentLoad.getStartTime() > minimumDuration) {
                        currentLoad.setRecording(false);
                        currentLoad.addRecord(currentLoad.getStartTime() + currentLoad.getTimeFrame(), "0", 1);
                        loadList.addLast(currentLoad);
                    }
                    currentLoad = null;
                } else if (currentLoad != null) {                                         // in a load
                    if (record instanceof SampledRecord) {
                        currentLoad.addRecord(srcMR.getStartTime() + record.getTS(),
                                ((SampledRecord) record).getDuration(),
                                record.getValue(), record.getConfidence());
                    } else {
                        currentLoad.addRecord(srcMR.getStartTime() + record.getTS(),
                                record.getValue(), record.getConfidence());
                    }
                }

                if (val != lastChangeVal) {
                    lastChangeVal = val;
                    lastChangeTS = record.getTS();
                }
            }

            if (currentLoad != null &&
                    currentLoad.getlastTS() - currentLoad.getStartTime() > minimumDuration) {
                currentLoad.setRecording(false);
                loadList.addLast(currentLoad);
            }
        }

        return loadList;
    }

    /**
     * Convert instant power into energy
     */
    public static double[] normalizedLoad(final MetricRecord load,
                                          final long start,
                                          final long horizon,
                                          final long granularity) {
        double[] data = new double[(int) (horizon / granularity)];

        // set the index just before the load start
        int indexSlot = (int) ((load.getStartTime() - start) / granularity);

        double power = 0;
        if (load.getRecords().size() > 0) power = load.getRecords().getFirst().getDouble();
        long lastTS = load.getStartTime();
        for (Record record : load.getRecords()) {
            long currentTS = load.getStartTime() + record.getTS();
            long nextSlotStart = start + (indexSlot + 1) * granularity;
            if (currentTS < nextSlotStart) {   // before next slot, add energy the new val
                data[indexSlot] += Convert.watt2kWh(power, currentTS - lastTS);
                lastTS = currentTS;
            } else {
                while (currentTS > nextSlotStart) {
                    data[indexSlot] += Convert.watt2kWh(power, nextSlotStart - lastTS);
                    lastTS = nextSlotStart;
                    indexSlot++;
                    nextSlotStart = start + (indexSlot + 1) * granularity;
                }
                data[indexSlot] += Convert.watt2kWh(power, currentTS - lastTS);
            }
            power = record.getDouble();
        }
        return data;
    }

    public static TreeMap<String, MetricRecord> generateGenericGridCO2(final long start,
                                                                       final long horizon) {
        TreeMap<String, MetricRecord> gridCO2Map = new TreeMap<>();
        gridCO2Map.put("biomass", new MetricRecord("biomass", horizon, "", start, "610", 1));
        gridCO2Map.put("coal", new MetricRecord("coal", horizon, "", start, "910", 1));
        gridCO2Map.put("nuclear", new MetricRecord("nuclear", horizon, "", start, "16", 1));
        gridCO2Map.put("dutch_int", new MetricRecord("dutch_int", horizon, "", start, "392", 1));
        gridCO2Map.put("french_int", new MetricRecord("french_int", horizon, "", start, "83", 1));
        gridCO2Map.put("ocgt", new MetricRecord("ocgt", horizon, "", start, "479", 1));
        gridCO2Map.put("oil", new MetricRecord("oil", horizon, "", start, "610", 1));
        gridCO2Map.put("gas", new MetricRecord("gas", horizon, "", start, "360", 1));
        gridCO2Map.put("hydro", new MetricRecord("hydro", horizon, "", start, "0", 1));
        gridCO2Map.put("wind", new MetricRecord("wind", horizon, "", start, "0", 1));
        gridCO2Map.put("ni_int", new MetricRecord("ni_int", horizon, "", start, "699", 1));
        gridCO2Map.put("eire_int", new MetricRecord("eire_int", horizon, "", start, "300", 1));
        gridCO2Map.put("net_pumped", new MetricRecord("net_pumped", horizon, "", start, "0", 1));
        return gridCO2Map;
    }


}
