package org.activehome.energy.sim.battery;

/*
 * #%L
 * Active Home :: Energy :: Sim :: Battery
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
 * Types of wiring scheme between battery cells.
 *
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public enum BatteryScheme {

    /**
     * Battery cells connected in series
     */
    SERIAL,
    /**
     * Battery cells connected in parallel
     */
    PARALLEL

}
