package org.activehome.energy.balancer.test;

/*
 * #%L
 * Active Home :: Energy :: Power Balancer
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


import org.activehome.com.Status;
import org.activehome.context.data.DataPoint;
import org.activehome.context.data.Device;
import org.activehome.energy.balancer.data.ApplianceContext;
import org.activehome.energy.balancer.PowerBalancer;
import org.junit.Test;
import org.kevoree.annotation.Param;

import java.util.HashMap;
import java.util.Random;

/**
 * Off line tests for the Balancer
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class TestPowerBalancer {

    public static void main(String[] args) {
        long time = 0;
        double value = 0;
        Random random = new Random(0);
        System.out.println("bg1," + time + "," + 0.0);
        for (int i = 0; i < 20; i++) {
            time += Math.abs(random.nextLong()) % 1800000;
            value = value == 0 ? 500 : 0;
            DataPoint dp = new DataPoint("bg1", time, value + "");
            System.out.println("bg1," + dp.getTS() + "," + dp.getValue());
        }
        time = 0;
        random = new Random(1);
        System.out.println("bg2," + time + "," + 0.0);
        for (int i = 0; i < 20; i++) {
            time += Math.abs(random.nextLong()) % 1800000;
            value = value == 0 ? 200 : 0;
            DataPoint dp = new DataPoint("bg2", time, value + "");
            System.out.println("bg2," + dp.getTS() + "," + dp.getValue());
        }
    }

    @Test
    public void test() {
        PowerBalancer powerBalancer = new PowerBalancer();

        HashMap<String, ApplianceContext> deviceMap = new HashMap<>();
        Device dev1 = new Device("bg1");
        dev1.setParams(true, false, false);
        deviceMap.put("bg1", new ApplianceContext(dev1));
        Device dev2 = new Device("bg2");
        dev2.setParams(true, false, false);
        deviceMap.put("bg1", new ApplianceContext(dev2));

        powerBalancer.setDeviceMap(deviceMap);

        powerBalancer.updateDeviceStatus("bg1", Status.ON, 0);
        powerBalancer.updateDeviceCurrentPower("bg1", 500, 0);
        powerBalancer.updateDeviceStatus("bg2", Status.ON, 0);
        powerBalancer.updateDeviceCurrentPower("bg2", 200, 0);

        // balance = ..., anything to do to reduce / increase
        // bg status ON && power>0
        // control
    }

}
