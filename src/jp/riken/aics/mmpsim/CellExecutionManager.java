/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

/**
 *
 * @author hamed
 */
public abstract class CellExecutionManager {
    public final CellDependancy cellDependancy;    
    private final CellLocation2D startCell;
    private int nTimeSteps;
    private int bufferDepth = 2;
    private int linkedVariablesSize = 0;
    
    public CellExecutionManager(CellLocation2D startCell,
            CellDependancy cellDependancy, 
            int nTimeSteps) 
    {
        this.startCell = startCell;
        this.cellDependancy = cellDependancy;
        this.nTimeSteps = nTimeSteps;
    }

    public CellLocation2D getStartCell() {
        return startCell;
    }
    
    public int getNTimeSteps() {
        return nTimeSteps;
    }

    public CellExecutionManager setNTimeSteps(int nTimeSteps) {
        this.nTimeSteps = nTimeSteps;
        return this;
    }

    public int getBufferDepth() {
        return bufferDepth;
    }

    public CellExecutionManager setBufferDepth(int bufferDepth) {
        this.bufferDepth = bufferDepth;
        return this;
    }

    public int getLinkedVariablesSize() {
        return linkedVariablesSize;
    }

    public CellExecutionManager setLinkedVariablesSize(int linkedVariablesSize) {
        this.linkedVariablesSize = linkedVariablesSize;
        return this;
    }
    
    public abstract CellLocation2D nextCell(CellLocation2D loc, final int nRows, final int nCols);
    public abstract double getExecutionTime(CellInfo cell);
    public abstract CellLocation2D getBoundaryExchangeTriggerCell(Direction d, int nRows, int nCols, int borderThickness);
    public abstract CellLocation2D getCellAtBarrier(int nRows, int nCols);
}