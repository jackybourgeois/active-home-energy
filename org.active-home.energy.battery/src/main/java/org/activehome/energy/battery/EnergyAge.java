package org.activehome.energy.battery;

import org.activehome.com.Notif;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Schedule;
import org.activehome.tools.Convert;

import java.util.LinkedList;


public class EnergyAge {

    private BatteryInfo bat;

    private LinkedList<EnergyUnit> batteryEnergyUnits;
    private LinkedList<EnergyUnit> usedEnergyUnits;
    private double partialChargingUnit = 0;
    private double partialDischargingUnit = 0;
    private DataPoint batteryRate = null;

    private double latencyUnit;
    private boolean lifoMode;

    public EnergyAge(final double latencyUnit,
                     final BatteryInfo batteryInfo,
                     final boolean lifoMode) {
        batteryEnergyUnits = new LinkedList<>();
        usedEnergyUnits = new LinkedList<>();
        this.latencyUnit = latencyUnit;
        this.bat = batteryInfo;
        this.lifoMode = lifoMode;
    }

    public LinkedList<EnergyUnit> updateAge(final DataPoint dp) {
        double power = Double.valueOf(dp.getValue());
        LinkedList<EnergyUnit> changes;
        if (power > 0) {
            changes = updateAgeCharging(dp.getTS());
        } else {
            changes = updateAgeDischarging(dp.getTS());
        }
        batteryRate = dp;
        return changes;
    }

    private LinkedList<EnergyUnit> updateAgeCharging(final long ts) {
        LinkedList<EnergyUnit> chargedUnits = new LinkedList<>();
        if (batteryRate != null) {
            double power = Double.valueOf(batteryRate.getValue());
            double latencyUnitWatt = latencyUnit / 1000.;
            long lastTS = batteryRate.getTS();
            double energy = Convert.watt2kWh(power * bat.getChargingEfficiency(), ts - lastTS) + partialChargingUnit;
            int nbFullEnergyUnit = (int) (energy / latencyUnitWatt);
            partialChargingUnit = energy - (nbFullEnergyUnit * latencyUnitWatt);
            for (int i = 0; i < nbFullEnergyUnit; i++) {
                EnergyUnit energyUnit = new EnergyUnit(ts);
                batteryEnergyUnits.addLast(energyUnit);
                chargedUnits.addLast(energyUnit);
            }
        }
        return chargedUnits;
    }

    private LinkedList<EnergyUnit> updateAgeDischarging(final long ts) {
        LinkedList<EnergyUnit> dischargedUnits = new LinkedList<>();
        if (batteryRate != null) {
            double power = Double.valueOf(batteryRate.getValue());
            double latencyUnitWatt = latencyUnit / 1000.;
            long lastTS = batteryRate.getTS();
            double energy = Convert.watt2kWh(power * -1 * (2 - bat.getDischargingEfficiency()), ts - lastTS) + partialDischargingUnit;
            int nbFullEnergyUnit = (int) (energy / latencyUnitWatt);
            int toMove = nbFullEnergyUnit;
            partialDischargingUnit = energy - (nbFullEnergyUnit * latencyUnitWatt);
            while (nbFullEnergyUnit > 0 && batteryEnergyUnits.size() > 0) {
                EnergyUnit unit;
                if (lifoMode) {
                    unit = batteryEnergyUnits.removeLast();
                } else {
                    unit = batteryEnergyUnits.removeFirst();
                }
                unit.setTsOut(ts);
                usedEnergyUnits.addLast(unit);
                dischargedUnits.addLast(unit);
                nbFullEnergyUnit--;
            }
        }
        return dischargedUnits;
    }


    /**
     * Generate the average storing time
     * of the energy units currently inside the battery.
     */
    public final double averageCurrentAge(final long ts) {
        long currentTotalLatency = 0;
        for (EnergyUnit eu : batteryEnergyUnits) {
            currentTotalLatency += ts - eu.getTsIn();
        }
        double currentLatency = 0;
        if (batteryEnergyUnits.size() > 0) {
            currentLatency = (currentTotalLatency / 3600000.) / batteryEnergyUnits.size();
        }
        return currentLatency;
    }

    /**
     * Generate the average storing time
     * of the energy units discharged over the last hour.
     */
    public final double averageLastHourAge(final long ts) {
        long lastHourTotalAge = 0;
        for (EnergyUnit eu : usedEnergyUnits) {
            lastHourTotalAge += ts - eu.getTsIn();
        }

        double lastHourAge = 0;
        if (batteryEnergyUnits.size() > 0) {
            lastHourAge = (lastHourTotalAge / 3600000.) / batteryEnergyUnits.size();
        }
        return lastHourAge;
    }

    public void setBatteryRate(DataPoint batteryRate) {
        this.batteryRate = batteryRate;
    }

    public EnergyUnit oldestUnit() {
        if (batteryEnergyUnits.size()>0) {
            return batteryEnergyUnits.getFirst();
        }
        return null;
    }

    public EnergyUnit youngestUnit() {
        if (batteryEnergyUnits.size()>0) {
            return batteryEnergyUnits.getLast();
        }
        return null;
    }
}
