package org.activehome.energy.grid;

/*
 * #%L
 * Active Home :: Energy :: Grid
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
import org.activehome.com.ScheduledRequest;
import org.activehome.context.data.DataPoint;
import org.activehome.io.IO;
import org.activehome.tools.Convert;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;
import org.kevoree.log.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class UKGrid extends IO {

    @Param(defaultValue = "Provide real information (every 30 minutes) on UK grid status.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.grid/docs/grid.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.grid/docs/grid.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.grid/docs/demo.kevs")
    private String demoScript;

    @Param(defaultValue = "/activehome-energy/tree/master/org.activehome.energy.grid")
    private String src;

    private final static String URL_SRC_UK = "http://www.bmreports.com"
            + "/bsp/additional/saveoutput.php"
            + "?element=generationbyfueltypetablehistoric&output=CSV";

    @Param(defaultValue = "30mn")
    private String frequency;

    void checkData() {
        try {
            URL url = new URL(URL_SRC_UK);

            String line;
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("wwwcache.open.ac.uk", 80));
            try (InputStream inputStream = url.openConnection(proxy).getInputStream()) {
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                String prevLine = "";
                System.out.println("before scan uk file");
                while ((line = br.readLine()) != null && !line.contains("FTR")) {
                    prevLine = line;
                }
                System.out.println(prevLine);
                String[] splitted = prevLine.split(",");
                if (splitted.length >= 12) {

                    long ts = getCurrentTime();

                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.ccgt", ts, splitted[4])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.ocgt", ts, splitted[5])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.coal", ts, splitted[6])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.nuclear", ts, splitted[7])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.wind", ts, splitted[8])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.pump", ts, splitted[9])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.hydro", ts, splitted[10])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.oil", ts, splitted[11])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.other", ts, splitted[12])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.intfr", ts, splitted[13])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.intirl", ts, splitted[14])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.intned", ts, splitted[15])));
                    sendNotif(new Notif(getId(), getNode() + ".context", getCurrentTime(),
                            new DataPoint("*.grid.intew", ts, splitted[16])));
                }
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.error("Malformed URL Exception", e);
        } catch (IOException e) {
            e.printStackTrace();
            Log.error("IO Exception", e);
        }

        scheduleNextDataCheck();
    }

    @Override
    public final void fromAPI(final String msgStr) {

    }

    @Override
    public final void toExecute(final String reqStr) {
        //System.out.println("toExecute EMeter");
        JsonObject json = JsonObject.readFrom(reqStr);
        if (json.get("dest").asString().compareTo(getId()) == 0) {
            switch (json.get("method").asString()) {
                case "checkData":
                    checkData();
                    break;
                default:
            }
        }
    }

    void scheduleNextDataCheck() {
        ScheduledRequest sr = new ScheduledRequest(getId(), getId(), getCurrentTime(),
                "checkData", Convert.strDurationToMillisec(frequency));
        sendToTaskScheduler(sr);
    }
}
