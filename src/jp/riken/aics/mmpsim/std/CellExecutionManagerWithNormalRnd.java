/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jp.riken.aics.mmpsim.std;

import jp.riken.aics.mmpsim.CellDependancy;
import jp.riken.aics.mmpsim.CellExecutionManager;
import jp.riken.aics.mmpsim.CellInfo;
import jp.riken.aics.mmpsim.CellLocation2D;
import net.hkhandan.math.NormalDistribution;

/**
 *
 * @author hamed
 */
public abstract class CellExecutionManagerWithNormalRnd extends CellExecutionManager {
    final private NormalDistribution randomDistribution;

    public CellExecutionManagerWithNormalRnd(CellLocation2D startCell, 
            CellDependancy cellDependancy, int nTimeSteps, double mu, 
            double sigma) 
    {
        super(startCell, cellDependancy, nTimeSteps);
        this.randomDistribution = new NormalDistribution(mu, sigma);
    }

    @Override
    public double getExecutionTime(CellInfo cell) {
        return Math.abs(randomDistribution.generateRandomSample());
    }    
}
