package org.activehome.energy.battery;

/*
 * #%L
 * Active Home :: Energy :: Battery
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


/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public class EnergyUnit {

    private long tsIn;
    private long tsOut;
    private boolean consumed;

    public EnergyUnit(long tsIn) {
        this.tsIn = tsIn;
        this.consumed = false;
    }

    public long getTsIn() {
        return tsIn;
    }

    public long getTsOut() {
        return tsOut;
    }

    public void setTsOut(long tsOut) {
        this.tsOut = tsOut;
        this.consumed = true;
    }

    public double getAge() {
        return (tsOut-tsIn)/3600000.;
    }

    public boolean isConsumed() {
        return consumed;
    }
}
