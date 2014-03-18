/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jp.riken.aics.mmpsim.std;

import jp.riken.aics.mmpsim.CellDependancy;
import jp.riken.aics.mmpsim.CellLocation2D;
import jp.riken.aics.mmpsim.Direction;

/**
 *
 * @author hamed
 */
public class BarrierCellExecutionManager 
    extends CellExecutionManagerWithNormalRnd 
{

    public BarrierCellExecutionManager(int nTimeSteps, double mu, double sigma) 
    {
        super(new CellLocation2D(0, 0), CellDependancy.FIRST_NEIGHBORS, 
                nTimeSteps, mu, sigma);
    }
    
    @Override
    public CellLocation2D nextCell(CellLocation2D loc, int nRows, int nCols) {
        if(loc.getCol() >= nCols - 1) {
            if(loc.getRow() >= nRows - 1)
                return null;
            return new CellLocation2D(loc.getRow() + 1, 0);
        }
        return new CellLocation2D(loc.getRow(), loc.getCol() + 1);
    }

    @Override
    public CellLocation2D getBoundaryExchangeTriggerCell(Direction d, int nRows, int nCols, int borderThickness) {
        return null;
    }

    @Override
    public CellLocation2D getCellAtBarrier(int nRows, int nCols) {
        return new CellLocation2D(0, 0);
    }

    @Override
    public String toString() {
        return "Barrier";
    }
}
