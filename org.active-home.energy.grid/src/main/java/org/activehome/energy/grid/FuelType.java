package org.activehome.energy.grid;

/**
 * @author Jacky Bourgeois
 * @version 17/09/2016.
 *
 * Name and carbon intensity of electricity fuels in the UK
 * (From http://www.gridcarbon.uk/)
 */
public enum FuelType {

    CCGT("Closed cycle gas turbine",360),
    OCGT("Open cycle gas turbine",480),
    COAL("Coal", 910),
    NUCLEAR("Nuclear", 0),
    WIND("Wind",0),
    PS("Pumped storage", 0),
    NPSHYD("Non-pumped storage hydro",0),
    OTHER("Other",300),
    OIL("Oil",610),
    INTFR("French Interconnector", 90),
    INTIRL("Irish Interconnector",450),
    INTNED("Dutch Interconnector", 550),
    INTEW("East-West Interconnector", 450);

    private String fuelName;
    private int carbonIntensity;

    FuelType(String fuelName, int carbonIntensity) {
        this.fuelName = fuelName;
        this.carbonIntensity = carbonIntensity;
    }

    public String getFuelName() {
        return fuelName;
    }

    public int getCarbonIntensity() {
        return carbonIntensity;
    }
}
