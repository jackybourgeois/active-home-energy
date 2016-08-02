package org.activehome.energy.battery;


public class BatteryInfo {


    private double maxChargingRate;
    private double maxDischargingRate;
    private double minSoC;
    private double maxSoC;
    private double capacityKWh;
    private double chargingEfficiency;
    private double dischargingEfficiency;

    public BatteryInfo(double maxChargingRate, double maxDischargingRate,
                       double minSoC, double maxSoC,
                       double capacityKWh,
                       double chargingEfficiency, double dischargingEfficiency) {
        this.maxChargingRate = maxChargingRate;
        this.maxDischargingRate = maxDischargingRate;
        this.minSoC = minSoC;
        this.maxSoC = maxSoC;
        this.capacityKWh = capacityKWh;
        this.chargingEfficiency = chargingEfficiency;
        this.dischargingEfficiency = dischargingEfficiency;
    }

    public double getMaxChargingRate() {
        return maxChargingRate;
    }

    public double getMaxDischargingRate() {
        return maxDischargingRate;
    }

    public double getMinSoC() {
        return minSoC;
    }

    public double getMaxSoC() {
        return maxSoC;
    }

    public double getCapacityKWh() {
        return capacityKWh;
    }

    public double getChargingEfficiency() {
        return chargingEfficiency;
    }

    public double getDischargingEfficiency() {
        return dischargingEfficiency;
    }
}
