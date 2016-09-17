package org.activehome.energy.grid;

import org.kevoree.log.Log;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Jacky Bourgeois
 * @version  17/09/2016.
 *
 * Read a data extract (.csv) from Grid Watch
 * (http://www.gridwatch.templar.co.uk/download.php)
 *
 * CSV details: id, timestamp, demand, frequency,
 * coal, nuclear, ccgt, wind, french_ict, dutch_ict,
 * irish_ict, ew_ict, pumped, hydro, oil, ocgt, other
 */
public class GridWatchLoader {

    /**
     * List of GridStatus ordered by time.
     */
    private LinkedList<GridStatus> gridStatusList;


    public GridWatchLoader(final InputStream is) {
        load(is);
    }

    private void load(final InputStream is) {
        gridStatusList = new LinkedList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String line;
        String[] values = new String[]{};
        boolean label = true;
        System.out.println(is);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            while ((line = br.readLine()) != null) {
                if (!label) {
                    values = line.replaceAll(" ","").split(",");
                    long timestamp = sdf.parse(values[1]).getTime();
                    int demand = Integer.valueOf(values[2]);
                    double frequency = Double.valueOf(values[3]);
                    Map<FuelType, Integer> fuelMap = new HashMap<>();
                    fuelMap.put(FuelType.COAL, Integer.valueOf(values[4]));
                    fuelMap.put(FuelType.NUCLEAR, Integer.valueOf(values[5]));
                    fuelMap.put(FuelType.CCGT, Integer.valueOf(values[6]));
                    fuelMap.put(FuelType.WIND, Integer.valueOf(values[7]));
                    fuelMap.put(FuelType.INTFR, Integer.valueOf(values[8]));
                    fuelMap.put(FuelType.INTNED, Integer.valueOf(values[9]));
                    fuelMap.put(FuelType.INTIRL, Integer.valueOf(values[10]));
                    fuelMap.put(FuelType.INTEW, Integer.valueOf(values[11]));
                    fuelMap.put(FuelType.PS, Integer.valueOf(values[12]));
                    fuelMap.put(FuelType.NPSHYD, Integer.valueOf(values[13]));
                    fuelMap.put(FuelType.OIL, Integer.valueOf(values[14]));
                    fuelMap.put(FuelType.OCGT, Integer.valueOf(values[15]));
                    fuelMap.put(FuelType.OTHER, Integer.valueOf(values[16]));
                    GridStatus gridStatus = new GridStatus(timestamp,
                            demand, frequency, fuelMap);
                    gridStatusList.addLast(gridStatus);
                } else {
                    label = false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            Log.error("Unable to parse the timestamp: " + values[1]);
        }

    }

    public LinkedList<GridStatus> getGridStatusList() {
        return gridStatusList;
    }

}
