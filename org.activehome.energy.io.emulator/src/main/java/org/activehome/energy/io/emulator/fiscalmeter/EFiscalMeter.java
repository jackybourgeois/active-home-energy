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


import org.activehome.com.Notif;
import org.activehome.context.data.DataPoint;
import org.activehome.io.IO;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Emulate a energy tariff rate from an energy provider
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public abstract class EFiscalMeter extends IO {

    /**
     * label of the metric attached to the published value
     */
    @Param(optional = false)
    protected String metricId;

    protected ScheduledThreadPoolExecutor stpe;
    protected double currentRate;

    @Override
    public void forceNotif() {
        publishRate();
    }

    protected void publishRate() {
        DataPoint dp = new DataPoint(metricId, getCurrentTime(), currentRate + "");
        sendNotif(new Notif(getFullId(), getNode() + ".context", getCurrentTime(), dp));
    }

    @Override
    public final void onPauseTime() {
        stpe.shutdownNow();
    }

    @Override
    public void onStopTime() {
        stpe.shutdown();
    }


    protected void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-fiscalmeter-pool");
        });
    }


}
