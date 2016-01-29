package org.activehome.energy.balancer.data;

/*
 * #%L
 * Active Home :: Energy :: Power Balancer
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


import java.util.LinkedList;

public class OptimisationPlan {

    private double currentBalance;
    private double optimisedBalance;
    private LinkedList<BalancerAction> actionToTake;

    public OptimisationPlan(final double theCurrentBalance,
                            final double theOptimisedBalance,
                            final LinkedList<BalancerAction> actions) {
        currentBalance = theCurrentBalance;
        optimisedBalance = theOptimisedBalance;
        actionToTake = actions;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public double getOptimisedBalance() {
        return optimisedBalance;
    }

    public LinkedList<BalancerAction> getActionToTake() {
        return actionToTake;
    }
}
