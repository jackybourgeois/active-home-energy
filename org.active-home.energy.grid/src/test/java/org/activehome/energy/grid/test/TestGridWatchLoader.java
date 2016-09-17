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

        // assert statements
        assertEquals("5 GridStatus should be loaded", 5, gridStatus.size());
        assertEquals("Check the timestamp", 1464739203000L,
                gridStatus.getFirst().getTimestamp());
        assertEquals("Check 2nd 'demand'  ", 22742, gridStatus.get(1).getDemand());
        assertEquals("Check 5th 'other'", 1595,
                (int)gridStatus.get(4).getFuelMap().get(FuelType.OTHER));
    }

}
