package org.activehome.energy.library.etp;

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


/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public abstract class EnergyTariffPolicy {

    public abstract double calculate(double energyKWh, double rate);

}

/**
 * Standard German fiscal meter.
 * - Import cost: Fix import tariff (EUR/kWh)
 * - Generation benefit: if generation < consumption: FiT (EUR/kWh)
 * - Export benefit:
 * if export <= consumption/3: FiT-0.1638 (EUR/kWh)
 * if export > consumption/3: FiT-0.12 (EUR/kWh)
 *
 */
