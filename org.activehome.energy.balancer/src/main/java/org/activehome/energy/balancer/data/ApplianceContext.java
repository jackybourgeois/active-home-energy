package org.activehome.energy.balancer.data;

/*
 * #%L
 * Active Home :: Energy :: Balancer
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


import org.activehome.com.Status;
import org.activehome.context.data.Device;

/**
 * The current status, consumption, potential and
 * properties of an appliance.
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */

public class ApplianceContext {

    private Device device;

    private double currentPower;
    private Status status;
    /**
     * The timestamp of the last update received,
     * either current power or status
     */
    private long lastUpdate;
    /**
     * The last action sent to this appliance
     */
    private BalancerAction lastActionApplied = null;

    public ApplianceContext(final Device theDevice) {
        device = theDevice;
        status = Status.UNKNOWN;
        currentPower = 0;
        lastUpdate = 0;
    }

    public final Device getDevice() {
        return device;
    }

    public final double getCurrentPower() {
        return currentPower;
    }

    public final void setCurrentPower(final double theCurrentPower, final long ts) {
        currentPower = theCurrentPower;
        lastUpdate = ts;
    }

    public final Status getStatus() {
        return status;
    }

    public final void setStatus(final Status theStatus, final long ts) {
        status = theStatus;
        lastUpdate = ts;
    }

    public final long getLastUpdate() {
        return lastUpdate;
    }

    public final BalancerAction getLastActionApplied() {
        return lastActionApplied;
    }

    public final void setLastActionApplied(BalancerAction action) {
        lastActionApplied = action;
    }
}
