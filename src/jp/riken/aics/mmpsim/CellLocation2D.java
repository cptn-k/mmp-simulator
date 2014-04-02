/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jp.riken.aics.mmpsim;

import net.hkhandan.math.IntPair;

/**
 *
 * @author hamed
 */
public class CellLocation2D {
    public final static int SIZE_IN_BITS = Integer.SIZE * 2;
    
    private final int row;
    private final int col;
    
    public CellLocation2D(int row, int col) {
        this.row = row;
        this.col = col;
    }
    
    public CellLocation2D(IntPair intPair) {
        this(intPair.y, intPair.x);
    }
    
    public int getRow() {
        return row;
    }
    
    public int getCol() {
        return col;
    }
    
    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof CellLocation2D))
            return false;
        CellLocation2D l = (CellLocation2D)obj;
        return l.row == row && l.col == col;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 23 * hash + this.row;
        hash = 23 * hash + this.col;
        return hash;
    }
    
    @Override
    public String toString() {
        return String.format("[%d, %d]", row, col);
    }
}
