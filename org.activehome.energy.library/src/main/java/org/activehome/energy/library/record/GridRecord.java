package org.activehome.energy.library.record;

/*
 * #%L
 * Active Home :: Energy :: Library
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


import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.activehome.context.data.MetricRecord;

import java.util.TreeMap;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class GridRecord {

    private TreeMap<String, MetricRecord> co2Map;
    private TreeMap<String, MetricRecord> shareMap;

    public GridRecord() {
        co2Map = new TreeMap<>();
        shareMap = new TreeMap<>();
    }

    public GridRecord(final JsonObject json) {

        co2Map = new TreeMap<>();
        for (JsonValue val : json.get("co2").asArray()) {
            MetricRecord mr = new MetricRecord(val.asObject());
            co2Map.put(mr.getMetricId(), new MetricRecord(val.asObject()));
        }

        shareMap = new TreeMap<>();
        for (JsonValue val : json.get("share").asArray()) {
            MetricRecord mr = new MetricRecord(val.asObject());
            shareMap.put(mr.getMetricId(), new MetricRecord(val.asObject()));
        }

    }

    public TreeMap<String, MetricRecord> getCO2Map() {
        return co2Map;
    }

    public TreeMap<String, MetricRecord> getShareMap() {
        return shareMap;
    }

    public final void setCO2Map(final TreeMap<String, MetricRecord> co2Map) {
        this.co2Map = co2Map;
    }

    public final void setShareMap(final TreeMap<String, MetricRecord> shareMap) {
        this.shareMap = shareMap;
    }

    public final JsonObject toJson() {
        JsonObject json = new JsonObject();

        JsonArray co2 = new JsonArray();
        for (String key : co2Map.keySet()) co2.add(co2Map.get(key).toJson());
        json.add("co2", co2);

        JsonArray share = new JsonArray();
        for (String key : shareMap.keySet()) share.add(shareMap.get(key).toJson());
        json.add("share", share);

        return json;
    }


}
