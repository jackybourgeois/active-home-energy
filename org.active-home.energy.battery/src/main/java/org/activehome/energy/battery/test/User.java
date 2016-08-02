package org.activehome.energy.battery.test;

import org.activehome.energy.battery.BatteryInfo;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class User {

    private String id;
    private double homeBatCapacity;
    private BatteryInfo batteryInfo;

    public User(String id, double homeBatCapacity, BatteryInfo batteryInfo) {
        this.id = id;
        this.homeBatCapacity = homeBatCapacity;
        this.batteryInfo = batteryInfo;
    }

    public String getId() {
        return id;
    }

    public double getHomeBatCapacity() {
        return homeBatCapacity;
    }

    public BatteryInfo getBatteryInfo() {
        return batteryInfo;
    }
}
