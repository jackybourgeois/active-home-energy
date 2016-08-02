package org.activehome.energy.battery;

/*
 * #%L
 * Active Home :: Energy :: Battery
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
import com.eclipsesource.json.JsonValue;
import org.activehome.com.Request;
import org.activehome.service.RequestHandler;
import org.activehome.tools.file.FileHelper;
import org.activehome.tools.file.TypeMime;
import org.kevoree.log.Log;

/**
 * Dedicated handler for BatteryAutonomy request.
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class BatteryAutonomyRequestHandler implements RequestHandler {

    /**
     * The request to handle.
     */
    private final Request request;
    /**
     * The service that will handle it.
     */
    private final BatteryAutonomy service;

    /**
     * @param theRequest The request to handle
     * @param theService The service that will execute it
     */
    public BatteryAutonomyRequestHandler(final Request theRequest,
                                         final BatteryAutonomy theService) {
        request = theRequest;
        service = theService;
    }

    /**
     * @return Json content or Error
     */
    public final Object html() {
        JsonObject wrap = new JsonObject();
        wrap.add("name", "batteryautonomy-view");
        wrap.add("url", service.getId() + "/batteryautonomy-view.html");
        wrap.add("title", "AH Battery Autonomy");
        wrap.add("description", "Active Home Battery Autonomy");

        JsonObject json = new JsonObject();
        json.add("wrap", wrap);
        return json;
    }

    /**
     * @param fileName The file name
     * @return Content wrapped in Json
     */
    public final JsonValue file(final String fileName) {
        String content = FileHelper.fileToString(fileName,
                getClass().getClassLoader());
        if (fileName.endsWith(".html")) {
            content = content.replaceAll("\\$\\{id\\}", service.getId());
            if (fileName.equals("batteryautonomy-view.html")) {
                String gradient = FileHelper.fileToString("gradient.svg",
                        getClass().getClassLoader());
                content = content.replace("${gradient}", gradient);
            }
        }
        JsonObject json = new JsonObject();
        json.add("content", content);
        json.add("mime", TypeMime.valueOf(
                fileName.substring(fileName.lastIndexOf(".") + 1,
                        fileName.length())).getDesc());
        return json;
    }

    public final JsonValue currentValues() {
        JsonObject json = new JsonObject();
        json.add("storage.availabilityKWh", service.getCurrentSoCKWh() + "");
        json.add("storage.availabilityPercent", service.getCurrentSoCPercent() + "");
//        json.add("power.storage", service.getCurrentBatteryPower().getValue());
//        json.add("storage.status", service.getCurrentStatus().name());
//        if (service.getAutonomyCurrentCons()!=null) {
//            json.add("storage.autonomyCurrentCons", service.getAutonomyCurrentCons());
//        }
//        if (service.getAutonomyConsPred()!=null) {
//            json.add("storage.autonomyConsPred", service.getAutonomyConsPred());
//        }
//        if (service.getAutonomyConsGenPrd()!=null) {
//            json.add("storage.autonomyConsGenPred", service.getAutonomyConsGenPrd());
//        }
//        if (service.getRemainingCurrentGen()!=null) {
//            json.add("storage.remainingCurrentGen", service.getRemainingCurrentGen());
//        }
//        if (service.getRemainingGenPred()!=null) {
//            json.add("storage.remainingGenPred", service.getRemainingGenPred());
//        }
//        if (service.getRemainingGenConsPrd()!=null) {
//            json.add("storage.remainingGenCons", service.getRemainingGenConsPrd());
//        }
        return json;
    }

}
