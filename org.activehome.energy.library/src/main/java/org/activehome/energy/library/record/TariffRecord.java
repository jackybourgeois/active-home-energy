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
import org.activehome.context.data.Schedule;
import org.activehome.context.data.MetricRecord;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class TariffRecord {

    private MetricRecord importTariff;
    private MetricRecord exportTariff;
    private MetricRecord generationTariff;

    public TariffRecord(final MetricRecord impCost,
                        final MetricRecord expBenefits,
                        final MetricRecord genBenefits) {
        importTariff = impCost;
        exportTariff = expBenefits;
        generationTariff = genBenefits;
    }

    public TariffRecord() {
        importTariff = new MetricRecord("importCost");
        exportTariff = new MetricRecord("exportBenefits");
        generationTariff = new MetricRecord("generationBenefits");
    }

    public TariffRecord(final JsonObject json) {
        importTariff = new MetricRecord(json.get("import").asObject());
        exportTariff = new MetricRecord(json.get("export").asObject());
        generationTariff = new MetricRecord(json.get("generation").asObject());
    }

    public final MetricRecord getImport() {
        return importTariff;
    }

    public final MetricRecord getExport() {
        return exportTariff;
    }

    public final MetricRecord getGeneration() {
        return generationTariff;
    }


    public final void setImport(final MetricRecord imp) {
        importTariff = imp;
    }

    public final void setExport(final MetricRecord export) {
        exportTariff = export;
    }

    public final void setGeneration(final MetricRecord generation) {
        generationTariff = generation;
    }


    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.add("import", importTariff.toJson());
        json.add("export", exportTariff.toJson());
        json.add("generation", generationTariff.toJson());

        return json;
    }

    public JsonObject toJson(final Schedule schedule) {
        JsonObject json = toJson();

        String[] array = schedule.normalize(importTariff);
        JsonArray jsonArray = new JsonArray();
        for (String val : array) jsonArray.add(val);
        json.get("import").asObject().add("normalized", jsonArray);

        array = schedule.normalize(exportTariff);
        jsonArray = new JsonArray();
        for (String val : array) jsonArray.add(val);
        json.get("export").asObject().add("normalized", jsonArray);

        array = schedule.normalize(generationTariff);
        jsonArray = new JsonArray();
        for (String val : array) jsonArray.add(val);
        json.get("generation").asObject().add("normalized", jsonArray);

        return json;
    }

}
