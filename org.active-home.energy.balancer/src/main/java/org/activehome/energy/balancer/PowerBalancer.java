package org.activehome.energy.balancer;

/*
 * #%L
 * Active Home :: Energy :: Power Balancer
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
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.Status;
import org.activehome.context.data.*;
import org.activehome.context.helper.ModelHelper;
import org.activehome.energy.balancer.data.ApplianceContext;
import org.activehome.energy.balancer.data.BalancerAction;
import org.activehome.energy.balancer.data.OptimisationPlan;
import org.activehome.io.action.Command;
import org.activehome.service.RequestHandler;
import org.activehome.service.Service;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Output;
import org.kevoree.annotation.Param;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class PowerBalancer extends Service implements RequestHandler {


    private static final long DEFAULT_PAUSE_TIME = 900000;
    @Param(defaultValue = "The Balancer is a reactive component which tends to optimize the\n" +
            " * overall consumption of a household at real time towards an objective.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.balancer")
    private String src;
    /**
     * The balancer react if the current balance goes outside +-100kW.
     */
    @Param(defaultValue = "100")
    private int staticTolerance;
    /**
     * The necessary bindings.
     */
    @Param(defaultValue = "pushRequest>Scheduler.getRequest,"
            + "getResponse>Scheduler.pushResponse,"
            + "pushRequest>Context.getRequest,"
            + "getNotif>Context.pushDataToSystem,"
            + "pushNotif>Context.getNotif,"
            + "pushCmd>BackgroundAppliance.ctrl,"
            + "pushCmd>InteractiveAppliance.ctrl")
    private String bindingPowerBalancer;

    /**
     * Port to send command to appliances.
     */
    @Output
    private org.kevoree.api.Port pushCmd;
    /**
     * The current balance: overall consumption - generation.
     */
    private double currentBalance;
    /**
     * The previous balance.
     */
    private double previousBalance;
    /**
     * The record of what is planned, what we expect.
     */
    private MetricRecord plannedBalance;
    /**
     * The map of devices considered for the balancing.
     */
    private HashMap<String, ApplianceContext> deviceMap;
    /**
     * the schedule of the coming events (plan)
     */
    private Schedule planSchedule;

    /**
     * @param request The request to handle
     * @return The current balancer
     */
    @Override
    protected final RequestHandler getRequestHandler(final Request request) {
        return this;
    }

    @Override
    public final void onInit() {
        currentBalance = 0;
        deviceMap = new HashMap<>();
        updateSource();
    }

    @Override
    public final void onStartTime() {
        subscribeToContext("power.balance", "schedule.plan.power", "event.plan.power",
                "power.cons.bg.*", "power.cons.inter.*", "status.app.*");
    }

    /**
     * @param notifStr The received notification from the context as string
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0) {
            if (notif.getContent() instanceof DataPoint) {
                DataPoint dp = (DataPoint) notif.getContent();
                if (dp.getMetricId().equals("power.balance")) {
                    previousBalance = currentBalance;
                    currentBalance = Double.valueOf(dp.getValue());
                    applyOptimisationPlan(buildOptimisationPlan());
                } else if (dp.getMetricId().startsWith("status.app.")) {
                    String deviceId = dp.getMetricId().replace("status.app.", "");
                    updateDeviceStatus(deviceId, Status.valueOf(dp.getValue()), dp.getTS());
                } else if (dp.getMetricId().startsWith("power.cons.")
                        && !dp.getMetricId().equals("power.cons.bg.baseload")) {
                    String deviceId = dp.getMetricId()
                            .substring(dp.getMetricId().lastIndexOf(".") + 1);
                    updateDeviceCurrentPower(deviceId,
                            Double.valueOf(dp.getValue()), dp.getTS());
                }
            } else if (notif.getContent() instanceof Schedule) {
                Schedule newSchedule = (Schedule) notif.getContent();
                if (newSchedule.getName().equals("schedule.plan.power")) {
                    updatePlanSchedule(newSchedule);
                }
            } else if (notif.getContent() instanceof Event) {
                updateEvent((Event) notif.getContent());
            }
        }
    }

    /**
     * @param deviceId The device Id
     * @param status   The new status
     * @param ts       The time-stamp of the update
     */
    public final void updateDeviceStatus(final String deviceId,
                                         final Status status,
                                         final long ts) {
        if (deviceMap.containsKey(deviceId)) {
            deviceMap.get(deviceId).setStatus(status, ts);
        }
    }

    /**
     * @param deviceId     The device Id
     * @param currentPower The new current power
     * @param ts           Yhe time-stamp of the update
     */
    public final void updateDeviceCurrentPower(final String deviceId,
                                               final double currentPower,
                                               final long ts) {
        if (deviceMap.containsKey(deviceId)) {
            deviceMap.get(deviceId).setCurrentPower(currentPower, ts);
        }
    }

    /**
     * Tend to balance = 0.
     *
     * @return optimisation plan
     */
    public final OptimisationPlan buildOptimisationPlan() {
        LinkedList<BalancerAction> appActionList = new LinkedList<>();
        double sumAction = 0;

        // collect all ON/Running, interruptable device, with none or expired action
        List<ApplianceContext> appList = deviceMap.values().stream()
                .filter(app -> app.getDevice().isInterruptable()
                        && (app.getStatus().equals(Status.ON) || app.getStatus().equals(Status.RUNNING))
                        && (app.getLastActionApplied() == null || app.getLastActionApplied().getExpireDate() < getCurrentTime()))
                .collect(Collectors.toList());

        int i = 0;
        while (sumAction < currentBalance && i < appList.size()) {
            ApplianceContext app = appList.get(i);
            if (sumAction + app.getCurrentPower() <= currentBalance) {
                HashMap<String, Double> impact = new HashMap<>();
                impact.put("power", -1 * app.getCurrentPower());
                BalancerAction action = new BalancerAction(app, Command.STOP, Command.START,
                        impact, DEFAULT_PAUSE_TIME);
                sumAction += app.getCurrentPower();
                appActionList.add(action);
            }
            i++;
        }
        return new OptimisationPlan(currentBalance,
                currentBalance - sumAction, appActionList);
    }

    /**
     * @param plan The optimised plan.
     */
    private void applyOptimisationPlan(final OptimisationPlan plan) {
        plan.getActionToTake().forEach(this::applyAction);
    }

    /**
     * Transform the action into control request
     * sent to the relevant appliance.
     *
     * @param action The potential action to apply
     */
    private void applyAction(final BalancerAction action) {
        Request request = new Request(getId(),
                getNode() + "." + action.getApplianceContext().getDevice().getID(),
                getCurrentTime(), action.getStartCmd().name());
        if (pushCmd != null && pushCmd.getConnectedBindingsSize() > 0) {
            pushCmd.send(request.toString(), null);
        }
        action.setExpireDate(getCurrentTime() + action.getDuration());
        action.getApplianceContext().setLastActionApplied(action);
    }

    public final double getCurrentPlannedBalance() {
        while (plannedBalance.getRecords().size() > 1) {
            long nextTS = plannedBalance.getRecords().get(1).getTS();
            if (plannedBalance.getStartTime() + nextTS < getCurrentTime()) {
                plannedBalance.pollFirst();
            } else {
                return plannedBalance.getRecords().get(0).getDouble();
            }
        }
        return currentBalance;
    }

    /**
     * Look at the Kevoree model to find running appliances.
     */
    private void updateSource() {
        HashMap<String, Device> foundDeviceMap = ModelHelper.findAllRunningDevice("Appliance",
                new String[]{context.getNodeName()}, getModelService());

        foundDeviceMap.values().stream()
                .filter(device -> !deviceMap.containsKey(device.getID()))
                .forEach(device -> deviceMap.put(device.getID(),
                        new ApplianceContext(device)));
    }

    private void updatePlanSchedule(final Schedule schedule) {
        planSchedule = schedule;
    }

    private void updateEvent(final Event newEvent) {
        if (planSchedule != null) {
            planSchedule.getEpisode().getEvents().remove(
                    planSchedule.getEpisode().findEventById(newEvent.getId()));
            planSchedule.getEpisode().getEvents().add(newEvent);
        }
    }

    /**
     * @param theDeviceMap The map of device available for balancing
     */
    public final void setDeviceMap(final HashMap<String, ApplianceContext> theDeviceMap) {
        deviceMap = theDeviceMap;
    }

}
