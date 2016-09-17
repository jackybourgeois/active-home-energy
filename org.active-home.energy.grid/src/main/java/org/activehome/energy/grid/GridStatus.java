package org.activehome.energy.grid;

import java.util.Map;
import java.util.Set;

/**
 * @author Jacky
 * @version 17/09/2016.
 *          <p>
 *          Values of the grid for a given time.
 */
public class GridStatus {

    private long timestamp;
    private int demand;
    private double frequency;
    private Map<FuelType, Integer> fuelMap;

    public GridStatus(final long timestamp,
                      final int demand,
                      final double frequency,
                      final Map<FuelType, Integer> fuelMap) {
        this.timestamp = timestamp;
        this.demand = demand;
        this.frequency = frequency;
        this.fuelMap = fuelMap;
    }

    /**
     * Sum up the generation from all types
     *
     * @return total generation power in MegaWatt
     */
    public int computeTotalGeneration() {
        int totalPower = 0;
        for (Integer power : fuelMap.values()) {
            if (power > 0) {
                totalPower += power;
            }
        }
        return totalPower;
    }

    /**
     * Compute the carbon intensity based on the share of each fuel
     *
     * @return carbon intensity in gCO2/kWh
     */
    public double computeCarbonIntensity() {
        double totalGen = computeTotalGeneration();
        double carbonIntensity = 0;
        for (Map.Entry<FuelType, Integer> entry : fuelMap.entrySet()) {
            if (entry.getValue() > 0) {
                // for each generation (>0), get the share wrt the total generation
                carbonIntensity += (entry.getValue() / totalGen)
                        * entry.getKey().getCarbonIntensity();
            }
        }
        return carbonIntensity;
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
