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
import org.activehome.service.RequestHandler;
import org.activehome.service.Service;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;

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
    /**
     * Mode: true=lifo, false=fifo
     */
    @Param(defaultValue = "false")
    private boolean lifoMode;

    private Autonomy autonomy;
    private Delivery delivery;
    private EnergyAge energyAge;

    private double currentSoCKWh = -1;
    private double currentSoCPercent = -1;
    private Status currentStatus = Status.UNKNOWN;

    private double solaxGen1 = 0;
    private double solaxGen2 = 0;

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        super.onInit();
        BatteryInfo batteryInfo = new BatteryInfo(maxChargingRate, maxDischargingRate, minSoC, maxSoC,
                capacityKWh, chargingEfficiency, dischargingEfficiency);
        listenAPI(getNode() + ".http", "/battery", true);
        subscribeToContext("storage.availabilityKWh", "storage.availabilityPercent",
                "power.cons", "power.storage", "storage.status", "power.gen.solax1", "power.gen.solax2");
        autonomy = new Autonomy(batteryInfo);
        delivery = new Delivery(batteryInfo);
        energyAge = new EnergyAge(latencyUnit, batteryInfo, lifoMode);
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
            long ts = dp.getTS();
            switch (dp.getMetricId()) {
                case "power.cons":
                    autonomy.setCurrentConsumption(Double.valueOf(dp.getValue()));
                    sendStorageNotif("storage.autonomy.currentCons", ts, autonomy.basedOnCurrentCons(ts));
                    break;
                case "storage.availabilityKWh":
                    currentSoCKWh = Double.valueOf(dp.getValue());
                    break;
                case "storage.availabilityPercent":
                    currentSoCPercent = Double.valueOf(dp.getValue());
                    autonomy.setCurrentSoCPercent(currentSoCPercent);
                    sendStorageNotif("storage.autonomy.currentCons", ts, autonomy.basedOnCurrentCons(ts));
                    delivery.setCurrentSoCPercent(currentSoCPercent);
                    sendStorageNotif("storage.delivery.currentGen", ts, delivery.basedOnCurrentGen(ts));
                    break;
                case "storage.status":
                    currentStatus = Status.valueOf(dp.getValue());
                    break;
                case "power.gen.solax1":
                    solaxGen1 = Double.valueOf(dp.getValue());
                    delivery.setGeneration(solaxGen1+solaxGen2);
                    sendStorageNotif("storage.delivery.currentGen", ts, delivery.basedOnCurrentGen(ts));
                    break;
                case "power.gen.solax2":
                    solaxGen2 = Double.valueOf(dp.getValue());
                    delivery.setGeneration(solaxGen1+solaxGen2);
                    sendStorageNotif("storage.delivery.currentGen", ts, delivery.basedOnCurrentGen(ts));
                    break;
                case "power.storage":
                    energyAge.setBatteryRate(dp);
                    energyAge.updateAge(dp);
                    sendStorageNotif("storage.age.current", ts, energyAge.averageCurrentAge(ts));
                    sendStorageNotif("storage.age.lastHour", ts, energyAge.averageLastHourAge(ts));
                    break;
                default:

            }
        }
    }


    private void sendStorageNotif(final String metric,
                                  final long ts,
                                  final double val) {
        sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(),
                new DataPoint(metric, ts, val + "")));
    }

    public double getCurrentSoCKWh() {
        return currentSoCKWh;
    }

    public double getCurrentSoCPercent() {
        return currentSoCPercent;
    }

    public Status getCurrentStatus() {
        return currentStatus;
    }

}