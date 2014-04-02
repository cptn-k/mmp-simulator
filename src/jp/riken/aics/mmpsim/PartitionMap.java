/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import net.hkhandan.util.TextTools;

/**
 *
 * @author hamed
 */
public class PartitionMap extends GridDivision2D implements Cloneable {
    private final int[] address;
    private PartitionMap[] subPartitions = null;
    private String description = null;
    private PartitionMap parent = null;
        
    public PartitionMap(int rowOffset, int colOffset, int nRows, int nCols) {
        super(rowOffset, colOffset, nRows, nCols);
        address = null;
    }
    
    public PartitionMap(GridDivision2D div) {
        super(div.rowOffset, div.colOffset, div.nRows, div.nCols);
        this.address = null;
    }
            
    private PartitionMap(int[] address, int rowOffset, int colOffset, int nRows, int nCols) {
        super(rowOffset, colOffset, nRows, nCols);
        this.address = address;
    }
    
    private PartitionMap(int[] address, GridDivision2D div) {
        super(div.rowOffset, div.colOffset, div.nRows, div.nCols);
        this.address = address;
    }

    public int getIndex() {
        return address[address.length - 1];
    }

    public void setSubdivisions(GridDivision2D[] divs) {
        subPartitions = new PartitionMap[divs.length];
        for(int i = divs.length - 1; i >= 0; i--) {
            int[] subAddress;
            
            if(address == null)
                subAddress = new int[1];
            else
                subAddress = Arrays.copyOf(address, address.length + 1);
            
            subAddress[subAddress.length - 1] = i;
            subPartitions[i] = new PartitionMap(subAddress, divs[i]);
            subPartitions[i].parent = this;
        }
    }
    
    public int getNumberOfSubPartitions() {
        if(subPartitions == null)
            return 0;
        return subPartitions.length;
    }
    
    public PartitionMap getSubPartition(int i) {
        return subPartitions[i];
    }

    public String getDescription() {
        if(description == null)
            return "";
        
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
    public PartitionMap getInnermostContainer(CellLocation2D target) {
        if(!contains(target))
            return null;
        
        if(subPartitions == null)
            return this;
        
        for(PartitionMap p : subPartitions) {
            PartitionMap res = p.getInnermostContainer(target);
            if(res != null)
                return res;
        }
        
        return null;
    }
    
    public int[] getAddress() {
        return address;
    }
    
    public PartitionMap[] getNeighbors(Direction pos) {
        ArrayList<PartitionMap> allNeighbors = new ArrayList<PartitionMap>();
        allNeighbors.addAll(traceRoot(this, pos, 0));
        return allNeighbors.toArray(new PartitionMap[allNeighbors.size()]);
    }
    
    private ArrayList<PartitionMap> traceRoot(GridDivision2D div, Direction pos, int level) {
        if(parent == null) {
            return getNeighbors(div, pos, level);
        } else {
            return parent.traceRoot(div, pos, level + 1);
        }
    }
    
    private ArrayList<PartitionMap> getNeighbors(GridDivision2D div, Direction pos, int level) {
        if(level > 1) {
            ArrayList<PartitionMap> allPartitions = new ArrayList<PartitionMap>();
            for(PartitionMap p:subPartitions) {
                if(isAdjecentTo(p, pos, div) || p.contains(div)) {
                    ArrayList<PartitionMap> partialResult = p.getNeighbors(div, pos, level - 1);
                    allPartitions.addAll(partialResult);
                }
            }
            return allPartitions;
        } else {
            ArrayList<PartitionMap> allPartitions = new ArrayList<PartitionMap>();
            for(PartitionMap p:subPartitions) {
                if(isAdjecentTo(p, pos, div)) {
                    allPartitions.add(p);                    
                }
            }
            return allPartitions;
        }
    }
    
    private static boolean isAdjecentTo(GridDivision2D div1, Direction pos, GridDivision2D div2) {
        switch(pos) {
            case ABOVE:
                return (div2.rowOffset == div1.getLastRow() + 1)
                        && (isInRange(div2.colOffset, div1.colOffset, div1.getLastCol())
                            || isInRange(div1.colOffset, div2.colOffset, div2.getLastCol()));

            case BELOW:
                return isAdjecentTo(div2, Direction.ABOVE, div1);
                
            case LEFT:
                return (div1.getLastCol() + 1 == div2.colOffset)
                        && (isInRange(div1.rowOffset, div2.rowOffset, div2.getLastRow()) 
                            || isInRange(div2.rowOffset, div1.rowOffset, div1.getLastRow()));
                
            case RIGHT:
                return isAdjecentTo(div2, Direction.LEFT, div1);
                
            case UPPER_RIGHT:
                return (isInRange(div2.rowOffset - 1, div1.rowOffset, div1.getLastRow())
                        && isInRange(div2.getLastCol() + 1, div1.colOffset, div1.getLastCol()));
                        
                                
            case LOWER_LEFT:
                return (isInRange(div2.getLastRow() + 1, div1.rowOffset, div1.getLastRow())
                        && isInRange(div2.colOffset - 1, div1.colOffset, div1.getLastCol()));
                
            case UPPER_LEFT:
                return (isInRange(div2.rowOffset - 1, div1.rowOffset, div1.getLastRow())
                        && isInRange(div2.colOffset - 1, div1.colOffset, div1.getLastCol()));
                
            case LOWER_RIGHT:
                return (isInRange(div2.getLastRow() + 1, div1.rowOffset, div1.getLastRow())
                        && isInRange(div2.getLastCol() + 1, div1.colOffset, div1.getLastCol()));
        }
        
        return false;
    }
    
    private static boolean isInRange(int n, int a, int b) {
        return n >= a && n <= b;
    }
    
    @Override
    public Object clone() {
        PartitionMap clone = new PartitionMap(address, rowOffset, colOffset, nRows, nCols);
        clone.description = description;
        clone.subPartitions = new PartitionMap[subPartitions.length];
        for(int i = 0; i < subPartitions.length; i++) {
            clone.subPartitions[i] = (PartitionMap)subPartitions[i].clone();
            clone.subPartitions[i].parent = clone;
        }
        return clone;
    }
    
    public int getSizeInBits() {
        if(subPartitions == null) {
            return Integer.SIZE * 4;
        }
        int sum = 0;
        for(PartitionMap p : subPartitions) {
            sum += p.getSizeInBits();
        }
        return sum + 64;
    }
    
    public void printPartitionMap(PrintStream os) {
        printPartitionMap(os, 0);
    }
    
    private void printPartitionMap(PrintStream os, int indent) {
        for(int i = 0; i < indent; i++) {
            os.print(' ');
        }
        
        os.println(super.toString());
        
        if(subPartitions != null) {
            for(PartitionMap p:subPartitions) {
                p.printPartitionMap(os, indent + 4);
            }
        }
    }
    
    public String dump() {
        StringBuilder builder = new StringBuilder();
        builder.append(Arrays.toString(address)).append(' ').append(description).append('\n');
        if(subPartitions != null) {
            for(PartitionMap p : subPartitions) {
                builder.append(TextTools.indent(p.dump(), 2)).append("\n");
            }
        }
        return builder.toString();
    }
    
    @Override
    public String toString() {
        if(description != null)
            return description;
        return super.toString();
    }
}
