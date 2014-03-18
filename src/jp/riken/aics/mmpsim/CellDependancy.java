/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

/**
 *
 * @author hamed
 */
public enum CellDependancy {
    INDEPENDANT("Independant"),
    CROSS("Cross"),
    FIRST_NEIGHBORS("First neghbors");
    
    public static interface ToRun {
        public void run(int row, int col);
    }
    
    final public String description;
    
    CellDependancy(String desc) {
        this.description = desc;
    }
    
    public void iterateAround(CellLocation2D loc, ToRun toRun) {
        iterateAround(loc.getRow(), loc.getCol(), toRun);
    }
    
    public void iterateAround(int row, int col, ToRun toRun) {
        switch(this) {
            case FIRST_NEIGHBORS:
                toRun.run(row - 1, col - 1);
                toRun.run(row + 1, col + 1);
                toRun.run(row - 1, col + 1);
                toRun.run(row + 1, col - 1);

            case CROSS:
                toRun.run(row - 1, col);
                toRun.run(row + 1, col);
                toRun.run(row, col - 1);
                toRun.run(row, col + 1);

            default:
                // nothing
        }
    }
    
    @Override
    public String toString() {
        return description;
    }
}
