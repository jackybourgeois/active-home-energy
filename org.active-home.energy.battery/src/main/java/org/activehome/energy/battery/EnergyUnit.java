package org.activehome.energy.battery;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class EnergyUnit {

    private long tsIn;
    private long tsOut;

    public EnergyUnit(long tsIn) {
        this.tsIn = tsIn;
    }

    public long getTsIn() {
        return tsIn;
    }

    public long getTsOut() {
        return tsOut;
    }

    public void setTsOut(long tsOut) {
        this.tsOut = tsOut;
    }

    public long getLatency() {
        return tsOut-tsIn;
    }
}
