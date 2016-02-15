package org.activehome.energy.io.emulator.fiscalmeter;

/*
 * #%L
 * Active Home :: Energy :: IO :: Emulator
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


import org.activehome.com.Notif;
import org.activehome.context.data.DataPoint;
import org.activehome.tools.Convert;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@ComponentType
public class EDualRateFM extends EFiscalMeter {

    @Param(defaultValue = "Emulate a fiscal meter delivering a dual energy rate (such as Night/Day).")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.io.emulator")
    private String src;

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

    /**
     * the timestamp of the next switch of rate
     */
    private long nextUpdate;


    @Override
    public void onStartTime() {
        initExecutor();
        switchTariff();
    }

    @Override
    public void onResumeTime() {
        initExecutor();
        switchTariff();
    }

    public void switchTariff() {
        long actualSwitchTime = updateTariff();
        DataPoint dp = new DataPoint(metricId, actualSwitchTime, currentRate + "");
        sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(), dp));
        scheduleNextUpdate();
    }

    /**
     * update the tariff
     * @return the actual time of switch
     */
    public long updateTariff() {
        long midnight = getLocalTime() - (getLocalTime() % DAY);
        long changeTariffTime = Convert.strDurationToMillisec(switchTime);
        boolean isDay = (getLocalTime() - midnight) >= changeTariffTime;
        if (isDay) {
            currentRate = highRate;
            nextUpdate = midnight + DAY;
            return midnight + changeTariffTime - getTic().getTimezone()*HOUR;
        } else {
            currentRate = lowRate;
            nextUpdate = midnight + changeTariffTime;
            return midnight - getTic().getTimezone()*HOUR;
        }
    }

    public void scheduleNextUpdate() {
        nextUpdate = nextUpdate - getTic().getTimezone() * HOUR;
        long delay = (nextUpdate-getCurrentTime())/getTic().getZip();
        stpe.schedule(this::switchTariff, delay, TimeUnit.MILLISECONDS);
    }

}



