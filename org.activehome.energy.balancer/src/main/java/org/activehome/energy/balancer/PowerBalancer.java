package org.activehome.energy.balancer;

/*
 * #%L
 * Active Home :: Energy :: Balancer
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
import org.activehome.com.ShowIfErrorCallback;
import org.activehome.com.Status;
import org.activehome.context.data.Event;
import org.activehome.context.data.Schedule;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Device;
import org.activehome.context.data.MetricRecord;
import org.activehome.context.helper.ModelHelper;
import org.activehome.energy.balancer.data.ApplianceContext;
import org.activehome.energy.balancer.data.BalancerAction;
import org.activehome.energy.balancer.data.OptimisationPlan;
import org.activehome.io.action.Command;
import org.activehome.service.Service;
import org.activehome.service.RequestHandler;
import org.kevoree.ContainerRoot;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Output;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.UUIDModel;
import org.kevoree.factory.DefaultKevoreeFactory;
import org.kevoree.factory.KevoreeFactory;
import org.kevoree.pmodeling.api.ModelCloner;

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


    @Param(defaultValue = "The Balancer is a reactive component which tends to optimize the\n" +
            " * overall consumption of a household at real time towards an objective.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.balancer/docs/load_balancer_scheme.svg")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.balancer/docs/powerBalancer.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.balancer/docs/demo.kevs")
    private String demoScript;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.balancer")
    private String src;

    private static final long DEFAULT_PAUSE_TIME = 900000;

    /**
     * The Kevoree model.
     */
    @KevoreeInject
    private ModelService modelService;
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
            + "getStatus>EBackgroundApp.pushStatus,"
            + "getStatus>EInteractiveApp.pushStatus,"
            + "getStatus>EUncontrolledApp.pushStatus,"
            + "toBackground>EBackgroundApp.ctrl,"
            + "toInteractive>EInteractiveApp.ctrl,"
            + "toStorage>EInteractiveApp.ctrl")
    private String binding;

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
     * The Kevoree model cloner.
     */
    private ModelCloner cloner;
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

    /**
     *
     */
    @Start
    public final void start() {
        super.start();
        KevoreeFactory kevFactory = new DefaultKevoreeFactory();
        cloner = kevFactory.createModelCloner();
    }

    /**
     *
     */
    @Override
    public final void onInit() {
        currentBalance = 0;
        deviceMap = new HashMap<>();
        updateSource();
        Request request = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe",
                new Object[]{new String[]{"power.balance", "schedule.plan.power", "event.plan.power",
                        "power.cons.bg.*", "power.cons.inter.*", "status.app.*"}, getFullId()});
        sendRequest(request, new ShowIfErrorCallback());
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
                    logInfo(dp.getMetricId() + ": " + dp.getValue());
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
     * @param ts       Yhe time-stamp of the update
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

        /**
         * if the balance drop, check action applied not expired
         * which reduced the overall consumption and restart them.
         */
//        if (currentBalance < previousBalance - 200) {
//            appActionList.addAll(deviceMap.values().stream()
//                    .filter(app -> app.getDevice().isInterruptable()
//                            && app.getLastActionApplied() != null
//                            && app.getLastActionApplied().getExpireDate() > getCurrentTime()
//                            && app.getLastActionApplied().getImpact() < 0)
//                    .map(app -> new Action(app, Command.START, null,
//                                -1 * app.getLastActionApplied().getImpact(), 0))
//                    .collect(Collectors.toList()));
//        }

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
//        if (plan.getActionToTake().size()>0) {
//            logInfo("########## optimisation benefits: " + (plan.getCurrentBalance() - plan.getOptimisedBalance()));
//            logInfo("nb action: " + plan.getActionToTake().size());
//        }
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
        UUIDModel model = modelService.getCurrentModel();
        ContainerRoot localModel = cloner.clone(model.getModel());

        HashMap<String, Device> foundDeviceMap = ModelHelper.findAllRunningDevice("Appliance",
                new String[]{context.getNodeName()}, localModel);

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


    //    /**
//     * @param objective
//     * @param currentSolution
//     */
//    public final void checkPotentialBackgroundAction(final double objective,
//                                                     final double currentSolution) {
//
//    }
//
//    /**
//     * @param device The interactive device to check
//     * @param status The current status of the device
//     */
//    public final void checkPotentialInterAction(final Device device,
//                                                final Status status) {
//        if (status.equals(Status.RUNNING)) {
//            //potentialActions.add(new PotentialAction(device, Command.PAUSE, -500));
//        } else if (status.equals(Status.DONE)) {
//            //removePotentialAction(device);
//        }
//    }
//
//    /**
//     * @param device The storage device to check
//     * @param status The current status of the device
//     */
//    public final void checkPotentialStorageAction(final Device device,
//                                                  final Status status) {
//
//    }

//    /**
//     *
//     */
//    private void checkBalance() {
//        if (currentBalance > staticTolerance) {
//            lookForReducingConsumption(currentBalance);
//        } else if (currentBalance < staticTolerance * (-1)) {
//            lookForIncreasingConsumption(currentBalance);
//        }
//
//        if (plannedBalance != null) {
//            if (staticTolerance < (Math.abs(Math.abs(currentBalance)
//                    - Math.abs(getCurrentPlannedBalance())))) {
//                // outside the plan, request new plan
//                Request request = new Request(getFullId(), getNode() + ".scheduler",
//                        getCurrentTime(), "generateNewSchedule");
//                sendRequest(request, new RequestCallback() {
//                    public void success(final Object result) {
//                    }
//
//                    public void error(final Error result) {
//                    }
//                });
//            }
//        }
//    }


//    /**
//     * Looks for potential actions allowing to reduce
//     * the overall consumption.
//     *
//     * @param extra The power rate in extra
//     */
//    public final void lookForReducingConsumption(final double extra) {
//        double sumImpact = 0;
//        if (extra > 0) {
//            LinkedList<Action> toRemove = new LinkedList<>();
//            for (Action action : potentialActions) {
//                if (action.isOutDated()) {
//                    toRemove.add(action);
//                } else if (action.getImpact() < 0) {
//                    applyAction(action);
//                    sumImpact += action.getImpact();
//                    toRemove.add(action);
//                    if ((sumImpact * -1) >= extra) {
//                        break;
//                    }
//                }
//            }
//            potentialActions.removeAll(toRemove);
//        }
//    }
//
//    /**
//     * Looks for potential actions allowing to increase
//     * the overall consumption.
//     *
//     * @param spare The power rate available
//     */
//    public final void lookForIncreasingConsumption(final double spare) {
//        double sumImpact = 0;
//        if (spare < 0) {
//            LinkedList<Action> toRemove = new LinkedList<>();
//            for (Action action : potentialActions) {
//                if (action.getImpact() > 0) {
//                    applyAction(action);
//                    toRemove.add(action);
//                    sumImpact += action.getImpact();
//                    if ((sumImpact * -1) >= spare) {
//                        break;
//                    }
//                }
//            }
//            toRemove.forEach(potentialActions::remove);
//        }
//    }
}
