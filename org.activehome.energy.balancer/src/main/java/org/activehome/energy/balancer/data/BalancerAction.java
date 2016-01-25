package org.activehome.energy.balancer.data;

/*
 * #%L
 * Active Home :: Energy :: Balancer
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


import org.activehome.io.action.Action;
import org.activehome.io.action.Command;

import java.util.HashMap;

public class BalancerAction extends Action {

    private final ApplianceContext applianceContext;

    /**
     * @param theApplianceContext
     * @param theStartCmd
     * @param theEndCmd
     * @param theImpact
     * @param theDuration
     */
    public BalancerAction(final ApplianceContext theApplianceContext,
                          final Command theStartCmd,
                          final Command theEndCmd,
                          final HashMap<String, Double> theImpact,
                          final long theDuration) {

        super(theStartCmd, theEndCmd, theImpact, theDuration);
        applianceContext = theApplianceContext;
    }

    public ApplianceContext getApplianceContext() {
        return applianceContext;
    }

}
