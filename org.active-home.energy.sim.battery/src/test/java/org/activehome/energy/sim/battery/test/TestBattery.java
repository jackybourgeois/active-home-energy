package org.activehome.energy.sim.battery.test;

/*
 * #%L
 * Active Home :: Energy :: Sim :: Battery
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


import junit.framework.Assert;
import org.activehome.com.Status;
import org.activehome.energy.sim.battery.Battery;
import org.activehome.energy.sim.battery.BatteryScheme;
import org.activehome.energy.sim.battery.BatteryUpdateSummary;
import org.activehome.time.TimeControlled;
import org.junit.Test;

/**
 * Off line test cases for the Battery
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class TestBattery {

    @Test
    public void testCharge() {
        Battery battery = new Battery(1, BatteryScheme.SERIAL, 10, 2, 2000, 1000, 0.8, 0.9);
        battery.start();
        BatteryUpdateSummary summary = battery.charge(1000, TimeControlled.HOUR);   // 1kW for 1hr

        Assert.assertEquals(0.2, summary.getLosses());
        Assert.assertEquals(0.8, summary.getUpdate());
        Assert.assertEquals(2.8, battery.getStateOfCharge());
    }

    @Test
    public void testChargeOverRate() {
        Battery battery = new Battery(1, BatteryScheme.SERIAL, 10, 2, 2000, 1000, 0.8, 0.9);
        battery.start();
        BatteryUpdateSummary summary = battery.charge(3000, TimeControlled.HOUR);   // 3kW for 1hr

        Assert.assertEquals(0.4, summary.getLosses());
        Assert.assertEquals(1.6, summary.getUpdate());
        Assert.assertEquals(3.6, battery.getStateOfCharge());
    }

    @Test
    public void testChargeOverCapacity() {
        Battery battery = new Battery(1, BatteryScheme.SERIAL, 10, 2, 2000, 1000, 0.8, 0.9);
        battery.start();
        for (int i = 1; i < 10; i++) {
            BatteryUpdateSummary summary = battery.charge(1500, TimeControlled.HOUR);   // 1.5kW for 1hr
            if (summary.getStatus().equals(Status.FULL)) {
                double expectedLosses = org.activehome.tools.Util.round5((10-summary.getPrevSoc())*1.25-(10-summary.getPrevSoc()));
                Assert.assertEquals(expectedLosses, summary.getLosses());
                Assert.assertEquals(10-summary.getPrevSoc(), summary.getUpdate());
                Assert.assertEquals(10.0, battery.getStateOfCharge());
            } else {
                Assert.assertEquals(true,i<7);
                Assert.assertEquals(0.3, summary.getLosses());
                Assert.assertEquals(1.2, summary.getUpdate());
                Assert.assertEquals(2 + i*1.2, battery.getStateOfCharge());
            }
        }
    }

    @Test
    public void testDischarge() {
        Battery battery = new Battery(1, BatteryScheme.SERIAL, 10, 2, 2000, 1000, 0.8, 0.9);
        battery.start();
        battery.charge(2000, 3600000 * 5);   // 2kW for 5hr to FULL
        Assert.assertEquals(Status.FULL, battery.getStatus());
        Assert.assertEquals(10.0,battery.getStateOfCharge());

        BatteryUpdateSummary summary = battery.discharge(500, TimeControlled.HOUR);   // 1kW for 1hr
        double discharged = (1 / 0.9) * 0.5;
        Assert.assertEquals(org.activehome.tools.Util.round5(discharged - 0.5), summary.getLosses());
        Assert.assertEquals(org.activehome.tools.Util.round5(discharged), summary.getUpdate());
        Assert.assertEquals(org.activehome.tools.Util.round5(10-discharged), battery.getStateOfCharge());
    }

    @Test
    public void testDischargeOverRate() {
        Battery battery = new Battery(1, BatteryScheme.SERIAL, 10, 2, 2000, 1000, 0.8, 0.9);
        battery.start();
        battery.charge(2000, 3600000 * 5);   // 2kW for 5hr to FULL
        Assert.assertEquals(Status.FULL, battery.getStatus());
        Assert.assertEquals(10.0,battery.getStateOfCharge());

        BatteryUpdateSummary summary = battery.discharge(1500, TimeControlled.HOUR);   // 1kW for 1hr
        double discharged = 1 / 0.9;
        Assert.assertEquals(org.activehome.tools.Util.round5(discharged - 1), summary.getLosses());
        Assert.assertEquals(org.activehome.tools.Util.round5(discharged), summary.getUpdate());
        Assert.assertEquals(org.activehome.tools.Util.round5(10-discharged), battery.getStateOfCharge());
    }

    @Test
    public void testDischargeUnderCapacity() {
//        Battery battery = new Battery(1, BatteryScheme.SERIAL, 10, 2, 2000, 1000, 0.8, 0.9);
//        battery.start();
//        battery.charge(2000, 3600000 * 5);   // 2kW for 5hr to FULL
//        Assert.assertEquals(Status.FULL, battery.getStatus());
//        Assert.assertEquals(10.0,battery.getStateOfCharge());
//
//        for (int i = 1; i < 10; i++) {
//            BatteryUpdateSummary summary = battery.charge(750, 3600000);   // 1.5kW for 1hr
//            if (summary.getStatus().equals(Status.EMPTY)) {
//                Assert.assertEquals(org.activehome.tools.Util.round5((summary.getPrevSoc()-2.0)*0.9), summary.getLosses());
//                Assert.assertEquals(summary.getPrevSoc()-2.0, summary.getUpdate());
//                Assert.assertEquals(2.0, battery.getStateOfCharge());
//            } else {
//                Assert.assertEquals(true,i<10);
//                double discharged = (1 / 0.9) * 0.75;
//                Assert.assertEquals(org.activehome.tools.Util.round5(discharged - 0.75), summary.getLosses());
//                Assert.assertEquals(org.activehome.tools.Util.round5(discharged), summary.getUpdate());
//                Assert.assertEquals(org.activehome.tools.Util.round5(10-i*discharged), battery.getStateOfCharge());
//            }
//        }
    }

    @Test
    public void testMultiCellSerial() {

    }

    @Test
    public void testMultiCellParallel() {

    }
}
