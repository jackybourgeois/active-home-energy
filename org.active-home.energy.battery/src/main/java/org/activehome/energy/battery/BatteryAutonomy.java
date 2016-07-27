package org.activehome.energy.battery;

/*
 * #%L
 * Active Home :: Energy :: Battery
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


import com.eclipsesource.json.JsonObject;
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.Status;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.UserInfo;
import org.activehome.service.RequestHandler;
import org.activehome.service.Service;
import org.activehome.tools.Convert;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;

import java.util.LinkedList;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class BatteryAutonomy extends Service implements RequestHandler {

    @Param(defaultValue = "Provide a set of estimation of a battery autonomy.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.battery")
    private String src;
    @Param(defaultValue = "/active-home-energy/master/org.active-home.energy.battery/docs/DemoBatteryAutonomy.kevs")
    private String demo;

    /**
     * The necessary bindings.
     */
    @Param(defaultValue = "getNotif>Context.pushDataToSystem")
    private String bindingBatteryAutonomy;

    /**
     * The maximum rate that we can take out of the battery
     * (should be found dynamically).
     */
    @Param(defaultValue = "9.6")
    private double capacityKWh;
    /**
     * The maximum rate that we can take out of the battery
     * (should be found dynamically).
     */
    @Param(defaultValue = "2500")
    private int maxDischargingRate;
    /**
     * The maximum rate that we can push energy into the battery
     * (should be found dynamically).
     */
    @Param(defaultValue = "2500")
    private int maxChargingRate;
    /**
     * The min state of charge to avoid damaging the battery
     * (should be found dynamically).
     */
    @Param(defaultValue = "20")
    private int minSoC;
    /**
     * The min state of charge to avoid damaging the battery
     * (should be found dynamically).
     */
    @Param(defaultValue = "100")
    private int maxSoC;
    /**
     * The min state of charge to avoid damaging the battery
     * (should be found dynamically).
     */
    @Param(defaultValue = "0.87")
    private double dischargingEfficiency;
    /**
     * The min state of charge to avoid damaging the battery
     * (should be found dynamically).
     */
    @Param(defaultValue = "0.82")
    private double chargingEfficiency;
    /**
     * The size of an energy unit in watt-hour
     */
    @Param(defaultValue = "100")
    private double latencyUnit;

    private double currentConsumption = -1;
    private double currentSoCKWh = -1;
    private double currentSoCPercent = -1;
    private Status currentStatus = Status.UNKNOWN;
    private DataPoint currentBatteryPower = null;

    private Double autonomyCurrentCons;
    private Double autonomyConsPred;
    private Double autonomyConsGenPrd;

    private Double remainingCurrentGen;
    private Double remainingGenPred;
    private Double remainingGenConsPrd;

    private double solaxGen1 = 0;
    private double solaxGen2 = 0;

    private LinkedList<EnergyUnit> batteryEnergyUnits;
    private LinkedList<EnergyUnit> usedEnergyUnits;
    private double partialChargingUnit = 0;
    private double partialDischargingUnit = 0;

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        super.onInit();
        listenAPI(getNode() + ".http", "/battery", true);
        subscribeToContext("storage.availabilityKWh", "storage.availabilityPercent",
                "power.cons", "power.storage", "storage.status", "power.gen.solax1", "power.gen.solax2");
        batteryEnergyUnits = new LinkedList<>();
        usedEnergyUnits = new LinkedList<>();
    }

    @Override
    protected RequestHandler getRequestHandler(Request request) {
        return new BatteryAutonomyRequestHandler(request, this);
    }

    /**
     * Receive data from context
     *
     * @param notifStr the Notif as string
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0
                && notif.getContent() instanceof DataPoint) {
            DataPoint dp = (DataPoint) notif.getContent();
            switch (dp.getMetricId()) {
                case "power.cons":
                    currentConsumption = Double.valueOf(dp.getValue());
                    autonomyHrsCurrentCons(dp.getTS());
                    break;
                case "storage.availabilityKWh":
                    currentSoCKWh = Double.valueOf(dp.getValue());
                    break;
                case "storage.availabilityPercent":
                    currentSoCPercent = Double.valueOf(dp.getValue());
                    autonomyHrsCurrentCons(dp.getTS());
                    remainingHrsCurrentGen(dp.getTS());
                    break;
                case "storage.status":
                    currentStatus = Status.valueOf(dp.getValue());
                    break;
                case "power.gen.solax1":
                    solaxGen1 = Double.valueOf(dp.getValue());
                    remainingHrsCurrentGen(dp.getTS());
                    break;
                case "power.gen.solax2":
                    solaxGen2 = Double.valueOf(dp.getValue());
                    remainingHrsCurrentGen(dp.getTS());
                    break;
                case "power.storage":
                    double power = Double.valueOf(dp.getValue());
                    if (power > 0) {
                        updateLatencyCharging(power, dp.getTS());
                    } else if (power < 0) {
                        updateLatencyDischarging(power, dp.getTS());
                    }
                    currentBatteryPower = dp;
                    break;
                default:

            }
        }
    }

    private void updateLatencyCharging(final double power,
                                       final long ts) {
        double latencyUnitWatt = latencyUnit / 1000.;
        if (currentBatteryPower != null) {
            long lastTS = currentBatteryPower.getTS();
            double energy = Convert.watt2kWh(power * chargingEfficiency, ts - lastTS) + partialChargingUnit;
            int nbFullEnergyUnit = (int) (energy / latencyUnitWatt);
            partialChargingUnit = energy - (nbFullEnergyUnit * latencyUnitWatt);
            for (int i = 0; i < nbFullEnergyUnit; i++) {
                batteryEnergyUnits.addLast(new EnergyUnit(ts));
            }
//            logInfo("adding " + nbFullEnergyUnit + " energy units in the battery, total: " + batteryEnergyUnits.size());

            computeCurrentLatency(ts);
            computeLastHourLatency(ts);
        }
    }

    private void updateLatencyDischarging(final double power,
                                          final long ts) {
        double latencyUnitWatt = latencyUnit / 1000.;
        if (currentBatteryPower != null) {
            long lastTS = currentBatteryPower.getTS();
            double energy = Convert.watt2kWh(power * -1 * (2 - dischargingEfficiency), ts - lastTS) + partialDischargingUnit;
            int nbFullEnergyUnit = (int) (energy / latencyUnitWatt);
            int toMove = nbFullEnergyUnit;
            partialDischargingUnit = energy - (nbFullEnergyUnit * latencyUnitWatt);
            while (nbFullEnergyUnit > 0 && batteryEnergyUnits.size() > 0) {
                EnergyUnit unit = batteryEnergyUnits.removeFirst();
                unit.setTsOut(ts);
                usedEnergyUnits.addLast(unit);
                nbFullEnergyUnit--;
            }
//            logInfo("moved " + toMove + " energy unit from the battery to the used stack. " +
//                    "now in bat: " + batteryEnergyUnits.size() + " and used: " + usedEnergyUnits.size());
        }
    }

    /**
     * Calculate the number of hours to discharge the battery till the minimum SoC (20%),
     * based on the current consumption (or maximum discharging rate)
     *
     * @param ts evaluation time
     */
    public final void autonomyHrsCurrentCons(final long ts) {
        double autonomyHrs = 0;
        if (currentConsumption > 0 && currentSoCPercent > minSoC) {
            // limit the discharging rate to the maximum rate
            double power = currentConsumption;
            if (power > maxDischargingRate) {
                power = maxDischargingRate;
            }

            double toDischarge = currentSoCKWh - (minSoC * (capacityKWh*1000.) / 100);
            autonomyHrs = toDischarge / power;
        }
        if (autonomyHrs >= 0) {
            sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(),
                    new DataPoint("storage.autonomy.currentCons", ts, autonomyHrs + "")));
        }
    }

    /**
     * Calculate the number of hours to discharge the battery till the minimum SoC (20%),
     * based on the predicted consumption (or maximum discharging rate)
     *
     * @param ts evaluation time
     */
    public final void autonomyHrsConsPrediction(final long ts,
                                                final Schedule prediction) {

    }

    /**
     * Calculate the number of hours to discharge the battery till the minimum SoC (20%),
     * based on the predicted consumption and generation (or maximum discharging rate)
     *
     * @param ts evaluation time
     */
    public final void autonomyHrsConsGenPrediction(final long ts,
                                                   final Schedule prediction) {

    }

    /**
     * Calculate the number of hours remaining to fully charge the battery,
     * based on the current generation (or maximum charging rate)
     *
     * @param ts evaluation time
     */
    public final void remainingHrsCurrentGen(final long ts) {
        double totGen = solaxGen1 + solaxGen2;
        // we are generating and the battery is not fully charged yet
        if (totGen > 0 && currentSoCPercent < maxSoC) {
            // limit the charging rate to the maximum charging rate
            double power = totGen;
            if (power > maxChargingRate) {
                power = maxChargingRate;
            }

            double toCharge = (maxSoC * (capacityKWh*1000.) / 100) - currentSoCKWh;
            double remainingHrs = toCharge / power;

            if (remainingHrs >= 0) {
                sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(),
                        new DataPoint("storage.toCharge.currentGen", ts, remainingHrs + "")));
            }
        } else if (currentSoCPercent == maxSoC) {
            sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(),
                    new DataPoint("storage.toCharge.currentGen", ts, "0")));
        }
    }

    /**
     * Calculate the number of hours remaining to fully charge the battery,
     * based on the predicted generation (or maximum charging rate)
     *
     * @param ts evaluation time
     */
    public final void remainingHrsGenPrediction(final long ts,
                                                final Schedule prediction) {

    }

    /**
     * Calculate the number of hours remaining to fully charge the battery,
     * based on the predicted generation and Consumption (or maximum charging rate)
     *
     * @param ts evaluation time
     */
    public final void remainingHrsGenConsPrediction(final long ts,
                                                    final Schedule prediction) {

    }

    /**
     * Generate storage.latency.current, the average storing time
     * of the energy units currently inside the battery.
     */
    public final void computeCurrentLatency(final long ts) {
        long currentTotalLatency = 0;
        for (EnergyUnit eu : batteryEnergyUnits) {
            currentTotalLatency += ts - eu.getTsIn();
        }
        double currentLatency = 0;
        if (batteryEnergyUnits.size() > 0) {
            currentLatency = (currentTotalLatency / 3600000.) / batteryEnergyUnits.size();
        }
        sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(),
                new DataPoint("storage.latency.current", ts, currentLatency + "")));
    }

    /**
     * Generate storage.latency.lastHour, the average storing time
     * of the energy units discharged over the last hour.
     */
    public final void computeLastHourLatency(final long ts) {
        long lastHourTotalLatency = 0;
        for (EnergyUnit eu : usedEnergyUnits) {
            lastHourTotalLatency += ts - eu.getTsIn();
        }

        double lastHourLatency = 0;
        if (batteryEnergyUnits.size() > 0) {
            lastHourLatency = (lastHourTotalLatency / 3600000.) / batteryEnergyUnits.size();
        }
        sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(),
                new DataPoint("storage.latency.lastHour", ts, lastHourLatency + "")));
    }

    public double getCurrentConsumption() {
        return currentConsumption;
    }

    public double getCurrentSoCKWh() {
        return currentSoCKWh;
    }

    public double getCurrentSoCPercent() {
        return currentSoCPercent;
    }

    public DataPoint getCurrentBatteryPower() {
        return currentBatteryPower;
    }

    public Status getCurrentStatus() {
        return currentStatus;
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
