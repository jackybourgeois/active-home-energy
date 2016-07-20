package org.activehome.energy.solax;

/*
 * #%L
 * Active Home :: Energy :: Solax
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


import org.activehome.io.IO;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class SolaxInverter extends IO {

    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.solax")
    private String src;
    @Param(defaultValue = "9600")
    private int storageCapacity;
    @Param(defaultValue = "2500")
    private int maxDischargingRate;
    @Param(defaultValue = "2500")
    private int maxChargingRate;


    /**
     * Commands.
     */
    @Param(defaultValue = "")
    private String commands;
    /**
     * Label of the metric sent to the context.
     */
    @Param(defaultValue = "power.cons,"
            + "storage.availabilityKWh,"
            + "storage.availabilityPercent,"
            + "power.gen.<compId>1,"
            + "power.gen.<compId>2,"
            + "power.storage")
    private String metrics;


    public int getStorageCapacity() {
        return storageCapacity;
    }

    public int getMaxDischargingRate() {
        return maxDischargingRate;
    }

    public int getMaxChargingRate() {
        return maxChargingRate;
    }
}
