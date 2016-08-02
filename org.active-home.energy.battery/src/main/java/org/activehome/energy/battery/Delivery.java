package org.activehome.energy.battery;

import org.activehome.context.data.Schedule;

public class Delivery {

    private BatteryInfo bat;

    private Double remainingCurrentGen;
    private Double remainingGenPred;
    private Double remainingGenConsPrd;

    private double generation = 0;
    private double currentSoCPercent;
    private double currentSoCKWh;

    public Delivery(final BatteryInfo batteryInfo) {
        this.bat = batteryInfo;
    }

    /**
     * Calculate the number of hours remaining to fully charge the battery,
     * based on the current generation (or maximum charging rate)
     *
     * @param ts evaluation time
     */
    public final double basedOnCurrentGen(final long ts) {
        // we are generating and the battery is not fully charged yet
        double deliveryTime = 0;
        if (generation > 0 && currentSoCPercent < bat.getMaxSoC()) {
            // limit the charging rate to the maximum charging rate
            double power = generation;
            if (power > bat.getMaxChargingRate()) {
                power = bat.getMaxChargingRate();
            }

            double toCharge = (bat.getMaxSoC() * (bat.getCapacityKWh() * 1000.) / 100) - currentSoCKWh;
            deliveryTime = toCharge / power;

            if (deliveryTime >= 0) {
                return deliveryTime;
            }
        }
        return deliveryTime;
    }

    /**
     * Calculate the number of hours remaining to fully charge the battery,
     * based on the predicted generation (or maximum charging rate)
     *
     * @param ts evaluation time
     */
    public final double basedOnGenPrediction(final long ts,
                                             final Schedule prediction) {
        return 0;
    }

    /**
     * Calculate the number of hours remaining to fully charge the battery,
     * based on the predicted generation and Consumption (or maximum charging rate)
     *
     * @param ts evaluation time
     */
    public final double basedOnGenConsPrediction(final long ts,
                                                 final Schedule prediction) {
        return 0;
    }

    public void setGeneration(double generation) {
        this.generation = generation;
    }

    public void setCurrentSoCPercent(double currentSoCPercent) {
        this.currentSoCPercent = currentSoCPercent;
    }

    public void setCurrentSoCKWh(double currentSoCKWh) {
        this.currentSoCKWh = currentSoCKWh;
    }

    public Double getRemainingCurrentGen() {
        return remainingCurrentGen;
    }

    public Double getRemainingGenPred() {
        return remainingGenPred;
    }

    public Double getRemainingGenConsPrd() {
        return remainingGenConsPrd;
    }
}
