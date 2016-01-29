package org.activehome.energy.sim.battery;

/*
 * #%L
 * Active Home :: Energy :: Sim :: Battery
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
import org.activehome.com.ScheduledRequest;
import org.activehome.com.Status;
import org.activehome.context.data.DataPoint;
import org.activehome.io.Storage;
import org.activehome.io.action.Command;
import org.activehome.tools.Convert;
import org.activehome.tools.Util;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.log.Log;

/**
 * Simulation of a Lead Acid Battery.
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class Battery extends Storage {

    @Param(defaultValue = "Simulate an electrical storage.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.sim.battery")
    private String src;

    /**
     * Frequency of computation/notification if no IO
     * in milliseconds.
     */
    @Param(defaultValue = "300000")
    private long updateFrequency;
    /**
     * Number of  battery cells.
     */
    @Param(defaultValue = "1")
    private int numCells;
    /**
     * How the battery cells are connected.
     */
    @Param(defaultValue = "SERIAL")
    private String batteryScheme;
    /**
     * The upper capacity in kWh for 1 cell.
     */
    @Param(defaultValue = "1.44")
    private double upperCapacity;
    /**
     * The lower capacity in kWh for 1 cell.
     */
    @Param(defaultValue = "0.43")
    private double lowerCapacity;
    /**
     * The maximum charging rate in watt for 1 cell.
     */
    @Param(defaultValue = "216")
    private double maxChargingRate;
    /**
     * The maximum discharging rate in watt for 1 cell.
     */
    @Param(defaultValue = "360")
    private double maxDischargingRate;
    /**
     * The charging efficiency rate (between 0 and 1).
     * Each time I charge, losses = kWh * (1 - chargingEffRate)
     */
    @Param(defaultValue = "0.84")
    private double chargingEffRate;
    /**
     * The discharging efficiency rate (between 0 and 1).
     * Each time I discharge, losses = kWh * (1 - dischargingEffRate)
     */
    @Param(defaultValue = "0.9")
    private double dischargingEffRate;

    /**
     * The battery status: CHARGING, DISCHARGING, IDLE, FULL, EMPTY.
     */
    private Status status;
    /**
     * The battery state of charge in kWh.
     */
    private double stateOfCharge;
    /**
     * The current power rate in watt.
     * <0 if discharging, >0 if charging
     */
    private double currentPowerRate;
    /**
     * Sum of energy losses.
     */
    private double sumLosses;
    /**
     * Time-stamp of the last update, used for energy computation.
     */
    private long lastUpdate;

    public Battery() {

    }

    /**
     * @param theNumOfCell          The number of cells
     * @param theBatteryScheme      How the battery are connected
     * @param theUpperCapacity      The upper capacity (full) in kWh for 1 cell
     * @param theLowerCapacity      The lower capacity (empty) in kWh for 1 cell
     * @param theMaxChargingRate    The maximum charging rate in watt for 1 cell
     * @param theMaxDischargingRate The maximum discharging rate in watt for 1 cell
     * @param theChargingEffRate    The charging efficiency rate (between 0 and 1)
     * @param theDischargingEffRate The discharging efficiency rate (between 0 and 1)
     */
    public Battery(final int theNumOfCell,
                   final BatteryScheme theBatteryScheme,
                   final double theUpperCapacity,
                   final double theLowerCapacity,
                   final double theMaxChargingRate,
                   final double theMaxDischargingRate,
                   final double theChargingEffRate,
                   final double theDischargingEffRate) {
        numCells = theNumOfCell;
        batteryScheme = theBatteryScheme.name();
        upperCapacity = theUpperCapacity;
        lowerCapacity = theLowerCapacity;
        maxChargingRate = theMaxChargingRate;
        maxDischargingRate = theMaxDischargingRate;
        chargingEffRate = theChargingEffRate;
        dischargingEffRate = theDischargingEffRate;
    }

    /**
     * On start, init attributes, set the battery as EMPTY.
     */
    @Start
    public final void start() {
        status = Status.EMPTY;
        stateOfCharge = lowerCapacity;
        currentPowerRate = 0;
        sumLosses = 0;
        lastUpdate = 0;
    }

    /**
     * @param reqStr The received Command Request as string
     */
    @Input
    public final void ctrl(final String reqStr) {
        JsonObject json = JsonObject.readFrom(reqStr);
        if (json.get("dest").asString().compareTo(getFullId()) == 0) {
            Request request = new Request(json);
            switch (Command.fromString(request.getMethod())) {
                case CHARGE:
                    if (request.getParams().length > 0) {
                        startCharging((Double) request.getParams()[0]);
                    } else {
                        startCharging(maxChargingRate);
                    }
                    break;
                case DISCHARGE:
                    if (request.getParams().length > 0) {
                        startDischarging((Double) request.getParams()[0]);
                    } else {
                        startDischarging(maxDischargingRate);
                    }
                    break;
                case PAUSE:
                    pause();
                    break;
                case STATUS:
                    notifyStatus();
                    notifySoC();
                    break;
                default:
            }
        }
    }

    /**
     * @param reqStr Received Request from the Task Scheduler as string
     */
    @Override
    public final void toExecute(final String reqStr) {
        JsonObject json = JsonObject.readFrom(reqStr);
        if (json.get("dest").asString().compareTo(getFullId()) == 0) {
            switch (json.get("method").asString()) {
                case "updateStateOfCharge":
                    updateStateOfCharge();
                    break;
                default:
                    ctrl(reqStr);
            }
        }
    }

    /**
     * Send a request to the task scheduler to schedule the next update.
     */
    private void scheduleNextUpdate() {
        ScheduledRequest sr = new ScheduledRequest(getFullId(), getFullId(),
                getCurrentTime(), "updateStateOfCharge",
                getCurrentTime() + updateFrequency);
        sendToTaskScheduler(sr);
    }

    /**
     * @param chargingRate The charging rate in Watt
     *                     (forced to maxChargingRate if above)
     */
    private void startCharging(final double chargingRate) {
        Log.debug(getFullId() + " start charging ");
        if (stateOfCharge < upperCapacity) {
            if (chargingRate >= 0 && chargingRate <= maxChargingRate) {
                setRate(chargingRate);
            } else {
                setRate(maxChargingRate);
            }
            lastUpdate = getCurrentTime();
            if (!status.equals(Status.CHARGING)) {
                setStatus(Status.CHARGING);
                notifyStatus();
                scheduleNextUpdate();
            }
        }
    }

    /**
     * @param dischargingRate The discharging rate in Watt
     *                        (forced to maxDischargingRate if above)
     */
    private void startDischarging(final double dischargingRate) {
        if (stateOfCharge > lowerCapacity) {
            if (dischargingRate >= 0 && dischargingRate <= maxDischargingRate) {
                setRate(dischargingRate);
            } else {
                setRate(maxDischargingRate);
            }
            notifyRate();
            lastUpdate = getCurrentTime();
            if (!status.equals(Status.DISCHARGING)) {
                setStatus(Status.DISCHARGING);
                notifyStatus();
                scheduleNextUpdate();
            }
        }
    }

    /**
     * Pause if charging or discharging.
     */
    private void pause() {
        if (status.equals(Status.CHARGING)
                || status.equals(Status.DISCHARGING)) {
            setStatus(Status.IDLE);
            notifyStatus();
            lastUpdate = getCurrentTime();
        }
    }

    /**
     * Compute the current state of charge
     * and schedule the next one.
     */
    private void updateStateOfCharge() {
        if (status.equals(Status.CHARGING)) {
            charge(currentPowerRate, getCurrentTime() - lastUpdate);
        } else if (status.equals(Status.DISCHARGING)) {
            discharge(currentPowerRate, getCurrentTime() - lastUpdate);
        }
        notifyRate();
        notifyStatus();
        notifySoC();
        lastUpdate = getCurrentTime();
        if (status.equals(Status.CHARGING)
                || status.equals(Status.DISCHARGING)) {
            scheduleNextUpdate();
        }
    }

    /**
     * Add energy stored since the last update to the state of charge
     * after removing losses.
     *
     * @param requestedRate The power rate over the period
     * @param duration      The period duration
     * @return The battery update summary
     */
    public final BatteryUpdateSummary charge(final double requestedRate,
                                             final long duration) {
        double prevSoC = stateOfCharge;
        double rate = requestedRate;
        if (rate > maxChargingRate) {
            rate = maxChargingRate;
        }
        double charged = Util.round5(Convert.watt2kWh(rate, duration));
        double chargedActual = Util.round5(chargingEffRate * charged);
        double losses;
        // if still space in the battery for available energy
        if (chargedActual + stateOfCharge <= upperCapacity) {
            stateOfCharge = Util.round5(stateOfCharge + chargedActual);
            if (stateOfCharge == upperCapacity) {
                setStatus(Status.FULL);
            }
            losses = charged - chargedActual;
        } else {
            // we are finishing to charge the battery
            chargedActual = upperCapacity - stateOfCharge;
            losses = chargedActual * (1 / chargingEffRate) - chargedActual;
            stateOfCharge = upperCapacity;
            // the battery is full, we deactivate the battery
            setStatus(Status.FULL);
            setRate(0);
        }
        losses = Util.round5(losses);
        sumLosses += losses;
        return new BatteryUpdateSummary(prevSoC, chargedActual, losses, status);
    }

    /**
     * Remove energy consumed since the last update from the state of charge
     * after adding sumLosses.
     *
     * @param requestedRate The power rate over the period
     * @param duration      The period duration
     * @return The battery update summary
     */
    public final BatteryUpdateSummary discharge(final double requestedRate,
                                                final long duration) {
        double prevSoC = stateOfCharge;
        double rate = requestedRate;
        if (rate > maxDischargingRate) {
            rate = maxDischargingRate;
        }
        double discharged = Util.round5(Convert.watt2kWh(rate, duration));
        double dischargedActual = Util.round5((1 / dischargingEffRate) * discharged);
        double losses;
        // if enough energy in the battery
        if (stateOfCharge - dischargedActual >= lowerCapacity) {
            stateOfCharge = Util.round5(stateOfCharge - dischargedActual);
            if (stateOfCharge == lowerCapacity) {
                setStatus(Status.EMPTY);
            }
            losses = dischargedActual - discharged;
        } else {
            // we are finishing to discharge the battery
            dischargedActual = stateOfCharge - lowerCapacity;
            losses = dischargedActual - dischargedActual * dischargingEffRate;
            stateOfCharge = lowerCapacity;
            // the battery is empty, we deactivate the battery
            setStatus(Status.EMPTY);
            setRate(0);
        }
        losses = Util.round5(losses);
        sumLosses += losses;
        return new BatteryUpdateSummary(prevSoC, dischargedActual, losses, status);
    }

    /**
     * @return The battery status (CHARGING, DISCHARGING, IDLE, EMPTY, FULL)
     */
    public final Status getStatus() {
        return status;
    }

    /**
     * @param theStatus Set the battery status
     */
    public final void setStatus(final Status theStatus) {
        status = theStatus;
    }

    /**
     * @param theRate Set the current rate
     */
    public final void setRate(final double theRate) {
        currentPowerRate = theRate;
    }

    /**
     * Disseminate the current battery status.
     */
    private void notifyStatus() {
        DataPoint statusDP = new DataPoint(getId() + ".status",
                getCurrentTime(), status.name());
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), statusDP));
    }

    /**
     * Disseminate the current rate.
     */
    private void notifyRate() {
        DataPoint rateDP = new DataPoint("power.cons.storage." + getId(),
                getCurrentTime(), currentPowerRate + "");
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), rateDP));
    }

    /**
     * Disseminate the current state of charge.
     */
    private void notifySoC() {
        DataPoint socDP = new DataPoint(getId() + ".soc",
                getCurrentTime(), stateOfCharge + "");
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), socDP));
    }

    /**
     * @param message Message received from APIs as string
     */
    @Override
    public void fromAPI(final String message) {

    }

    /**
     * On stop time, reinitialized attributes and set battery as EMPTY.
     */
    @Override
    public final void onStopTime() {
        status = Status.EMPTY;
        stateOfCharge = lowerCapacity;
        currentPowerRate = 0;
        sumLosses = 0;
        lastUpdate = 0;
    }

    public final double getStateOfCharge() {
        return stateOfCharge;
    }
}
