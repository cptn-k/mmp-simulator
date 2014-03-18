/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jp.riken.aics.mmpsim;

public interface CellInfo {
    public WQCellAddress getAddress();
    public int getTimeStep();
    public boolean isAtBarrier();
    public Direction[] getBoundaryExchangeTargets();
    public int getExecutingProcessorUnitId();
}
