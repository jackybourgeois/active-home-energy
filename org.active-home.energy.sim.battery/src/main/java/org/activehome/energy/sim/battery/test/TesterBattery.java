package org.activehome.energy.sim.battery.test;

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
import org.activehome.com.*;
import org.activehome.context.data.DataPoint;
import org.activehome.io.action.Command;
import org.activehome.test.ComponentTester;
import org.activehome.context.data.UserInfo;
import org.kevoree.annotation.*;

/**
 * Mock component to test Battery component.
 * - Start charging (no params),
 * - when full charge detected schedule discharge (no params),
 * - when empty charge detected schedule charge (100 rate)
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class TesterBattery extends ComponentTester {

    @Param(defaultValue = "Mock component to test Battery component.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.sim.battery")
    private String src;

    /**
     * Port to control storage device
     */
    @Output
    private org.kevoree.api.Port toStorage;

    private boolean tested = false;

    /**
     * On start, subscribe to relevant metrics
     */
    @Start
    public final void start() {
        super.start();

        String[] metricArray = new String[]{"power.cons.storage.battery",
                "battery.status", "battery.soc"};
        Request subscriptionReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe", new Object[]{metricArray, getFullId()});

        UserInfo testUser = new UserInfo("tester", new String[]{"user"},
                "ah", "org.activehome.energy.emulator.energy.emulator.user.EUser");
        subscriptionReq.getEnviElem().put("userInfo", testUser);
        sendRequest(subscriptionReq, null);
    }

    /**
     * On start time, try to charge the battery
     */
    @Override
    public final void onStartTime() {
        toStorage.send(new Request(getFullId(), getNode() + ".battery",
                getCurrentTime(), Command.CHARGE.name()).toString(), null);
    }

    @Override
    protected String logHeaders() {
        return "";
    }

    @Override
    protected JsonObject prepareNextTest() {
        if (!tested) {
            JsonObject timeProp = new JsonObject();
            timeProp.set("startDate", startDate);
            timeProp.set("zip", 300);
            tested = true;
            return timeProp;
        }
        return null;
    }

    /**
     * @param notifStr Notification from the context received as string
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0
                && notif.getContent() instanceof DataPoint) {
            DataPoint dp = (DataPoint) notif.getContent();
            logInfo(dp.getMetricId() + " = " + dp.getValue());
            if (dp.getMetricId().contains("status")) {
                if (dp.getValue().equals(Status.FULL.name())) {
                    // if battery full, start discharging
                    Request dischargeReq = new Request(getFullId(), getNode() + ".battery",
                            getCurrentTime(), Command.DISCHARGE.name());
                    sendToTaskScheduler(new ScheduledRequest(dischargeReq,
                            getCurrentTime() + 300000), new ShowIfErrorCallback());
                } else if (dp.getValue().equals(Status.EMPTY.name())) {
                    // if battery empty, start charging
                    Request chargeReq = new Request(getFullId(), getNode() + ".battery",
                            getCurrentTime(), Command.CHARGE.name(),
                            new Object[]{100});
                    sendToTaskScheduler(new ScheduledRequest(chargeReq,
                            getCurrentTime() + 300000), new ShowIfErrorCallback());
                }
            }
        }
    }


}
