/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.util.ArrayList;

/**
 *
 * @author hamed
 */
public class SynchronizationMapMaker {
    private static class ConnectionTemplate {
        final CellLocation2D source;
        final CellLocation2D target;
        final Direction neighbor;
        final boolean bothWays;

        public ConnectionTemplate(CellLocation2D source, Direction neighbor, CellLocation2D target, boolean bothWays) {
            this.source = source;
            this.target = target;
            this.neighbor = neighbor;
            this.bothWays = bothWays;
        }
    }
    
    private final static CellLocation2D BARRIER = new CellLocation2D(0, 0);
    private final static CellLocation2D BOUNDARY_EXCHANGE_TRIGGER = new CellLocation2D(0, 0);
    
    private ArrayList<ConnectionTemplate> templates = new ArrayList<ConnectionTemplate>();
    
    public void addTemplateConnection(int fromRow, int fromCol, Direction neighbour, int toRow, int toCol) {
        addTemplateConnection(fromRow, fromCol, neighbour, toRow, toCol, false);
    }
    
    public void addTemplateBiConnection(int fromRow, int fromCol, Direction neighbour, int toRow, int toCol) {
        addTemplateConnection(fromRow, fromCol, neighbour, toRow, toCol, true);
    }
    
    private void addTemplateConnection(int fromRow, int fromCol, Direction neighbour, int toRow, int toCol, boolean bothWays) {
        templates.add(new ConnectionTemplate(
                new CellLocation2D(fromRow, fromCol), 
                neighbour, 
                new CellLocation2D(toRow, toCol),
                bothWays));
    }
    
    public void setCellToTriggerBarrier(int row, int col) {
        templates.add(new ConnectionTemplate(new CellLocation2D(row, col), null, BARRIER, false));
    }
    
    public void setCellToTriggerBoundaryExchange(int row, int col) {
        templates.add(new ConnectionTemplate(new CellLocation2D(row, col), null, BOUNDARY_EXCHANGE_TRIGGER, false));
    }
    
    public SynchronizationMap process(PartitionMap p) {
        SynchronizationMap syncMap = new SynchronizationMap();
        traversePartitions(syncMap, p);
        return syncMap;
    }
    
    private void traversePartitions(SynchronizationMap map, PartitionMap p) {
        if(p.getNumberOfSubPartitions() == 0) {
            for(ConnectionTemplate t : templates) {
                makeConnections(p, t.source, t.neighbor, t.target, t.bothWays, map);
            }
        }
        for(int i = p.getNumberOfSubPartitions() - 1; i >= 0; i--) {
            traversePartitions(map, p.getSubPartition(i));
        }
    }
    
    private void makeConnections(PartitionMap source, CellLocation2D from,
            Direction targetPos, CellLocation2D to, boolean bothWays,
            SynchronizationMap syncMap) 
    {
        int row = from.getRow();
        int col = from.getCol();
        
        int absSourceRow = (row >= 0)?source.rowOffset + row:source.getLastRow() + row + 1;
        int absSourceCol = (col >= 0)?source.colOffset + col:source.getLastCol() + col + 1;

        int targetRow = to.getRow();
        int targetCol = to.getCol();

        if(source.nRows == 0 || source.nCols == 0)
            return;
        
        if(targetPos == null) {
            WQCellAddress sourceLocator = new WQCellAddress(
                    new WQThreadAddress(source.getAddress()), 
                    row, col, absSourceRow, absSourceCol);
            
            if(to == BARRIER) {
                syncMap.add(sourceLocator, SynchronizationMap.BARRIER);
            } else if(to == BOUNDARY_EXCHANGE_TRIGGER) {
                syncMap.add(sourceLocator, SynchronizationMap.BOUNDARY_EXCHANGE_TRIGGER);
            }
            return;
        }
        
        PartitionMap[] neighbors = source.getNeighbors(targetPos);
        
        for(PartitionMap p:neighbors) {
            int rowOffset = 0;
            int lastRow   = 0;
            int colOffset = 0;
            int lastCol   = 0;
            switch(targetPos) {
                case ABOVE:
                case BELOW:
                    rowOffset = p.rowOffset;
                    lastRow = p.getLastRow();
                    colOffset = Math.max(source.colOffset, p.colOffset);
                    lastCol = Math.min(source.getLastCol(), p.getLastCol());
                    
                    absSourceCol = (col >= 0)?colOffset + col:lastCol + col + 1;
                    break;
                    
                case LEFT:
                case RIGHT:
                    rowOffset = Math.max(source.rowOffset, p.rowOffset);
                    lastRow = Math.min(source.getLastRow(), p.getLastRow());
                    colOffset = p.colOffset;
                    lastCol = p.getLastCol();
                    
                    absSourceRow = (row >= 0)?rowOffset + row:lastRow + row + 1;                    
                    break;
               
                case LOWER_RIGHT:
                    rowOffset = source.getLastRow() + 1;
                    colOffset = source.getLastCol() + 1;
                    lastRow = p.getLastRow();
                    lastCol = p.getLastCol();
                    break;
                    
                case LOWER_LEFT:
                    rowOffset = source.getLastRow() + 1;
                    lastRow = p.getLastRow();                    
                    colOffset = p.colOffset;
                    lastCol = source.colOffset - 1;
                    break;
                    
                case UPPER_RIGHT:
                    rowOffset = p.rowOffset;
                    lastRow = source.rowOffset - 1;
                    colOffset = source.getLastCol() + 1;
                    lastCol = p.getLastCol();
                    break;
                    
                case UPPER_LEFT:
                    rowOffset = p.rowOffset;
                    lastRow = source.rowOffset - 1;
                    colOffset = p.colOffset;
                    lastCol = source.colOffset - 1;
                    break;
            }
            
            int absTargetRow = (targetRow >= 0)?rowOffset + targetRow:lastRow + targetRow + 1;
            int absTargetCol = (targetCol >= 0)?colOffset + targetCol:lastCol + targetCol + 1;
            
            WQCellAddress sourceAddress = new WQCellAddress(
                    new WQThreadAddress(source.getAddress()),
                    absSourceRow - source.rowOffset,
                    absSourceCol - source.colOffset,
                    absSourceRow, absSourceCol);

            WQCellAddress targetAddress = new WQCellAddress(
                    new WQThreadAddress(p.getAddress()),
                    absTargetRow - p.rowOffset,
                    absTargetCol - p.colOffset,
                    absTargetRow, absTargetCol);                    
            
            syncMap.add(sourceAddress, targetAddress);
            if(bothWays) {
                syncMap.add(targetAddress, sourceAddress);
            }
        }        
    }
    
}