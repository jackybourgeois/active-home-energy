package org.activehome.energy.battery;

import com.eclipsesource.json.JsonObject;
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.ShowIfErrorCallback;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Schedule;
import org.activehome.service.RequestHandler;
import org.activehome.service.Service;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Param;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class BatteryAutonomy extends Service implements RequestHandler {

    @Param(defaultValue = "Provide a set of estimation of a battery autonomy.")
    private String description;
    @Param(defaultValue = "/active-home-energy/tree/master/org.active-home.energy.battery")
    private String src;


    @Override
    protected RequestHandler getRequestHandler(Request request) {
        return this;
    }

    /**
     * Receive data from context
     * @param notifStr the Notif as string
     */
    @Input
    public final void getNotif(final String notifStr) {
        Notif notif = new Notif(JsonObject.readFrom(notifStr));
        if (notif.getDest().compareTo(getFullId()) == 0
                && notif.getContent() instanceof DataPoint) {
            DataPoint dp = (DataPoint) notif.getContent();
            logInfo("received " + dp);
        }
    }

}
