package org.activehome.energy.planner;

/*
 * #%L
 * Active Home :: Energy :: Planner
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
import org.activehome.com.RequestCallback;
import org.activehome.com.ShowIfErrorCallback;
import org.activehome.com.error.Error;
import org.activehome.context.data.Episode;
import org.activehome.context.data.Event;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.Device;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.data.Record;
import org.activehome.context.data.SampledRecord;
import org.activehome.context.helper.ModelHelper;
import org.activehome.energy.library.EnergyHelper;
import org.activehome.service.RequestHandler;
import org.activehome.service.Service;
import org.activehome.user.UserSuggestion;
import org.activehome.user.UserSuggestionResponse;
import org.kevoree.annotation.*;
import org.kevoree.ContainerRoot;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.UUIDModel;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.log.Log;
import org.kevoree.pmodeling.api.ModelCloner;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class Planner extends Service implements RequestHandler {

    @Param(defaultValue = "Define the expected plan in collaboration with the user.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.planner/docs/planner.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.planner/docs/grid.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.planner/docs/demo.kevs")
    private String demoScript;

    @Param(defaultValue = "getNotif>Context.pushNotif,"
            + "getNotif>Context.pushDataToSystem,"
            + "pushUserSuggestion>User.getNotif,"
            + "getNotif>User.pushNotif")
    private String bindingPlanner;


    /**
     * To get a local copy of Kevoree model.
     */
    private ModelCloner cloner;

    private UserSuggestion lastUserSuggestion;
    private Schedule currentPlan;

    /**
     * Port to send user suggestion to users.
     */
    @Output
    private org.kevoree.api.Port pushUserSuggestion;

    @Override
    protected RequestHandler getRequestHandler(Request request) {
        return this;
    }

    @Start
    public void start() {
        super.start();
        lastUserSuggestion = null;
        currentPlan = null;
        KevoreeFactory kevFactory = new DefaultKevoreeFactory();
        cloner = kevFactory.createModelCloner();
    }

    @Override
    public final void onInit() {
        Request request = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe",
                new Object[]{new String[]{"schedule.prediction.power", "schedule.alternative.power"}, getFullId()});
        sendRequest(request, new ShowIfErrorCallback());
    }

    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0) {
            if (notif.getContent() instanceof Schedule) {
                Schedule schedule = (Schedule) notif.getContent();
                switch (schedule.getName()) {
                    case "schedule.alternative.power":
                        manageAlternativeSchedule(schedule);
                        break;
                    case "schedule.prediction.power":
                        updatePlan(schedule);
                        break;
                    default:
                        break;
                }
            } else if (notif.getContent() instanceof UserSuggestionResponse) {
                UserSuggestionResponse usr = (UserSuggestionResponse) notif.getContent();
                logInfo("receive usr with suggestion id: " + usr.getSuggestionId()
                        + " and event " + usr.getEvent().getId());
                updatePlan(usr.getEvent());
            }
        }
    }

    private void updatePlan(final Schedule schedule) {
        Schedule planSchedule = new Schedule("schedule.plan.power", schedule.getStart(),
                schedule.getHorizon(), schedule.getGranularity());
        for (String metricId : schedule.getMetricRecordMap().keySet()) {
            MetricRecord load = schedule.getMetricRecordMap().get(metricId);
            MetricRecord loadPlan = new MetricRecord(metricId, load.getTimeFrame());
            loadPlan.setMainVersion("plan");
            for (Record record : load.getRecords()) {
                if (record instanceof SampledRecord) {
                    loadPlan.addRecord(load.getStartTime() + record.getTS(),
                            ((SampledRecord) record).getDuration(),
                            record.getValue(), "plan", record.getConfidence());
                } else {
                    loadPlan.addRecord(load.getStartTime() + record.getTS(),
                            record.getValue(), "plan", record.getConfidence());
                }
            }
            planSchedule.getMetricRecordMap().put(metricId, loadPlan);
        }

        planSchedule.setEpisode(schedule.getEpisode());

        currentPlan = planSchedule;

        sendNotif(new Notif(getFullId(), getNode() + ".context",
                getCurrentTime(), planSchedule));
    }

    private void updatePlan(final Event event) {
        Event eventInPlan = lastUserSuggestion.getEpisode().findEventById(event.getId());
        if (eventInPlan != null && currentPlan != null) {
            // get the current plan for the metric for the entire horizon
            MetricRecord fullMetricMR = currentPlan.getMetricRecordMap()
                    .get(eventInPlan.getMetricRecordMap().get("power").getMetricId());

            // erase origin load
            fullMetricMR.mergeRecords(eventInPlan.getMetricRecordMap().get("power"),
                    eventInPlan.getMetricRecordMap().get("power").getMainVersion(),
                    fullMetricMR.getMainVersion(), false);

            // add new load
            fullMetricMR.mergeRecords(event.getMetricRecordMap().get("power"),
                    event.getMetricRecordMap().get("power").getMainVersion(),
                    fullMetricMR.getMainVersion(), true);

            Notif notifUpdatePlan =  new Notif(getFullId(), getNode() + ".context",
                    getCurrentTime(), fullMetricMR);
            sendNotif(notifUpdatePlan);
        } else {
            Log.error("update plan: event referenced to suggestion but not in the current plan.");
        }
    }

    private void manageAlternativeSchedule(final Schedule schedule) {
        lastUserSuggestion = new UserSuggestion(schedule.getEpisode());
        sendUserSuggestion(lastUserSuggestion);
    }

    private void sendUserSuggestion(UserSuggestion suggestion) {
        if (pushUserSuggestion != null && pushUserSuggestion.getConnectedBindingsSize() > 0) {
            pushUserSuggestion.send(new Notif(getFullId(), "*",
                    getCurrentTime(), suggestion).toString(), null);
        }
    }

    /**
     * Look at the Kevoree model to find InteractiveAppliance
     * which are negotiables and update the negotiableDeviceMap.
     */
    private HashMap<String, Device> negotiableDeviceMap() {
        UUIDModel model = getModelService().getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());
        HashMap<String, Device> deviceMap = ModelHelper.findAllRunningDevice("InteractiveAppliance",
                new String[]{context.getNodeName()}, localModel);
        HashMap<String, Device> negotiableDeviceMap = new HashMap<>();
        deviceMap.keySet().stream()
                .filter(deviceId -> deviceMap.get(deviceId).isNegotiable())
                .forEach(deviceId -> {
                    negotiableDeviceMap.put(deviceId, deviceMap.get(deviceId));
                });
        return negotiableDeviceMap;
    }
}
