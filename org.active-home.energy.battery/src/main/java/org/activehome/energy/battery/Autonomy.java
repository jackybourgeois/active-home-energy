package org.activehome.energy.battery;


import org.activehome.com.Notif;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Schedule;

public class Autonomy {

    private BatteryInfo bat;

    private Double autonomyCurrentCons;
    private Double autonomyConsPred;
    private Double autonomyConsGenPrd;

    private double currentConsumption;
    private double currentSoCPercent;


    public Autonomy(final BatteryInfo batteryInfo) {
        this.bat = batteryInfo;
    }

    /**
     * Calculate the number of hours to discharge the battery till the minimum SoC (20%),
     * based on the current consumption (or maximum discharging rate)
     *
     * @param ts evaluation time
     */
    public final double basedOnCurrentCons(final long ts) {
        double autonomyHrs = 0;
        if (currentConsumption > 0 && currentSoCPercent > bat.getMinSoC()) {
            // limit the discharging rate to the maximum rate
            double power = currentConsumption;
            if (power > bat.getMaxDischargingRate()) {
                power = bat.getDischargingEfficiency();
            }

            double toDischarge = getCurrentSoCKWh() - (bat.getMinSoC() * (bat.getCapacityKWh() * 1000.) / 100);
            autonomyHrs = toDischarge / power;
        }
        if (autonomyHrs >= 0) {
            return autonomyHrs;
        }
        return 0;
    }

    /**
     * Calculate the number of hours to discharge the battery till the minimum SoC (20%),
     * based on the predicted consumption (or maximum discharging rate)
     *
     * @param ts evaluation time
     */
    public final double basedOnConsPrediction(final long ts,
                                              final Schedule prediction) {
        return 0;
    }

    /**
     * Calculate the number of hours to discharge the battery till the minimum SoC (20%),
     * based on the predicted consumption and generation (or maximum discharging rate)
     *
     * @param ts evaluation time
     */
    public final double basedOnConsGenPrediction(final long ts,
                                                 final Schedule prediction) {
        return 0;
    }

    public void setCurrentConsumption(double currentConsumption) {
        this.currentConsumption = currentConsumption;
    }

    public void setCurrentSoCPercent(double currentSoCPercent) {
        this.currentSoCPercent = currentSoCPercent;
    }

    private double getCurrentSoCKWh() {
        return currentSoCPercent*bat.getCapacityKWh()/100.;
    }

    public Double getAutonomyCurrentCons() {
        return autonomyCurrentCons;
    }

    public Double getAutonomyConsPred() {
        return autonomyConsPred;
    }

    public Double getAutonomyConsGenPrd() {
        return autonomyConsGenPrd;
    }
}
