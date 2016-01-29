package org.activehome.energy.library.oc;

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

/**
 * Avoid two load running at the same time on the same device.
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class OneLoadAtATime implements OperationalConstraint {

    private final LinkedList<OneLoadAtATimeItem> items;
    private final int nbSlot;
    private final long granularity;
    private final String name;

    public OneLoadAtATime(final int nbSlot,
                          final long granularity,
                          final String deviceName) {
        items = new LinkedList<>();
        name = OneLoadAtATime.class.getSimpleName() + "_" + deviceName;
        this.nbSlot = nbSlot;
        this.granularity = granularity;
    }


    public OneLoadAtATime(final JsonObject json) {
        name = json.get("description").asString();
        items = new LinkedList<>();
        nbSlot = json.get("nbSlot").asInt();
        granularity = json.get("granularity").asLong();
        for (JsonValue val : json.get("items").asArray()) {
            items.add(new OneLoadAtATimeItem(val.asObject().get("index").asInt(),
                    val.asObject().get("length").asInt()));
        }
    }

    public void addLoad(final MetricRecord load,
                        final int index) {
        int duration = (int) (load.getTimeFrame() / granularity);
        if (load.getTimeFrame()%granularity!=0) {
            duration += 1;
        }
        items.add(new OneLoadAtATimeItem(index, duration));
    }

    public final String getName() {
        return name;
    }

    @Override
    public final boolean isValid(final int[] solution) {
        int[] counterArray = new int[nbSlot];
        for (int i = 0; i < counterArray.length; i++) {
            counterArray[i] = 0;
        }
        for (OneLoadAtATimeItem item : items) {
            int start = solution[item.getIndex()];
            int end = start + item.getLength() - 1;
//            System.out.println(name + " => start: " + start + " end: " + end);
            for (int j = start; j <= end; j++) {
                counterArray[j]++;
            }
        }
        boolean valid = true;
        for (int counter : counterArray) {
            if (counter > 1) {
                valid = false;
            }
        }
        return valid;
    }

    public final LinkedList<OneLoadAtATimeItem> getItems() {
        return items;
    }

    public String toString() {
        return toJson().toString();
    }

    @Override
    public final JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("type", this.getClass().getName());
        json.add("description", name);
        JsonArray items = new JsonArray();
        for (OneLoadAtATimeItem it : this.items) {
            JsonObject jsonIt = new JsonObject();
            jsonIt.add("index", it.getIndex());
            jsonIt.add("length", it.getLength());
            items.add(jsonIt);
        }
        json.add("items", items);
        json.add("nbSlot", nbSlot);
        json.add("granularity", granularity);
        return json;
    }

    private class OneLoadAtATimeItem {

        private final int index;
        private final int length;

        public OneLoadAtATimeItem(final int theIndex,
                                  final int duration) {
            index = theIndex;
            length = duration;
        }

        public final int getIndex() {
            return index;
        }

        public final int getLength() {
            return length;
        }
    }

}
