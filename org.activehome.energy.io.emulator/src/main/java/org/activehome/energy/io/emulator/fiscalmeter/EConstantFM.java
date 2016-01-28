package org.activehome.energy.io.emulator.fiscalmeter;

/*
 * #%L
 * Active Home :: Energy :: IO :: Emulator
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


import org.activehome.tools.Convert;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EConstantFM extends EFiscalMeter {

    @Param(defaultValue = "Emulate a fiscal meter delivering a constant energy rate.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.io.emulator/docs/eConstantFM.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.io.emulator/docs/eConstantFM.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.io.emulator/docs/demo.kevs")
    private String demoScript;

    @Param(defaultValue = "/activehome-energy/tree/master/org.activehome.energy.io.emulator")
    private String src;

    /**
     * The constant rate
     */
    @Param(defaultValue = "0.135")
    private double rate;
    /**
     * The frequency of publishing the rate
     */
    @Param(defaultValue = "12h")
    private String updateFrequency;

    // == == == Time life cycle == == ==

    @Override
    public final void onStartTime() {
        currentRate = rate;
        scheduleRate();
    }

    @Override
    public final void onResumeTime() {
        scheduleRate();
    }

    private void scheduleRate() {
        initExecutor();
        long delay = Convert.strDurationToMillisec(updateFrequency) / getTic().getZip();
        stpe.scheduleAtFixedRate(this::publishRate, 0, delay, TimeUnit.MILLISECONDS);
    }

}
