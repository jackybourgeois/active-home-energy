package org.activehome.energy.emulator.solax.test;


import com.eclipsesource.json.JsonObject;
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.ShowIfErrorCallback;
import org.activehome.context.data.DataPoint;
import org.activehome.test.ComponentTester;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Param;
import org.kevoree.api.ModelService;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class TesterSolaxInverterEmulator extends ComponentTester {

    /**
     * Access to the Kevoree model
     */
    @KevoreeInject
    private ModelService modelService;

    @Param(defaultValue = "solax")
    private String testedCompId;

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

        String[] metricArray = new String[]{"gridPower","feedInPower","surplusEnergy","powerDC1","powerDC2"};
        for (int i=0;i<metricArray.length;i++) {
            metricArray[i] = testedCompId + "." + metricArray[i];
        }
        Request subscriptionReq = new Request(getFullId(), getNode() + ".context",
                getCurrentTime(), "subscribe", new Object[]{metricArray, getFullId()});

        subscriptionReq.getEnviElem().put("userInfo", testUser());
        sendRequest(subscriptionReq, new ShowIfErrorCallback());
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

    /**
     * Listen to receive the energy.cons data points
     * and sum them. They will be compared to a sum
     * based on data extracted from the database.
     *
     * @param notifStr Notification from the context received as string
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0) {
            if (notif.getContent() instanceof DataPoint) {
                DataPoint dp = (DataPoint) notif.getContent();
                logInfo(dp.toString());
            }
        }
    }
}
