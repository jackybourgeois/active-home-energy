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
import org.activehome.com.Notif;
import org.activehome.com.Request;
import org.activehome.com.RequestCallback;
import org.activehome.context.Context;
import org.activehome.context.com.ContextRequest;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Trigger;
import org.activehome.context.data.UserInfo;
import org.activehome.service.RequestHandler;
import org.activehome.tools.file.FileHelper;
import org.activehome.tools.file.TypeMime;

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
        wrap.add("name", "battery-view");
        wrap.add("url", service.getId() + "/battery-view.html");
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
        }
        JsonObject json = new JsonObject();
        json.add("content", content);
        json.add("mime", TypeMime.valueOf(
                fileName.substring(fileName.lastIndexOf(".") + 1,
                        fileName.length())).getDesc());
        return json;
    }

}
