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
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class LoadSequence implements OperationalConstraint {

    private final LinkedList<LoadSequenceItem> items;
    private final String name;
    private final long granularity;

    public LoadSequence(final String theDescription,
                        final long granularity) {
        name = theDescription;
        items = new LinkedList<>();
        this.granularity = granularity;
    }

    public LoadSequence(final JsonObject json) {
        name = json.get("description").asString();
        granularity = json.get("granularity").asLong();
        items = new LinkedList<>();
        for (JsonValue val : json.get("items").asArray()) {
            items.add(new LoadSequenceItem(
                    val.asObject().get("index").asInt(),
                    val.asObject().get("length").asInt(),
                    val.asObject().get("ts").asLong()));
        }
    }

    public void addLoad(final MetricRecord load,
                        final int index) {
        int duration = (int) (load.getTimeFrame() / granularity);
        if (load.getTimeFrame()%granularity!=0) {
            duration += 1;
        }
        int i = 0;
        while (i < items.size() && items.get(i).getTs() < load.getStartTime()) {
            i++;
        }
        items.add(i, new LoadSequenceItem(index, duration, load.getStartTime()));
    }

    public final String getName() {
        return name;
    }

    @Override
    public final boolean isValid(final int[] solution) {
        for (int i = 0; i < items.size() - 1; i++) {
            LoadSequenceItem item = items.get(i);
            int end1 = solution[item.getIndex()] + item.getLength();
            int start2 = solution[items.get(i + 1).getIndex()];
            if (end1 >= start2) return false;
        }
        return true;
    }

    private class LoadSequenceItem {

        private final int index;
        private final int length;
        private final long ts;

        public LoadSequenceItem(final int theIndex,
                                final int theLength,
                                final long theTS) {
            index = theIndex;
            length = theLength;
            ts = theTS;
        }

        public int getIndex() {
            return index;
        }
        public int getLength() {
            return length;
        }
        public long getTs() {
            return ts;
        }
    }

    @Override
    public final String toString() {
        return toJson().toString();
    }

    @Override
    public final JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("type", this.getClass().getName());
        json.add("description", name);
        json.add("granularity", granularity);
        JsonArray items = new JsonArray();
        for (LoadSequenceItem it : this.items) {
            JsonObject jsonIt = new JsonObject();
            jsonIt.add("index", it.getIndex());
            jsonIt.add("length", it.getLength());
            jsonIt.add("ts", it.getTs());
            items.add(jsonIt);
        }
        json.add("items", items);
        return json;
    }

}
