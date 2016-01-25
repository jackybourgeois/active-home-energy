package org.activehome.energy.scheduler.bruteforce;

/*
 * #%L
 * Active Home :: Energy :: Scheduler :: Brute Force
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


import org.activehome.com.RequestCallback;
import org.activehome.context.data.MetricRecord;

import java.util.LinkedList;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
public abstract class SchedulingObjective {

    public abstract double evaluate(int[] solution, double[] load);

    public abstract double meaningfulScoreEvaluation(final int[] solution,
                                                      final double[] load);

    /**
     * Look for the required variables in the schedule,
     * otherwise ask the context.
     *
     * @param callback
     */
    public abstract void getReady(RequestCallback callback);

}
