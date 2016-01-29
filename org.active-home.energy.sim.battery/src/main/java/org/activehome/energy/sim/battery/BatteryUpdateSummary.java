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


import org.activehome.com.Status;

/**
 * The summary of a battery update (charge or discharge).
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class BatteryUpdateSummary {

    /**
     * State of charge before update
     */
    private double prevSoc;
    /**
     * the charge (positive) or discharge (negative) in kWh
     */
    private double update;
    /**
     * the losses through the update in kWh
     */
    private double losses;
    /**
     * the new battery status
     */
    private Status status;


    public BatteryUpdateSummary(final double thePrevSoC,
                                final double theUpdate,
                                final double theLosses,
                                final Status theStatus) {
        prevSoc = thePrevSoC;
        update = theUpdate;
        losses = theLosses;
        status = theStatus;
    }

    public double getPrevSoc() {
        return prevSoc;
    }

    public double getUpdate() {
        return update;
    }

    public double getLosses() {
        return losses;
    }

    public Status getStatus() {
        return status;
    }
}
