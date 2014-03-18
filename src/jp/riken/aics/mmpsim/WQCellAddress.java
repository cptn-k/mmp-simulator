/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jp.riken.aics.mmpsim;

public class WQCellAddress extends CellLocation2D {
    public final static int SIZE_IN_BITS = WQThreadAddress.SIZE_IN_BITS + Integer.SIZE * 4;
    
    private final WQThreadAddress processLoc;
    
    private final int absRow;
    private final int absCol;
    
    public WQCellAddress(WQThreadAddress processLoc, int row, int col, int absRow, int absCol) {
        super(row, col);
        this.processLoc = processLoc;
        this.absRow = absRow;
        this.absCol = absCol;
    }

    public WQThreadAddress getProcessLoc() {
        return processLoc;
    }

    public int getAbsRow() {
        return absRow;
    }

    public int getAbsCol() {
        return absCol;
    }
    
    public CellLocation2D getAbsLocation() {
        return new CellLocation2D(absRow, absCol);
    }
        
    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof WQCellAddress))
            return false;
        return super.equals(obj) && ((WQCellAddress)obj).processLoc.equals(processLoc);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 29 * hash + (this.processLoc != null ? this.processLoc.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return processLoc + super.toString() + "(" + absRow + ", " + absCol + ")";
    }
}