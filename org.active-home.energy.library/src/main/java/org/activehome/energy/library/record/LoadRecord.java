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

import java.util.LinkedList;
import java.util.TreeMap;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class LoadRecord {

    private TreeMap<String, LinkedList<MetricRecord>> interactiveMap;
    private TreeMap<String, MetricRecord> backgroundMap;
    private TreeMap<String, MetricRecord> generationMap;

    public LoadRecord() {
        interactiveMap = new TreeMap<>();
        backgroundMap = new TreeMap<>();
        generationMap = new TreeMap<>();
    }

    public LoadRecord(final JsonObject json) {
        interactiveMap = new TreeMap<>();
        for (JsonObject.Member member : json.get("interactive").asObject()) {
            interactiveMap.put(member.getName(), new LinkedList<>());
            for (JsonValue jsonMR : member.getValue().asArray()) {
                interactiveMap.get(member.getName()).add(new MetricRecord(jsonMR.asObject()));
            }
        }

        backgroundMap = new TreeMap<>();
        for (JsonObject.Member member : json.get("background").asObject()) {
            MetricRecord mr = new MetricRecord(member.getValue().asObject());
            backgroundMap.put(member.getName(), mr);
        }

        generationMap = new TreeMap<>();
        for (JsonObject.Member member : json.get("generation").asObject()) {
            MetricRecord mr = new MetricRecord(member.getValue().asObject());
            generationMap.put(member.getName(), mr);
        }

    }

    public TreeMap<String, MetricRecord> getBackgroundLoadMap() {
        return backgroundMap;
    }

    public TreeMap<String, MetricRecord> getGenerationMap() {
        return generationMap;
    }

    public TreeMap<String, LinkedList<MetricRecord>> getInteractives() {
        return interactiveMap;
    }


    public void setInteractiveMap(final TreeMap<String, LinkedList<MetricRecord>> theInteractiveMap) {
        interactiveMap = theInteractiveMap;
    }

    public void setBackgroundMap(final TreeMap<String, MetricRecord> backgraoundLoadMap) {
        backgroundMap = backgraoundLoadMap;
    }

    public void setGenerationMap(final TreeMap<String, MetricRecord> theGenerationMap) {
        generationMap = theGenerationMap;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    public final JsonObject toJson() {
        JsonObject json = new JsonObject();

        JsonObject interactiveLoad = new JsonObject();
        for (String key : interactiveMap.keySet()) {
            JsonArray mrLoadArray = new JsonArray();
            for (MetricRecord mr : interactiveMap.get(key)) {
                mrLoadArray.add(mr.toJson());
            }
            interactiveLoad.add(key, mrLoadArray);
        }
        json.add("interactive", interactiveLoad);

        JsonObject bgLoad = new JsonObject();
        for (String key : backgroundMap.keySet()) bgLoad.add(key, backgroundMap.get(key).toJson());
        json.add("background", bgLoad);

        JsonObject gen = new JsonObject();
        for (String key : generationMap.keySet()) gen.add(key, generationMap.get(key).toJson());
        json.add("generation", gen);

        return json;
    }

    /*public JsonObject toJson() {

        JsonObject json = new JsonObject();

        JsonArray interactiveLoad = new JsonArray();
        for (MetricRecord mr : _interactiveList) {
            interactiveLoad.add(mr.toJson());
        }
        json.add("interactiveLoad", interactiveLoad);

        JsonArray bgLoad = new JsonArray();
        for (String key : backgroundMap.keySet()) {
            JsonObject jsonBg = backgroundMap.get(key).toJson();
            double[] array = schedule.normalizedLoad(backgroundMap.get(key));
            JsonArray jsonArray = new JsonArray();
            for (double val : array) jsonArray.add(val);
            jsonBg.add("normalized",jsonArray);
            bgLoad.add(jsonBg);
        }
        json.add("backgroundLoad", bgLoad);

        JsonArray gen = new JsonArray();
        for (String key : generationMap.keySet()) {
            JsonObject jsonGen = generationMap.get(key).toJson();
            double[] array = schedule.normalizedLoad(generationMap.get(key));
            JsonArray jsonArray = new JsonArray();
            for (double val : array) jsonArray.add(val);
            jsonGen.add("normalized",jsonArray);
            gen.add(jsonGen);
        }
        json.add("generation", gen);

        return json;
    }*/

}
