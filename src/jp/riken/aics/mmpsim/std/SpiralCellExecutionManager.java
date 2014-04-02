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
public class SpiralCellExecutionManager 
    extends CellExecutionManagerWithNormalRnd
{
    public SpiralCellExecutionManager(int nTimeSteps, double mu, double sigma) 
    {
        super(new CellLocation2D(0, 0), CellDependancy.FIRST_NEIGHBORS, 
                nTimeSteps, mu, sigma);
    }
    
    @Override
    public CellLocation2D nextCell(CellLocation2D loc, int nRows, int nCols) {
        int row = loc.getRow();
        int col = loc.getCol();

        if(col == nCols - 1 && row < nRows - 1) {
            return new CellLocation2D(row + 1, col);
        }

        if(row == nRows - 1 && col > 0 && nRows > 1) {
            return new CellLocation2D(row, col - 1);
        }

        if(col == 0 && row > 1 && nRows > 2 && nCols > 1) {
            return new CellLocation2D(row - 1, col);
        }

        if(col == nCols - 2 && row > 0 && row < nRows - 2 && nCols > 2) {
            return new CellLocation2D(row + 1, 1);
        }

        if((col == 0 && row < 2) || col > 0 && col < nCols - 1 && (row < nRows - 1 || nRows == 1)) {
            if(col == nCols - 2 && row == nRows - 2 && nRows != 2) {
                return null;
            }
            if((nRows == 2 || nCols == 2) && row == 1 && col == 0) {
                return null;
            }
            return new CellLocation2D(row, col + 1);
        }

        return null;
    }

    @Override
    public CellLocation2D getCellAtBarrier(int nRows, int nCols) {
        return null;
    }
    
    @Override
    public CellLocation2D getBoundaryExchangeTriggerCell(Direction d, 
            int nRows, int nCols, int borderThickness) 
    {
        if(borderThickness <= 0)
            return null;
        
        switch(d) {
            case UPPER_LEFT:
                return new CellLocation2D(borderThickness - 1, borderThickness - 1);
                
            case ABOVE:
                return new CellLocation2D(borderThickness - 1, nCols - 1);
                
            case UPPER_RIGHT:
                return new CellLocation2D(borderThickness - 1, nCols - 1);
                
            case RIGHT:
                return new CellLocation2D(nRows - 1, nCols - 1);
                
            case LOWER_RIGHT:
                return new CellLocation2D(nRows - borderThickness, 
                        nCols - borderThickness);
                
            case BELOW:
                return new CellLocation2D(nRows - borderThickness, 
                        borderThickness - 1);
                
            case LOWER_LEFT:
                return new CellLocation2D(nRows - borderThickness, 
                        borderThickness - 1);
                
            case LEFT:
                return new CellLocation2D(borderThickness, borderThickness - 1);
                
                
        }
        
        return null;
    }
    
    @Override
    public String toString() {
        return "Decentralized Spiral";
    }
    
}
