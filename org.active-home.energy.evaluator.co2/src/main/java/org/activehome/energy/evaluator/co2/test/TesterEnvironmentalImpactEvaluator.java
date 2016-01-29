package org.activehome.energy.evaluator.co2.test;

/*
 * #%L
 * Active Home :: Energy :: Evaluator :: CO2
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
import org.activehome.com.Request;
import org.activehome.com.RequestCallback;
import org.activehome.com.ShowIfErrorCallback;
import org.activehome.com.error.Error;
import org.activehome.context.data.Trigger;
import org.activehome.evaluator.EvaluationReport;
import org.activehome.test.ComponentTester;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class TesterEnvironmentalImpactEvaluator extends ComponentTester {

    @Param(defaultValue = "Mock up to test Environmental Impact Evaluator")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.evaluator.co2")
    private String src;

    private ScheduledThreadPoolExecutor stpe;
    private boolean testDone = false;

    /**
     * On init, subscribe to relevant metrics.
     */
    @Override
    public final void onInit() {
        super.onInit();
        startTS = getTic().getTS();
        stpe = new ScheduledThreadPoolExecutor(1);
    }

    @Override
    public final void onStartTime() {
        super.onStartTime();
        configureTriggers();
    }

    private void configureTriggers() {
        Trigger consTrigger = new Trigger("^power\\.cons\\.bg\\.baseload$",
                "sum(power.cons.*)", "power.cons");
        Trigger genTrigger = new Trigger("(^power\\.gen\\.)+(.*?)",
                "sum(power.gen.*)", "power.gen");
        Trigger balanceTrigger = new Trigger("(^power\\.cons$)|((^power\\.gen\\.)+(.*?))",
                "${power.cons,0}-sum(power.gen.*)", "power.balance");
        Trigger importTrigger = new Trigger("^power\\.balance$",
                "(${power.balance}>0)?${power.balance}:0", "power.import");
        Trigger exportTrigger = new Trigger("^power\\.balance$",
                "(${power.balance}<0)?(-1*${power.balance}):0", "power.export");

        Request triggerReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "addTriggers",
                new Object[]{new Trigger[]{consTrigger, genTrigger, balanceTrigger, importTrigger, exportTrigger}});
        sendRequest(triggerReq, new ShowIfErrorCallback());
    }

    /**
     * On stop time, request evaluation to CostEvaluator
     */
    @Override
    public final void onStopTime() {
        super.onStopTime();
        Request evalReq = new Request(getFullId(), getNode() + ".co2Eval", getCurrentTime(),
                "evaluate", new Object[]{startTS, startTS + getTestDuration()});
        sendRequest(evalReq, new RequestCallback() {
            @Override
            public void success(Object obj) {
                EvaluationReport report = (EvaluationReport) obj;
                for (String key : report.getReportedMetrics().keySet()) {
                    logInfo(key + ": " + report.getReportedMetrics().get(key));
                }
            }

            @Override
            public void error(Error error) {
                logError(error.toString());
            }
        });
    }

    @Override
    public final void onPauseTime() {
        super.onPauseTime();
        stpe.schedule(this::resumeTime, 30, TimeUnit.SECONDS);
    }

    @Override
    protected final String logHeaders() {
        return "";
    }

    @Override
    protected final JsonObject prepareNextTest() {
        if (!testDone) {
            testDone = true;
            JsonObject timeProp = new JsonObject();
            timeProp.set("startDate", startDate);
            timeProp.set("zip", 1800);
            return timeProp;
        }
        return null;
    }

    @Input
    public final void getNotif(final String notifStr) {

    }

}
