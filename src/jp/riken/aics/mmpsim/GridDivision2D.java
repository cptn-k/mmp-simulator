/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

/**
 *
 * @author hamed
 */
public class GridDivision2D  {
    public static interface ToRun {
        public void run(int row, int col, int absRow, int absCol, Object[] params);
    }
    
    private int rowOffset;
    private int colOffset;
    private int nRows;
    private int nCols;

    public GridDivision2D(int rowOffset, int colOffset, int nRows, int nCols) {
        this.rowOffset = rowOffset;
        this.colOffset = colOffset;
        this.nRows = nRows;
        this.nCols = nCols;
    }
    
    public GridDivision2D(int nRows, int nCols) {
        this(0, 0, nRows, nCols);
    }

    public int getRowOffset() {
        return rowOffset;
    }

    public int getColOffset() {
        return colOffset;
    }

    public int getNRows() {
        return nRows;
    }

    public int getNCols() {
        return nCols;
    }
    
    public int getLastRow() {
        return rowOffset + nRows - 1;
    }
    
    public int getLastCol() {
        return colOffset + nCols - 1;
    }
    
    public boolean isEmpty() {
        return nRows == 0 || nCols == 0;
    }
    
    public boolean contains(int row, int col) {
        return col >= colOffset && col < colOffset + nCols
                && row >= rowOffset && row < rowOffset + nRows;
    }
    
    boolean contains(CellLocation2D target) {
        return contains(target.getRow(), target.getCol());
    }
    
    public boolean contains(GridDivision2D div) {
        return div.colOffset >= colOffset
                && div.rowOffset >= rowOffset
                && div.getLastRow() <= getLastRow()
                && div.getLastCol() <= getLastCol();
    }
    
    public int getNCells() {
        return nRows * nCols;
    }
    
    public GridDivision2D getContactRange(GridDivision2D p1, Direction relativePosition, int borderSize) {
        if(borderSize <= 0)
            return new GridDivision2D(0, 0, 0, 0);
        
        // p1 is target, p2 is this
        int firstRow = -1;
        int firstCol = -1;
        int lastRow  = -1;
        int lastCol  = -1;

        switch(relativePosition) {
            case LEFT:
            case RIGHT:
                firstRow = Math.max(p1.rowOffset, rowOffset);
                lastRow  = Math.min(p1.getLastRow(), getLastRow());
                break;

            case ABOVE:
            case UPPER_RIGHT:
            case UPPER_LEFT:
                firstRow = Math.max(rowOffset, p1.rowOffset);
                lastRow  = firstRow + borderSize - 1;
                break;

            case BELOW:
            case LOWER_RIGHT:
            case LOWER_LEFT:
                lastRow  = Math.min(getLastRow(), p1.getLastRow());
                firstRow = lastRow - borderSize + 1;
                break;                    
        }

        switch(relativePosition) {
            case LEFT:
            case UPPER_LEFT:
            case LOWER_LEFT:
                firstCol = Math.max(p1.colOffset, colOffset);
                lastCol  = firstCol + borderSize - 1;
                break;

            case RIGHT:
            case UPPER_RIGHT:
            case LOWER_RIGHT:
                lastCol  = Math.min(getLastCol(), p1.getLastCol());
                firstCol = lastCol - borderSize + 1;
                break;

            case ABOVE:
            case BELOW:
                firstCol = Math.max(p1.colOffset, colOffset);
                lastCol  = Math.min(p1.getLastCol(), getLastCol());
                break;                    
        }

        return new GridDivision2D(firstRow, firstCol, 
                lastRow - firstRow + 1, lastCol - firstCol + 1);
    }
    
    public GridDivision2D mirror(Direction d) {
        int firstRow = -1;
        int firstCol = -1;
        int lastRow  = -1;
        int lastCol  = -1;
        
        switch(d) {
            case ABOVE:
            case UPPER_RIGHT:
            case UPPER_LEFT:
                lastRow = rowOffset - 1;
                firstRow = lastRow - nRows + 1;
                break;
                
            case BELOW:
            case LOWER_LEFT:
            case LOWER_RIGHT:
                firstRow = getLastRow() + 1;
                lastRow = firstRow + nRows - 1;
                break;
                
            case LEFT:
            case RIGHT:
                firstRow = rowOffset;
                lastRow = getLastRow();
                break;
        }
        
        switch(d) {
            case LEFT:
            case UPPER_LEFT:
            case LOWER_LEFT:
                lastCol = colOffset - 1;
                firstCol = lastCol - nCols + 1;
                break;
                
            case RIGHT:
            case UPPER_RIGHT:
            case LOWER_RIGHT:
                firstCol = getLastCol() + 1;
                lastCol = firstCol + nCols - 1;
                break;
                
            case ABOVE:
            case BELOW:
                firstCol = colOffset;
                lastCol = getLastCol();
        }
        
        return new GridDivision2D(firstRow, firstCol, 
                lastRow - firstRow + 1, lastCol - firstCol + 1);        
    }
    
    public GridDivision2D getBorder(Direction d, int thickness) {
        if(thickness <= 0)
            return new GridDivision2D(0, 0, 0, 0);
        
        // p1 is target, p2 is this
        int firstRow = -1;
        int firstCol = -1;
        int lastRow  = -1;
        int lastCol  = -1;

        switch(d) {
            case LEFT:
            case RIGHT:
                firstRow = rowOffset;
                lastRow  = getLastRow();
                break;

            case ABOVE:
            case UPPER_RIGHT:
            case UPPER_LEFT:
                firstRow = rowOffset;
                lastRow  = rowOffset + thickness - 1;
                break;

            case BELOW:
            case LOWER_RIGHT:
            case LOWER_LEFT:
                lastRow  = getLastRow();
                firstRow = lastRow - thickness + 1;
                break;                    
        }

        switch(d) {
            case LEFT:
            case UPPER_LEFT:
            case LOWER_LEFT:
                firstCol = colOffset;
                lastCol  = colOffset + thickness - 1;
                break;

            case RIGHT:
            case UPPER_RIGHT:
            case LOWER_RIGHT:
                lastCol  = getLastCol();
                firstCol = lastCol - thickness + 1;
                break;

            case ABOVE:
            case BELOW:
                firstCol = colOffset;
                lastCol  = getLastCol();
                break;                    
        }

        return new GridDivision2D(firstRow, firstCol, 
                lastRow - firstRow + 1, lastCol - firstCol + 1);    
    }
    
    public GridDivision2D grow(int n) {
        return new GridDivision2D(rowOffset - n, colOffset - n, nRows + n*2, nCols + n*2);
    }
    
    public GridDivision2D intersect(GridDivision2D other) {
        int firstRow = Math.max(rowOffset, other.rowOffset);
        int lastRow = Math.min(getLastRow(), other.getLastRow());
        int firstCol = Math.max(colOffset, other.colOffset);
        int lastCol = Math.min(getLastCol(), other.getLastCol());
        
        return new GridDivision2D(firstRow, firstCol, lastRow - firstRow + 1, lastCol - firstCol + 1);
    }
    
    public void iterateAllCells(ToRun toRun, final Object... params) {
        for(int row = 0; row < nRows; row++) {
            for(int col = 0; col < nCols; col++) {
                toRun.run(row, col, row + rowOffset, col + colOffset, params);
            }
        }
    }
    
    @Override
    public String toString() {
        return String.format("[%d~%d, %d~%d]",
                rowOffset, getLastRow(), colOffset, getLastCol());
    }
}
