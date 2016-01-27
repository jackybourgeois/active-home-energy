package org.activehome.energy.predictor.emulator;

/*
 * #%L
 * Active Home :: Energy :: Predictor :: Emulator
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


import org.activehome.com.RequestCallback;
import org.activehome.context.data.MetricRecord;
import org.activehome.energy.library.record.TariffRecord;
import org.activehome.predictor.Predictor;
import org.activehome.time.TimeControlled;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class ETariffPredictor extends Predictor {

    @Param(defaultValue = "Predict the electricity tariff rates for the given time frame.")
    private String description;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor.emulator/docs/eTariffPredictor.png")
    private String img;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor.emulator/docs/eTariffPredictor.md")
    private String doc;

    @Param(defaultValue = "/activehome-energy/master/org.activehome.energy.predictor.emulator/docs/eTariffPredictor.kevs")
    private String demoScript;

    /**
     * Predict the electricity tariff rates for the given time frame.
     *
     * @param startTS     Start time-stamp of the time frame
     * @param duration    Duration of the time frame
     * @param granularity Duration of each time slot
     * @param callback    where we send the result
     */
    @Override
    public void predict(final long startTS,
                        final long duration,
                        final long granularity,
                        final RequestCallback callback) {

    }

    private TariffRecord generateTariff(final long start, final long horizon) {
        TariffRecord tariff = new TariffRecord();
//
//        tariff.setExport(new MetricRecord("tariff.export",
//                horizon, start, export_benefit));
//        tariff.setGeneration(new MetricRecord("tariff.generation",
//                horizon, start, generation_benefit));
//
//        MetricRecord impTariff = new MetricRecord("tariff.import", horizon);
//        long midnight = start - (start % TimeControlled.DAY);
//        long changeTariffTime = 7 * TimeControlled.HOUR;     // day/night tariff change at 7AM
//        boolean isDay = (start - midnight) >= changeTariffTime;
//        long progress = start;
//        long aNight = 7 * TimeControlled.HOUR;
//        long aDay = 17 * TimeControlled.HOUR;
//
//        if (isDay) {
//            impTariff.addRecord(progress, import_day_tariff);
//            progress += TimeControlled.DAY - start;
//            isDay = false;
//        } else {
//            impTariff.addRecord(progress, import_night_tariff);
//            progress += aNight - start;
//            isDay = true;
//        }
//
//        while (progress < start + horizon) {
//            if (isDay) {
//                impTariff.addRecord(progress, import_day_tariff);
//                progress += aDay;
//                isDay = false;
//            } else {
//                impTariff.addRecord(progress, import_night_tariff);
//                progress += aNight;
//                isDay = true;
//            }
//        }
//        tariff.setImport(impTariff);
//
        return tariff;
    }

}
