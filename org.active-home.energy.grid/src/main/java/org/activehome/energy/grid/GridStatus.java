package org.activehome.energy.grid;

import java.util.Map;

/**
 * @author Jacky
 * @version 17/09/2016.
 *
 * Values of the grid for a given time.
 */
public class GridStatus {

    private long timestamp;
    private int demand;
    private double frequency;
    private Map<FuelType,Integer> fuelMap;

    public GridStatus(final long timestamp,
                      final int demand,
                      final double frequency,
                      final Map<FuelType, Integer> fuelMap) {
        this.timestamp = timestamp;
        this.demand = demand;
        this.frequency = frequency;
        this.fuelMap = fuelMap;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getDemand() {
        return demand;
    }

    public double getFrequency() {
        return frequency;
    }

    public Map<FuelType, Integer> getFuelMap() {
        return fuelMap;
    }
}
