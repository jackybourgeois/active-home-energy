package org.activehome.energy.grid.test;

import org.activehome.energy.grid.FuelType;
import org.activehome.energy.grid.GridStatus;
import org.activehome.energy.grid.GridWatchLoader;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.LinkedList;

/**
 * @author Jacky Bourgeois
 * @version 17/09/2016.
 *          <p>
 *          Check that file is loaded properly
 */
public class TestGridWatchLoader {

    @Test
    public void loadingData() {
        GridWatchLoader gwl = new GridWatchLoader(
                Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("gridwatch_test.csv"));
        LinkedList<GridStatus> gridStatus = gwl.getGridStatusList();

        assertEquals("5 GridStatus should be loaded", 5, gridStatus.size());
        assertEquals("Check the timestamp", 1464739203000L,
                gridStatus.getFirst().getTimestamp());
        assertEquals("Check 2nd 'demand'  ", 22742, gridStatus.get(1).getDemand());
        assertEquals("Check 5th 'other'", 1595,
                (int) gridStatus.get(4).getFuelMap().get(FuelType.OTHER));
    }

    @Test
    public void totalGeneration() {
        GridWatchLoader gwl = new GridWatchLoader(
                Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("gridwatch_test.csv"));
        LinkedList<GridStatus> gridStatus = gwl.getGridStatusList();

        int totalPower = 790 + 7741 + 7047 + 2581 + 1698 + 818 + 106 + 284 + 64 + 1602;
        assertEquals("Checking total generation", totalPower,
                gridStatus.get(0).computeTotalGeneration());
    }

    @Test
    public void carbonIntensity() {

        GridWatchLoader gwl = new GridWatchLoader(
                Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("gridwatch_test.csv"));
        LinkedList<GridStatus> gridStatus = gwl.getGridStatusList();

        double totalPower = 790 + 7741 + 7047 + 2581 + 1698 + 818 + 106 + 284 + 64 + 1602;
        double carbon = 790 / totalPower * FuelType.COAL.getCarbonIntensity()
                + 7741  / totalPower * FuelType.NUCLEAR.getCarbonIntensity()
                + 7047  / totalPower * FuelType.CCGT.getCarbonIntensity()
                + 2581  / totalPower * FuelType.WIND.getCarbonIntensity()
                + 1698  / totalPower * FuelType.INTFR.getCarbonIntensity()
                + 818   / totalPower * FuelType.INTNED.getCarbonIntensity()
                + 106   / totalPower * FuelType.INTIRL.getCarbonIntensity()
                + 284   / totalPower * FuelType.INTEW.getCarbonIntensity()
                + 64   / totalPower * FuelType.NPSHYD.getCarbonIntensity()
                + 1602  / totalPower * FuelType.OTHER.getCarbonIntensity();

        assertEquals("Checking carbon intensity", carbon,
                gridStatus.get(0).computeCarbonIntensity(), 0.00001);
    }

}