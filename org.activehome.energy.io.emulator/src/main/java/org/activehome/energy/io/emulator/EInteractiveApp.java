package org.activehome.energy.io.emulator;

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


import com.eclipsesource.json.JsonObject;
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.Status;
import org.activehome.context.data.DataPoint;
import org.activehome.io.InteractiveAppliance;
import org.activehome.io.action.Command;
import org.activehome.context.data.MetricRecord;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Stop;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class EInteractiveApp extends InteractiveAppliance {

    @Param(defaultValue = "Interactive device emulator.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.io.emulator/docs/eGrid.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.io.emulator/docs/eGrid.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.io.emulator/docs/EGrid.kevs")
    private String demoScript;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.io.emulator")
    private String src;

    /**
     * Commands.
     */
    @Param(defaultValue = "START,STOP,PAUSE,RESUME,STATUS,SET")
    private String commands;
    /**
     * Metrics
     */
    @Param(defaultValue = "power.cons.inter.<compId>,status.app.<compId>")
    private String metrics;
    /**
     * Details of the current load to play.
     */
    private MetricRecord currentLoad;
    /**
     * Where we are in current load.
     */
    private int index;
    /**
     * The current status of the appliance.
     */
    private Status status = Status.OFF;
    /**
     * The current power rate of the appliance.
     */
    private String currentPower = "0";
    /**
     * The delay accumulated when pausing the load.
     */
    private long extraDelay;
    /**
     * The time of the next.
     */
    private long nextValTS = -1;
    /**
     * When the current load actually started.
     * (-1 if not running or idled)
     */
    private long startTime = -1;
    /**
     * When the current load has been paused.
     * (-1 if not idled)
     */
    private long pauseTime = -1;
    /**
     * Scheduler playing the load values at the right time.
     */
    private ScheduledThreadPoolExecutor stpe;

    // == == == Component life cycle == == ==

    /**
     * Component stop, make sure we stop the current load.
     */
    @Stop
    public final void stop() {
        if (!status.equals(Status.OFF) && !status.equals(Status.DONE)) {
            stopLoad();
        }
    }

    // == == == Time life cycle == == ==

    /**
     * On init, initialise the scheduler.
     */
    @Override
    public final void onInit() {
        initExecutor();
    }

    /**
     * On pause, reset the scheduler.
     */
    @Override
    public final void onPauseTime() {
        stpe.shutdownNow();
        initExecutor();
    }

    /**
     * On resume, if a load was running reset it.
     */
    @Override
    public final void onResumeTime() {
        if (nextValTS != -1) {
            long execTime = (nextValTS - getCurrentTime()) / getTic().getZip();
            stpe.schedule(this::playNextValue, execTime, TimeUnit.MILLISECONDS);
        }
    }

    // == == == Load life cycle == == ==

    /**
     * The appliance start playing a load.
     */
    public final void startLoad() {
        if (status.equals(Status.READY)) {
            logInfo("Start");
            extraDelay = 0;
            startTime = getCurrentTime();
            index = 0;
            updateStatus(Status.RUNNING);
            playNextValue();
        }
    }

    /**
     * The running load is paused.
     */
    public final void pauseLoad() {
        if (status.equals(Status.RUNNING)) {
            pauseTime = getCurrentTime();
            stpe.shutdownNow();
            while (startTime + extraDelay +
                    currentLoad.getRecords().get(index).getTS() < pauseTime) {
                playNextValue();
            }
            updateCurrentPower(pauseTime, "0");
            updateStatus(Status.IDLE);
            logInfo("Pause");
        }
    }

    /**
     * The running load is resumed.
     */
    public final void resumeLoad() {
        if (status.equals(Status.IDLE)) {
            long resumeTime = getCurrentTime();
            logInfo("Resume");
            extraDelay += (resumeTime - pauseTime);
            initExecutor();
            updateStatus(Status.RUNNING);
            updateCurrentPower(resumeTime,
                    currentLoad.getRecords().get(index - 1).getValue());
            long execTime = (nextValTS - pauseTime) / getTic().getZip();
            stpe.schedule(this::playNextValue, execTime, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop the running load.
     */
    public final void stopLoad() {
        logInfo("Stop");
        // send 0 as new electric load
        updateCurrentPower(getCurrentTime(), "0");
        // reset data point progress
        index = 0;
        currentLoad = null;
        updateStatus(Status.STOPPED);
    }

    /**
     * Send the next value of the running load.
     */
    private void playNextValue() {
        if (index < currentLoad.getRecords().size()) {
            String power = currentLoad.getRecords().get(index).getValue();
            long ts = startTime + extraDelay
                    + currentLoad.getRecords().get(index).getTS();
            updateCurrentPower(ts, power);
        }
        index++;
        scheduleNextValue();
    }

    /**
     * Schedule the next value to play.
     */
    private void scheduleNextValue() {
        if (!stpe.isShutdown()) {
            if (index < currentLoad.getRecords().size()) {
                long delay = currentLoad.getRecords().get(index).getTS();
                if (index - 1 >= 0) {
                    long prevTS = currentLoad.getRecords().get(index - 1).getTS();
                    delay -= prevTS;
                }
                nextValTS = startTime + currentLoad.getRecords().get(index).getTS();

                stpe.schedule(this::playNextValue,
                        delay / getTic().getZip(),
                        TimeUnit.MILLISECONDS);
            } else {
                nextValTS = -1;
                updateStatus(Status.DONE);
            }
        }
    }

    /**
     * Push notif with current status.
     */
    public final void publishStatus() {
        DataPoint dp = new DataPoint("status.app." + getId(),
                getCurrentTime(), status.name());
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), dp));
    }


    /**
     * Update and publish the current power of the appliance.
     * @param ts the timestamp of the update (millisec)
     * @param power the new power rate (Watt)
     */
    private void updateCurrentPower(final long ts,
                                    final String power) {
        currentPower = power;
        DataPoint dp = new DataPoint("power.cons.inter." + getId(),
                ts, power);
        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), dp));
    }

    /**
     * @param newStatus The new status
     */
    private void updateStatus(final Status newStatus) {
        status = newStatus;
        publishStatus();
    }

    private void getReady(MetricRecord mr) {
        if (!status.equals(Status.RUNNING)) {
            currentLoad = mr;
            updateStatus(Status.READY);
        }
    }

    /**
     * Translate external command into local method.
     * @param reqStr The received command request as string
     */
    @Input
    public final void ctrl(final String reqStr) {
        Thread.currentThread().setContextClassLoader(
                this.getClass().getClassLoader());
        JsonObject json = JsonObject.readFrom(reqStr);
        if (json.get("dest").asString().compareTo(getFullId()) == 0) {
            Request request = new Request(json);
            switch (Command.fromString(request.getMethod())) {
                case START:
                    startLoad();
                    break;
                case STOP:
                    stopLoad();
                    break;
                case PAUSE:
                    pauseLoad();
                    break;
                case RESUME:
                    resumeLoad();
                    break;
                case STATUS:
                    publishStatus();
                    break;
                case SET:
                    if (request.getParams().length > 0) {
                        getReady((MetricRecord) request.getParams()[0]);
                    }
                default:
            }
        }
    }

    private  void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-emulator-interactive-pool");
        });
    }


}



