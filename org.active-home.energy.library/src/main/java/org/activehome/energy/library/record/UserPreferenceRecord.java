package org.activehome.energy.library.record;

/*
 * #%L
 * Active Home :: Energy :: Library
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


import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.activehome.context.data.MetricRecord;

import java.util.HashMap;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class UserPreferenceRecord {

    private HashMap<String, MetricRecord> appUsagePreference;

    public UserPreferenceRecord() {
        appUsagePreference = new HashMap<>();
    }

    public UserPreferenceRecord(final JsonObject json) {
        appUsagePreference = new HashMap<>();
        for (JsonValue val : json.get("appUsagePreference").asArray()) {
            MetricRecord mr = new MetricRecord(val.asObject());
            appUsagePreference.put(mr.getMetricId(), mr);
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray pref = new JsonArray();
        for (String key : appUsagePreference.keySet()) pref.add(appUsagePreference.get(key).toJson());
        json.add("appUsagePreference", pref);
        return json;
    }

    public void setAppUsagePreference(final HashMap<String, MetricRecord> usagePreference) {
        appUsagePreference = usagePreference;
    }

    public HashMap<String, MetricRecord> getUsagePreference() {
        return appUsagePreference;
    }

}
