package org.activehome.energy.predictor.emulator;

/*
 * #%L
 * Active Home :: Energy :: Predictor :: Emulator
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


import org.activehome.context.data.MetricRecord;

import java.text.DecimalFormat;

/**
 * Build a sampled metric record based on provided data.
 * The sample size is defined by the granularity.
 * Each sample value is an average of data for this sample.
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class SampledMetricRecordBuilder {
    /**
     * The MetricRecord to build.
     */
    private MetricRecord metricRecord;
    /**
     * Start timestamp of the MetricRecord.
     */
    private long startTS;
    /**
     * End timestamp of the MetricRecord.
     */
    private long endTS;
    /**
     * Size of each sample.
     */
    private long granularity;
    /**
     * Value of the last data added.
     */
    private Double prevVal = null;
    /**
     * Timestamp of the last data added.
     */
    private Long prevTS = null;
    /**
     * Weighted sum of the current sample.
     */
    private double weightedSum = 0;
    /**
     * Index of the current sample.
     */
    private int indexSample = 0;


    private static DecimalFormat df;

    /**
     * Initialize the MetricRecord.
     *
     * @param metricID       metric id of the MetricRecord
     * @param theStartTS     start timestamp of the MetricRecord
     * @param theEndTS       end timestamp of the MetricRecord
     * @param theGranularity Size of each sample
     */
    public SampledMetricRecordBuilder(final String metricID,
                                      final long theStartTS,
                                      final long theEndTS,
                                      final long theGranularity) {
        startTS = theStartTS;
        endTS = theEndTS;
        granularity = theGranularity;
        metricRecord = new MetricRecord(metricID, endTS - startTS);
        df = new DecimalFormat("#.#####");
    }

    /**
     * Add the value to the weighted sum for the current sample
     * or compute the sample and add the value to the next sample.
     *
     * @param ts    timestamp of the new data
     * @param value value of the new data
     */
    public final void addValue(final long ts,
                               final double value) {
        long currentSlot = startTS + granularity * indexSample;
        long nextSlot = currentSlot + granularity;
        if (prevTS != null) {
            if (nextSlot < ts) {
                weightedSum += prevVal * (nextSlot - prevTS);
                double slotVal = weightedSum / granularity;

                metricRecord.addRecord(currentSlot, granularity, df.format(slotVal), "prediction", 1);
                indexSample++;

                currentSlot = startTS + granularity * indexSample;
                nextSlot = currentSlot + granularity;
                while (nextSlot < ts) {
                    metricRecord.addRecord(currentSlot, granularity, df.format(prevVal), "prediction", 1);
                    indexSample++;
                    currentSlot = startTS + granularity * indexSample;
                    nextSlot = currentSlot + granularity;
                }

                weightedSum = prevVal * (ts - currentSlot);
            } else {
                weightedSum += prevVal * (ts - prevTS);
            }
        }
        prevTS = ts;
        prevVal = value;
    }

    /**
     * Finish to complete the last samples with the last value
     * received, then return the MetricRecord.
     *
     * @return MetricRecord completed
     */
    public final MetricRecord getMetricRecord() {
        long currentSlot = startTS + granularity * indexSample;
        long nextSlot = currentSlot + granularity;
        boolean first = true;
        while (currentSlot < endTS) {
            double slotVal = prevVal;
            if (first) {
                weightedSum += prevVal * (nextSlot - prevTS);
                slotVal = weightedSum / granularity;
                first = false;
            }
            metricRecord.addRecord(currentSlot, granularity, df.format(slotVal), "prediction", 1);
            indexSample++;
            currentSlot = nextSlot;
            nextSlot += granularity;
        }
        return metricRecord;
    }

    /**
     * The metric id of the MetricRecord currently built.
     *
     * @return the metric id
     */
    public final String getMetric() {
        return metricRecord.getMetricId();
    }

}
