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

@ComponentType
public class EDualRateFM extends EFiscalMeter {

    /**
     * The high rate
     */
    @Param(defaultValue = "0.15771")
    private double highRate;
    /**
     * The low rate
     */
    @Param(defaultValue = "0.06615")
    private double lowRate;
    /**
     * The time to switch the rate
     */
    @Param(defaultValue = "7h")
    private String switchTime;


    @Override
    public void onStartTime() {
        initExecutor();
        updateTariff();
    }

    @Override
    public void onResumeTime() {
        initExecutor();
        updateTariff();
    }

    public void updateTariff() {
        long midnight = getLocalTime() - (getLocalTime() % DAY);
        long changeTariffTime = Convert.strDurationToMillisec(switchTime);
        long nextUpdate;
        boolean isDay = (getLocalTime() - midnight) >= changeTariffTime;
        if (isDay) {
            currentRate = highRate;
            nextUpdate = midnight + DAY;
        } else {
            currentRate = lowRate;
            nextUpdate = midnight + changeTariffTime;
        }
        publishRate();
        nextUpdate = nextUpdate - getTic().getTimezone() * HOUR;
        long delay = (nextUpdate-getCurrentTime())/getTic().getZip();
        stpe.schedule(this::updateTariff, delay, TimeUnit.MILLISECONDS);
    }

}



