/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

/**
 *
 * @author hamed
 */
public class SimulationScenario implements Cloneable {
    private final static SynchronizationMapMaker DEFAULT_SYNCHRONIZATION_MAP_MAKER = 
            new SynchronizationMapMaker();
    
    private String name = "";
    private ClusterParams clusterParams;
    private CellExecutionManager cman;
    private PartitionMaker divider;
    private int ghostBoundarySize = 0;
    private int nRows = 50;
    private int nCols = 50;
    private int nTimeSteps = 10;

    public SimulationScenario(ClusterParams clusterParams, CellExecutionManager cman, PartitionMaker divider) {
        this.clusterParams = clusterParams;
        this.cman = cman;
        this.divider = divider;
    }

    public String getName() {
        return name;
    }

    public SimulationScenario setName(String name) {
        this.name = name;
        return this;
    }

    public ClusterParams getClusterParams() {
        return clusterParams;
    }

    public SimulationScenario setClusterParams(ClusterParams clusterParams) {
        this.clusterParams = clusterParams;
        return this;
    }

    public CellExecutionManager getCellExecutionManager() {
        return cman;
    }

    public SimulationScenario setCellExecutionManager(CellExecutionManager cman) {
        this.cman = cman;
        return this;
    }

    public PartitionMaker getPartitionMaker() {
        return divider;
    }

    public SimulationScenario setPartitionMaker(PartitionMaker divider) {
        this.divider = divider;
        return this;
    }
    
    public int getGhostBoundarySize() {
        return ghostBoundarySize;
    }

    public SimulationScenario setGhostBoundarySize(int s) {
        this.ghostBoundarySize = s;
        return this;
    }
    
    public int getNRows() {
        return nRows;
    }

    public SimulationScenario setNRows(int nRows) {
        this.nRows = nRows;
        return this;
    }

    public int getNCols() {
        return nCols;
    }

    public SimulationScenario setNCols(int nCols) {
        this.nCols = nCols;
        return this;
    }

    public int getNTimeSteps() {
        return nTimeSteps;
    }

    public SimulationScenario setNTimeSteps(int nTimeSteps) {
        this.nTimeSteps = nTimeSteps;
        return this;
    }
    
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new Error("This shouldn't happen.");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
