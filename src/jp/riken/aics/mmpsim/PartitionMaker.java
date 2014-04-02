/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.hkhandan.util.ArrayTools;

public abstract class PartitionMaker {
    
    ///// NESTED DATATYPES ////////////////////////////////////////////////////
    
    public class ClusterParamSet {
        private int nRowDivs;
        private int nColDivs;
        private boolean isHeterogenous;
        private ComputerParamSet[] computerParamsets;
    }
    
    public class ComputerParamSet {
        private int nRowDivs;
        private int nColDivs;
        private NodeParamSet nodeParamset;
    }
    
    public class NodeParamSet {
        private int nRowDivs;
        private int nColDivs;
        private ProcessorParamSet[] processorParamsets;
    }
    
    public class ProcessorParamSet {
        private int nRowDivs;
        private int nColDivs;
        private int nBlockRows;
        private int nBlockCols;
        private boolean isDividedByBlock;
    }
    
    private enum DivisionBy {
        ROWS,
        COLS,
        AUTO
    };
    
    private enum OutcomeType {
        GRID_DIVISION,
        DIRECTIONAL_DIVISION
    };
    
    public static class Penalty {
        final float leftoverPenalty;
        final int   borderPenalty;

        public Penalty(float leftoverPenalty, int borderPenalty) {
            this.leftoverPenalty = leftoverPenalty;
            this.borderPenalty = borderPenalty;
        }
    };
    
    public class ParamToken {
        private boolean isBest = false;
        private String description = null;
        private float borderPenalty;
        private float leftoverPenalty;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public float getBorderPenalty() {
            return borderPenalty;
        }

        public void setBorderPenalty(float borderPenalty) {
            this.borderPenalty = borderPenalty;
        }

        public float getLeftoverPenalty() {
            return leftoverPenalty;
        }

        public void setLeftoverPenalty(float leftoverPenalty) {
            this.leftoverPenalty = leftoverPenalty;
        }
        
        public void setBest() {
            isBest = true;
        }
        
        public boolean isBest() {
            return isBest;
        }
    }
    
    
    ///// CONSTANTS ///////////////////////////////////////////////////////////

    public final static PartitionMaker HOMOGENOUS_NODES_WITH_GPU = new PartitionMaker(null) {
        class Paramset extends ParamToken {
            int clusterNRowDivs = -1;
            int clusterNColDivs = -1;
            boolean clusterIsHeterogenous = false;
            int computerNRowDivs = -1;
            int computerNColDivs = -1;
            DivisionBy processorDivisionDirection = null;
            int[] cpuNRowDivs = null;
            int[] cpuNColDivs = null;
            int[] gpuBlockNRows = null;
            int[] gpuBlockNCols = null;
            boolean isConsumed = false;
            
            public Paramset(Paramset p) {
                clusterNRowDivs = p.clusterNRowDivs;
                clusterNColDivs = p.clusterNColDivs;
                computerNRowDivs = p.computerNRowDivs;
                processorDivisionDirection = p.processorDivisionDirection;
                cpuNRowDivs = p.cpuNRowDivs;
                cpuNColDivs = p.cpuNColDivs;
                gpuBlockNRows = p.gpuBlockNRows;
                gpuBlockNCols = p.gpuBlockNCols;
                isConsumed = isConsumed;
            } 
        }
        
        @Override
        public PartitionMap[] allPartitionCandidates(GridDivision2D d, ClusterParams clusterParams) {            
            
            return null;
        }
        
        public ParamToken[] generateAllPermutations() {
            
        }
        
        private void generateAllPermutationsForHomogeneusCluster(
                ClusterParams clusterParams, 
                Paramset base, 
                ArrayList<Paramset> bag) 
        {
            final int nComputers = clusterParams.getNumberOfComputers();
            int[] nRowDivs = factorsOf(nComputers);
            int[] nColDivs = cofactorsOf(nComputers, nRowDivs);
            for(int i = 0; i < nRowDivs.length; i++) {
                Paramset pset = new Paramset(base);
                pset.clusterNRowDivs = nRowDivs[i];
                pset.clusterNColDivs = nColDivs[i];
                bag.add(pset);
            }
        }
        
        private void generateAllPermutationsForHeterogenousCluster(
                ClusterParams clusterParams,
                Paramset base,
                ArrayList<Paramset> bag) 
        {
            Paramset pset = new Paramset(base);
            pset.clusterNRowDivs = clusterParams.getNumberOfComputers();
            pset.clusterNColDivs = 1;
            pset.clusterIsHeterogenous = true;
            bag.add(pset);
            
            pset = new Paramset(pset);
            pset.clusterNRowDivs = 1;
            pset.clusterNColDivs = clusterParams.getNumberOfComputers();
            pset.clusterIsHeterogenous = true;
            bag.add(pset);
        }
        
        private void generateAllPermutationsForComputer(ComputerParams params)
    };
    
    /**
     * Stupid thing. For comparison only.
     */
    public final static PartitionMaker FIRST_DIMENSION_HOMOGENOUS = new PartitionMaker("First Dimension, Equal Portions") {
        public PartitionMap partition(GridDivision2D d, ClusterParams clusterParams) {
            StringBuilder description = new StringBuilder();
            
            PartitionMap cluster = new PartitionMap(d);
            
            GridDivision2D[] computerPartitions = divide1D(d,
                    clusterParams.getNumberOfComputers(), DivisionBy.ROWS);
            
            cluster.setSubdivisions(computerPartitions);
                        
            for(int i = cluster.getNumberOfSubPartitions() - 1; i >= 0; i--) {
                ComputerParams computerParams = clusterParams.getComputerParams(i);
                PartitionMap computer = cluster.getSubPartition(i);
                
                GridDivision2D[] nodeDivisions = divide1D(computer,
                        computerParams.getNumberOfNodes(), DivisionBy.ROWS);
                computer.setSubdivisions(nodeDivisions);
                
                ComputeNodeParams nodeParams = computerParams.getNodeParams();
                if(nodeParams.getNumberOfCpuGroups() != 1)
                    return null;
                
                int nCpus = nodeParams.getTotalCpus();

                description.append("(").append(nodeDivisions.length).append(" , 1) x (").append(nCpus).append(" x 1)");
                
                for(int j = computer.getNumberOfSubPartitions() - 1; j >= 0; j--) {
                    PartitionMap node = computer.getSubPartition(j);
                    GridDivision2D[] cpuGroupDivisions = new GridDivision2D[1];
                    cpuGroupDivisions[0] = node;
                    
                    node.setSubdivisions(cpuGroupDivisions);
                    
                    PartitionMap cpuGroup = node.getSubPartition(0);
                    
                    GridDivision2D[] cpuDivisions = divide1D(cpuGroup,
                            nCpus, DivisionBy.ROWS);
                    
                    cpuGroup.setSubdivisions(cpuDivisions);
                }

                if(i > 0) {
                    description.append(" + ");
                }
            }
            
            cluster.setDescription(description.toString());
            
            return cluster;
        }

        @Override
        public PartitionMap[] allPartitionCandidates(GridDivision2D d, ClusterParams clusterParams) {
            return new PartitionMap[]{partition(d, clusterParams)};
        }
    };

    public final static PartitionMaker FIRST_DIMENSION_PROPORTIONAL = new PartitionMaker("First Dimension, Proportional") {
        public PartitionMap partition(GridDivision2D d, ClusterParams clusterParams) {
            StringBuilder description = new StringBuilder();
            
            PartitionMap cluster = new PartitionMap(d);
            
            GridDivision2D[] computerPartitions = divide1D(d,
                    clusterParams.getComputerProportions(), DivisionBy.ROWS);
            
            cluster.setSubdivisions(computerPartitions);
            
            for(int i = cluster.getNumberOfSubPartitions() - 1; i >= 0; i--) {
                ComputerParams computerParams = clusterParams.getComputerParams(i);
                PartitionMap computer = cluster.getSubPartition(i);
                
                GridDivision2D[] nodeDivisions = divide1D(computerPartitions[i],
                        computerParams.getNumberOfNodes(), DivisionBy.ROWS);
                
                computer.setSubdivisions(nodeDivisions);
                
                ComputeNodeParams nodeParams = computerParams.getNodeParams();
                float[] cpuGroupProportions = nodeParams.getCpuGroupProportions();
                
                description.append("(").append(nodeDivisions.length).append(" x 1) x (").append(nodeParams.getTotalCpus()).append(" x 1)");
                
                for(int j = computer.getNumberOfSubPartitions() - 1; j >= 0; j--) {                    
                    PartitionMap node = computer.getSubPartition(j);
                    
                    GridDivision2D[] cpuGroupDivisions = divide1D(
                            node, cpuGroupProportions,
                            DivisionBy.ROWS);
                    
                    node.setSubdivisions(cpuGroupDivisions);

                    for(int k = node.getNumberOfSubPartitions() - 1; k >= 0; k--) {
                        PartitionMap cpuGroup = node.getSubPartition(k);
                        
                        GridDivision2D[] cpuDivisions = divide1D(
                                cpuGroup, 
                                nodeParams.getCpuGroupParams(k).getNCores(),
                                DivisionBy.ROWS);
                        
                        cpuGroup.setSubdivisions(cpuDivisions);
                    } // for(k)
                    
                } // for(j)
                
                if(i > 0) {
                    description.append(" + ");
                }
                
            } // for(i)
            
            cluster.setDescription(description.toString());
            
            return cluster;
        }

        @Override
        public PartitionMap[] allPartitionCandidates(GridDivision2D d, ClusterParams clusterParams) {
            return new PartitionMap[]{partition(d, clusterParams)};
        }
    };

    public final static PartitionMaker AUTO_DIMENSION_PROPORTIONAL = new PartitionMaker("Single Dimension, Autoselcet, Proportional") {

        private PartitionMap partition(GridDivision2D d, ClusterParams clusterParams, 
                DivisionBy dCluster, DivisionBy dComputer, DivisionBy dNode, 
                DivisionBy dCpuGroup) 
        {
            PartitionMap cluster = new PartitionMap(d);
            
            GridDivision2D[] computerPartitions = divide1D(d,
                    clusterParams.getComputerProportions(), dCluster);
            
            cluster.setSubdivisions(computerPartitions);
            
            for(int i = cluster.getNumberOfSubPartitions() - 1; i >= 0; i--) {
                ComputerParams computerParams = clusterParams.getComputerParams(i);
                PartitionMap computer = cluster.getSubPartition(i);
                
                GridDivision2D[] nodeDivisions = divide1D(computer,
                        computerParams.getNumberOfNodes(), dComputer);
                
                computer.setSubdivisions(nodeDivisions);

                ComputeNodeParams nodeParams = computerParams.getNodeParams();
                float[] cpuGroupProportions = nodeParams.getCpuGroupProportions();
                
                for(int j = computer.getNumberOfSubPartitions() - 1; j >= 0; j--) {
                    PartitionMap node = computer.getSubPartition(j);
                    
                    GridDivision2D[] cpuGroupDivisions
                            = divide1D(node, cpuGroupProportions, dNode);
                    
                    node.setSubdivisions(cpuGroupDivisions);
                    
                    for(int k = node.getNumberOfSubPartitions() - 1; k >= 0; k--) {
                        PartitionMap cpuGroup = node.getSubPartition(k);
                        
                        GridDivision2D[] cpuDivisions = divide1D(
                                cpuGroup, 
                                nodeParams.getCpuGroupParams(k).getNCores(),
                                dCpuGroup);
                        
                        cpuGroup.setSubdivisions(cpuDivisions);
                        
                    } // for(k)                    
                } // for(j)
            } // for(i)
            
            cluster.setDescription(dCluster + ", " + dComputer + ", " + dNode + ", " + dCpuGroup);
            
            return cluster;
        }
        
        @Override
        public PartitionMap[] allPartitionCandidates(GridDivision2D d, ClusterParams clusterParams) {
            final DivisionBy[] dValues = new DivisionBy[]{DivisionBy.ROWS, DivisionBy.COLS};
            
            ArrayList<PartitionMap> candidates = new ArrayList<PartitionMap>();
            
            for(DivisionBy dCluster : dValues) {
                if(clusterParams.getNumberOfComputers() == 1 && dCluster != DivisionBy.ROWS)
                    continue;
                
                for(DivisionBy dComputer : dValues) {
                    for(DivisionBy dNode : dValues) {
                        for(DivisionBy dCpuGroup : dValues) {
                            candidates.add(partition(d, clusterParams, dCluster, dComputer, dNode, dCpuGroup));
                        }
                    }
                }
            }
            
            return candidates.toArray(new PartitionMap[candidates.size()]);
        }
    };
    
    /**
     * All nodes the same
     * All CPUs are the same (only one CPU group per node)
     * All computers are the same
     */
    public final static PartitionMaker TWO_DIMENSIONS_HOMOGENOUS = new PartitionMaker("Two Dimensions, Homogenous System") {
        class ParamSet {
            int nClusterRowDivs = -1;
            int nClusterColDivs = -1;
            int nComputerRowDivs = -1;
            int nComputerColDivs = -1;
            int nNodeRowDivs = -1;
            int nNodeColDivs = -1;
            @Override
            public Object clone() {
                ParamSet ps = new ParamSet();
                ps.nClusterColDivs = nClusterColDivs;
                ps.nClusterRowDivs = nClusterRowDivs;
                ps.nComputerColDivs = nComputerColDivs;
                ps.nComputerRowDivs = nComputerRowDivs;
                ps.nNodeColDivs = nNodeColDivs;
                ps.nNodeRowDivs = nNodeRowDivs;
                return ps;
            }

            @Override
            public String toString() {
                return String.format("(%d, %d) x (%d, %d) x (%d, %d)", nClusterRowDivs, nClusterColDivs, nComputerRowDivs, nComputerColDivs, nNodeRowDivs, nNodeColDivs);
            }
        };
        
        @Override
        public PartitionMap[] allPartitionCandidates(GridDivision2D d, ClusterParams clusterParams) {
            List<PartitionMap> list = checkAndPartition(d, clusterParams, true);
            if(list == null)
                return null;
            return list.toArray(new PartitionMap[list.size()]);
        }
        
        private List<PartitionMap> checkAndPartition(GridDivision2D d,
                ClusterParams clusterParams, boolean allCandidates)
        {
            int nComputers = clusterParams.getNumberOfComputers();
            
            if(nComputers == 0)
                return null;
            
            ComputerParams computerParams = clusterParams.getComputerParams(0);
            int nNodes = computerParams.getNumberOfNodes();
            if(nNodes == 0)
                return null;
            
            ComputeNodeParams nodeParams = computerParams.getNodeParams();            
            int nCpus = nodeParams.getTotalCpus();
            
            for(int i = nComputers - 1; i >= 0; i--) {
                computerParams = clusterParams.getComputerParams(i);
                if(computerParams.getNumberOfNodes() != nNodes)
                    return null;
                nodeParams = computerParams.getNodeParams();
                if(nodeParams.getNumberOfCpuGroups() > 1)
                    return null;
                if(nodeParams.getTotalCpus() != nCpus)
                    return null;
            }
            
            return partition(d, nComputers, nNodes, nCpus, allCandidates);
        }
        
        private List<PartitionMap> partition(GridDivision2D d, int nComputers,
                int nNodes, int nCpus, boolean allCandidates)
        {
            ArrayList<PartitionMap> candidates = new ArrayList<PartitionMap>();
            
            int[] nComputersFactors = factorsOf(nComputers);
            int[] nNodesFactors = factorsOf(nNodes);
            int[] nCpusFactors = factorsOf(nCpus);
            
            ParamSet paramSet = new ParamSet();
            
            for(int i = 0; i < nComputersFactors.length; i++) {
                paramSet.nClusterRowDivs = nComputersFactors[i];
                paramSet.nClusterColDivs = nComputers/paramSet.nClusterRowDivs;
                for(int j = 0; j < nNodesFactors.length; j++) {
                    paramSet.nComputerRowDivs = nNodesFactors[j];
                    paramSet.nComputerColDivs = nNodes/paramSet.nComputerRowDivs;
                    for(int k = 0; k < nCpusFactors.length; k++) {
                        paramSet.nNodeRowDivs = nCpusFactors[k];
                        paramSet.nNodeColDivs = nCpus / paramSet.nNodeRowDivs;
                        
                        if(allCandidates) {
                            candidates.add(partition(d, paramSet));
                        }
                    } // for(k)
                } // for(j)
            } // for(i)
                        
            return candidates;
        }
        
        private PartitionMap partition(GridDivision2D d, ParamSet paramSet) {
            PartitionMap cluster = new PartitionMap(d);
            
            GridDivision2D[] computerDivisions = divide2D(d,
                    paramSet.nClusterRowDivs, paramSet.nClusterColDivs);
            
            cluster.setSubdivisions(computerDivisions);
            
            for(int i = cluster.getNumberOfSubPartitions() - 1; i >= 0; i--) {
                PartitionMap computer = cluster.getSubPartition(i);
                
                GridDivision2D[] nodeDivisions = divide2D(computer,
                        paramSet.nComputerRowDivs, paramSet.nComputerColDivs);
                
                computer.setSubdivisions(nodeDivisions);
                
                for(int j = computer.getNumberOfSubPartitions() - 1; j >= 0; j--) {
                    PartitionMap node = computer.getSubPartition(j);
                    
                    node.setSubdivisions(new GridDivision2D[]{node});
                    
                    PartitionMap cpuGroup = node.getSubPartition(0);
                    
                    GridDivision2D[] cpuDivisions = divide2D(cpuGroup,
                            paramSet.nNodeRowDivs, paramSet.nNodeColDivs);
                    
                    cpuGroup.setSubdivisions(cpuDivisions);
                }
            }
            
            cluster.setDescription(paramSet.toString());
            
            return cluster;
        }
    };
    
    /**
     * All nodes are the same
     * Computers have the same number of nodes;
     * Each node can have several CPU groups
     */
    public final static PartitionMaker TWO_DIMENTIONAL_HOMOGENOUS_NODES = new PartitionMaker("Two Dimensions, Homogenous Nodes") {
        class ParamSet {
            int nClusterRowDivs = -1;
            int nClusterColDivs = -1;
            int nComputerRowDivs = -1;
            int nComputerColDivs = -1;
            DivisionBy nodeDivisionMethod;
            int[] nCpuRowDivs = null;
            int[] nCpuColDivs = null;
            int[] nGpuRowsPerBlock = null;
            int[] nGpuColsPerBlock = null;
            
            @Override 
            public Object clone() {
                ParamSet cp = new ParamSet();
                cp.nClusterColDivs = nClusterColDivs;
                cp.nClusterRowDivs = nClusterRowDivs;
                cp.nComputerColDivs = nComputerColDivs;
                cp.nComputerRowDivs = nComputerRowDivs;
                cp.nodeDivisionMethod = nodeDivisionMethod;
                cp.nCpuColDivs = (int[])nCpuColDivs.clone();
                cp.nCpuRowDivs = (int[])nCpuRowDivs.clone();
                cp.nGpuRowsPerBlock = (int[])nGpuRowsPerBlock;
                cp.nGpuColsPerBlock = (int[])nGpuColsPerBlock;
                return cp;
            }
            
            @Override
            public String toString() {
                return String.format("(%d, %d) x (%d, %d) x %s (%s, %s)", nClusterRowDivs, nClusterColDivs, nComputerRowDivs, nComputerColDivs,nodeDivisionMethod.toString(), Arrays.toString(nCpuRowDivs), Arrays.toString(nCpuColDivs));
            }
        };
        
        @Override
        public PartitionMap[] allPartitionCandidates(GridDivision2D d, ClusterParams clusterParams) {
            List<PartitionMap> list = checkAndPartition(d, clusterParams, true);
            if(list == null)
                return null;
            return list.toArray(new PartitionMap[list.size()]);
        }
        
        public List<PartitionMap> checkAndPartition(GridDivision2D d,
                ClusterParams clusterParams, boolean allCandidates) 
        {
            int nComputers = clusterParams.getNumberOfComputers();
            if(nComputers == 0)
                return null;

            if(clusterParams.getComputerParams(0).getNumberOfNodes() == 0)
                return null;
            
            ComputeNodeParams nodeParams = clusterParams.getComputerParams(0).getNodeParams();
            
            int nNodes = clusterParams.getComputerParams(0).getNumberOfNodes();
            if(nNodes == 0)
                return null;
            
            for(int i = nComputers - 1; i >= 0; i--) {
                ComputerParams computerParams = clusterParams.getComputerParams(i);
                
                int nOtherNodes = computerParams.getNumberOfNodes();                
                if(nOtherNodes != nNodes)
                    return null;
                
                ComputeNodeParams otherNodeParams = computerParams.getNodeParams();
                
                if(nodeParams.getNumberOfCpuGroups() != otherNodeParams.getNumberOfCpuGroups())
                    return null;
                
                for(int j = otherNodeParams.getNumberOfCpuGroups() - 1; i >= 0; i--) {
                    if(!otherNodeParams.getCpuGroupParams(j).equals(nodeParams.getCpuGroupParams(j)))
                        return null;
                }
            }
            
            return partition(d, nComputers, nNodes,
                    nodeParams.getCpuGroupProportions(),
                    nodeParams.getNumberOfCpusPerGroup(), allCandidates);
        }
        

        private List<PartitionMap> partition(GridDivision2D d, int nComputers, 
                int nNodes, float[] cpuGroupProportions, int[] nCpusPerGroup,
                boolean allCandidates) 
        {                    
            ArrayList<PartitionMap> partitions = new ArrayList<PartitionMap>();
            
            int nRows = d.nRows;
            int nCols = d.nCols;
            
            int[] nComputersFactors = factorsOf(nComputers);
            int[] nNodesFactors = factorsOf(nNodes);
            
            ParamSet paramSet = new ParamSet();
            
            for(int i = nComputersFactors.length - 1; i >= 0; i--) {
                paramSet.nClusterRowDivs = nComputersFactors[i];
                paramSet.nClusterColDivs = nComputers/paramSet.nClusterRowDivs;
                
                for(int j = nNodesFactors.length - 1; j >= 0; j--) {
                    paramSet.nComputerRowDivs = nNodesFactors[j];
                    paramSet.nComputerColDivs = nNodes / paramSet.nComputerRowDivs;
                    
                    int rowDivs = paramSet.nClusterRowDivs * paramSet.nComputerRowDivs;
                    int colDivs = paramSet.nClusterColDivs * paramSet.nComputerColDivs;
                    
                    int level1Leftover = numeberOfLeftOvers(nRows, nCols, rowDivs, colDivs);
                    
                    int nRowsPerNode = nRows / rowDivs;
                    int nColsPerNode = nCols / colDivs;
                    
                    paramSet.nodeDivisionMethod = DivisionBy.ROWS;
                    int leftover = level1Leftover + leftoverPerNode(nRowsPerNode, nColsPerNode, nCpusPerGroup, cpuGroupProportions, DivisionBy.ROWS, paramSet);
                    
                    if(allCandidates) {
                        partitions.add(partition(d, cpuGroupProportions, paramSet));
                    }
                    
                    paramSet.nodeDivisionMethod = DivisionBy.COLS;
                    leftover = level1Leftover + leftoverPerNode(nRowsPerNode, nColsPerNode, nCpusPerGroup, cpuGroupProportions, DivisionBy.COLS, paramSet);

                    if(allCandidates) {
                        partitions.add(partition(d, cpuGroupProportions, paramSet));
                    }
                    
                } // for(j)
                
            } // for(i)
            
            return partitions;
        }
        
        private int leftoverPerNode(int nRows, int nCols, 
                int[] nCpusPerGroup, float[] cpuGroupProportions, 
                DivisionBy divideBy, ParamSet paramSet) 
        {
            int[] portionSizes;
            int leftover;
            
            paramSet.nodeDivisionMethod = divideBy;
            
            paramSet.nCpuColDivs = new int[nCpusPerGroup.length];
            paramSet.nCpuRowDivs = new int[nCpusPerGroup.length];
            
            if(divideBy == DivisionBy.ROWS) {
                portionSizes = portionSizes(nRows, cpuGroupProportions);
                leftover = (nRows - ArrayTools.sum(portionSizes)) * nCols;
                for(int i = nCpusPerGroup.length - 1; i >= 0; i--) {
                    leftover += leftoverPerCpuGroup(portionSizes[i], nCols, nCpusPerGroup[i], i, paramSet);
                }
            } else {
                portionSizes = portionSizes(nCols, cpuGroupProportions);
                leftover = (nCols - ArrayTools.sum(portionSizes)) * nRows;
                for(int i = nCpusPerGroup.length - 1; i >= 0; i--) {
                    leftover += leftoverPerCpuGroup(nRows, portionSizes[i], nCpusPerGroup[i], i, paramSet);
                }
            }

            return leftover;
        }
        
        private int leftoverPerCpuGroup(int nRows, int nCols, int nDivisions, 
                int index, ParamSet paramSet) 
        {
            int leastLeftover = Integer.MAX_VALUE;
            int bestNRowDivs = 1;
            
            int[] nDivisionsFactors = factorsOf(nDivisions);
            
            for(int i = nDivisionsFactors.length - 1; i >= 0; i--) {
                int nRowDivs = nDivisionsFactors[i];
                int nColDivs = nDivisions / nRowDivs;
                int leftovers = numeberOfLeftOvers(nRows, nCols, nRowDivs, nColDivs);
                if(leftovers < leastLeftover) {
                    leastLeftover = leftovers;
                    bestNRowDivs = nRowDivs;
                }
            }

            paramSet.nCpuRowDivs[index] = bestNRowDivs;
            paramSet.nCpuColDivs[index] = nDivisions / bestNRowDivs;
            
            return leastLeftover;
        }
        
        private PartitionMap partition(GridDivision2D d, float[] cpuGroupProportions,
                ParamSet paramSet) 
        {
            PartitionMap cluster = new PartitionMap(d);
            
            GridDivision2D[] subdivs = divide2D(d, paramSet.nClusterRowDivs, paramSet.nClusterColDivs);
            
            cluster.setSubdivisions(subdivs);
            
            for(int i = cluster.getNumberOfSubPartitions() - 1; i >= 0; i--) {
                PartitionMap computer = cluster.getSubPartition(i);
                subdivs = divide2D(computer, paramSet.nComputerRowDivs, paramSet.nComputerColDivs);
                
                computer.setSubdivisions(subdivs);
                
                for(int j = computer.getNumberOfSubPartitions() - 1; j >= 0; j--) {
                    PartitionMap node = computer.getSubPartition(j);
                    subdivs = divide1D(node, cpuGroupProportions, paramSet.nodeDivisionMethod);
                    
                    node.setSubdivisions(subdivs);
                    
                    for(int k = node.getNumberOfSubPartitions() - 1; k >= 0; k--) {
                        PartitionMap cpuGroup = node.getSubPartition(k);
                        subdivs = divide2D(cpuGroup, paramSet.nCpuRowDivs[k], paramSet.nCpuColDivs[k]);
                        cpuGroup.setSubdivisions(subdivs);
                    }
                }
            }
            
            cluster.setDescription(paramSet.toString());
            
            return cluster;
        }
    };
    
    public final static PartitionMaker TWO_DIMENSIONS_FREE_RECURSIVE = new PartitionMaker("Two Dimensions, Heterogenous System") {
        public PartitionMap partition(GridDivision2D d, ClusterParams clusterParams) {
            PartitionMap cluster = new PartitionMap(d);
            
            GridDivision2D[] subdivs = divide1D(cluster, clusterParams.getComputerProportions(), DivisionBy.AUTO);
            
            cluster.setSubdivisions(subdivs);
            
            for(int i = cluster.getNumberOfSubPartitions() - 1; i >= 0; i--) {
                PartitionMap computer = cluster.getSubPartition(i);
                ComputerParams computerParams = clusterParams.getComputerParams(i);
                
                GridDivision2D bounds = computer;
                int nRowDivs = findBestPartitioning(bounds.nRows, bounds.nCols, computerParams.getNumberOfNodes());
                int nColDivs = computerParams.getNumberOfNodes() / nRowDivs;
                
                subdivs = divide2D(bounds, nRowDivs, nColDivs);
                
                computer.setSubdivisions(subdivs);
                
                ComputeNodeParams nodeParams = computerParams.getNodeParams();
                
                for(int j = computer.getNumberOfSubPartitions() - 1; j >= 0; j--) {
                    PartitionMap node = computer.getSubPartition(j);
                    subdivs = divide1D(node, nodeParams.getCpuGroupProportions(), DivisionBy.AUTO);
                    
                    node.setSubdivisions(subdivs);
                    
                    for(int k = node.getNumberOfSubPartitions() - 1; k >= 0; k--) {
                        PartitionMap cpuGroup = node.getSubPartition(k);
                        ProcessorParams cpuGroupParams = nodeParams.getCpuGroupParams(k);
                        
                        bounds = cpuGroup;
                        nRowDivs = findBestPartitioning(bounds.nRows, bounds.nCols, cpuGroupParams.getNCores());
                        nColDivs = cpuGroupParams.getNCores() / nRowDivs;
                        
                        subdivs = divide2D(bounds, nRowDivs, nColDivs);
                        
                        cpuGroup.setSubdivisions(subdivs);
                    }
                }
            }
            cluster.setDescription("Auto");
            return cluster;
        }        

        @Override
        public PartitionMap[] allPartitionCandidates(GridDivision2D d, ClusterParams clusterParams) {
            return new PartitionMap[]{partition(d, clusterParams)};
        }
    };

    
    ///// FIELDS //////////////////////////////////////////////////////////////
    
    
    private final String name;
    
    
    ///// CLASS METHODS ///////////////////////////////////////////////////////
    
    
    // <editor-fold defaultstate="collapsed" desc="Supporting Functions">
    private static int numeberOfLeftOvers(int nRows, int nCols, int nRowDivs, int nColDivs) {
        int leftOverRows = nRows%nRowDivs;
        int leftOverCols = nCols%nColDivs; 
        int lo = leftOverRows*nCols + leftOverCols*nRows - leftOverRows*leftOverCols;
        // System.out.printf("[%d / %d, %d / %d] --> %d\n", nRows, nRowDivs, nCols, nColDivs, lo);
        return lo;
    }
    
    private static int[] factorsOf(int n) {
        ArrayList<Integer> factorsList = new ArrayList<Integer>();
        for(int i = 1; i <= n; i++) {
            if(n % i == 0)
                factorsList.add(i);
        }
        int[] factors = new int[factorsList.size()];
        for(int i = 0; i < factors.length; i++) {
            factors[i] = factorsList.get(i).intValue();
        }
        return factors;
    }
    
    private static int[] cofactorsOf(int n, int[] factors) {
        int[] cofactors = new int[factors.length];
        for(int i = 0; i < cofactors.length; i++) {
            cofactors[i] = n/factors[i];
        }
        return cofactors;
    }
    
    private static int findBiggestCommonDivisor(int a, int b) {
        if(b == 0)
            return a;
        return findBiggestCommonDivisor(b, a%b);
    }
    
    private static int findBiggestCommonDivisor(int[] numbers) {
        int c = numbers[numbers.length - 1];
        for(int i = numbers.length - 2; i >= 0; i--) {
            c = findBiggestCommonDivisor(c, numbers[i]);
        }
        return c;
    }
    
    private static int findBestPartitioning(int nRows, int nCols, int nDivisions) {
        int[] nDivisionsFactors = factorsOf(nDivisions);
        int leastLeftover = Integer.MAX_VALUE;
        int bestNRowDivs = 1;
        for(int i = nDivisionsFactors.length - 1; i >= 0; i--) {
            int nRowDivs = nDivisionsFactors[i];
            int nColDivs = nDivisions / nRowDivs;
            int leftover = numeberOfLeftOvers(nRows, nCols, nRowDivs, nColDivs);
            if(leftover < leastLeftover) {
                leastLeftover = leftover;
                bestNRowDivs = nRowDivs;
            }
        }
        return bestNRowDivs;
    }
    
    public static Penalty penaltyOf(ClusterParams params, PartitionMap map) {
        float leftOverPenalty = 0;
        int borderPenalty = 0;
        
        float timeFactors[] = new float[params.getTotalCpus()];
        float minTimeFactor = 0;
        int cpuCounter = 0;
        
        for(int computerRank = params.getNumberOfComputers() - 1; computerRank >= 0; computerRank--) {
            ComputerParams computerParams = params.getComputerParams(computerRank);
            PartitionMap computerMap = map.getSubPartition(computerRank);
            
            ComputeNodeParams nodeParams = computerParams.getNodeParams();
            
            for(int nodeRank = computerParams.getNumberOfNodes() - 1; nodeRank >= 0; nodeRank--) {
                PartitionMap nodeMap = computerMap.getSubPartition(nodeRank);
                borderPenalty += (nodeMap.nRows + nodeMap.nCols) * 2;
                
                for(int cpuGroupRank = nodeParams.getNumberOfCpuGroups() - 1; cpuGroupRank >= 0; cpuGroupRank--) {
                    PartitionMap cpuGroupMap = nodeMap.getSubPartition(cpuGroupRank);
                    ProcessorParams cpuGroupParams = nodeParams.getCpuGroupParams(cpuGroupRank);
                    
                    for(int cpuRank = cpuGroupParams.getNCores() - 1; cpuRank >= 0; cpuRank--) {
                        PartitionMap cpuMap = cpuGroupMap.getSubPartition(cpuRank);
                        timeFactors[cpuCounter] = cpuGroupParams.getSpeedPerCore() * cpuMap.getNCells();
                        if(cpuCounter == 0) {
                            minTimeFactor = timeFactors[cpuCounter];
                        } else {
                            minTimeFactor = Math.min(minTimeFactor, timeFactors[cpuCounter]);
                        }
                        
                        cpuCounter++;
                    }
                }
            }
        }
        
        for(float timeFactor : timeFactors) {
            leftOverPenalty += (timeFactor - minTimeFactor);
        }
        
        borderPenalty -= (map.nCols + map.nRows)*2;
        
        return new Penalty(leftOverPenalty, borderPenalty);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Fundamental Dividing Functions">
    private static GridDivision2D[] divide1D(GridDivision2D d, int nDivisions, DivisionBy divBy) {
        final int nRows = d.nRows;
        final int nCols = d.nCols;
        final int co = d.colOffset;
        final int ro = d.rowOffset;
        
        GridDivision2D[] divisions = new GridDivision2D[nDivisions];

        int rLeftovers = nRows % nDivisions;
        int cLeftovers = nCols % nDivisions;
        
        if( divBy == DivisionBy.ROWS || ((divBy == DivisionBy.AUTO) 
                && (rLeftovers * nCols < cLeftovers * nRows)) )
        {
            // Do rows
            int rowOffset = ro;
            for(int i = 0; i < nDivisions; i++) {
                int divRows = nRows / nDivisions;
                if(i < rLeftovers)
                    divRows++;
                divisions[i] = new GridDivision2D(rowOffset, co, divRows, nCols);
                rowOffset += divRows;
            }
            return divisions;
        } else {
            // Do cols
            int colOffset = co;
            for(int i = 0; i < nDivisions; i++) {
                int divCols = nCols / nDivisions;
                if(i < cLeftovers)
                    divCols++;
                divisions[i] = new GridDivision2D(ro, colOffset, nRows, divCols);
                colOffset += divCols;
            }
            return divisions;
        }
    }
    
    private static int[] portionSizes(int n, float[] proportions) {
        int[] portionSizes = new int[proportions.length];
        float total = ArrayTools.sum(proportions);
        for(int i = proportions.length - 1; i >= 0; i--) {
            float ratio = proportions[i]/total;
            portionSizes[i] = (int)(ratio * n);
        }
        return portionSizes;
    }        

    private static GridDivision2D[] divide1D(GridDivision2D d, float[] proportions, DivisionBy divBy) {
        final int nRows = d.nRows;
        final int nCols = d.nCols;
        final int co = d.colOffset;
        final int ro = d.rowOffset;
        
        GridDivision2D[] divisions = new GridDivision2D[proportions.length];
        
        int[] rPortionSizes = portionSizes(nRows, proportions);
        int[] cPortionSizes = portionSizes(nCols, proportions);
        int rLeftovers = nRows - ArrayTools.sum(rPortionSizes);
        int cLeftovers = nCols - ArrayTools.sum(cPortionSizes);
        
        if( divBy == DivisionBy.ROWS || ((divBy == DivisionBy.AUTO) 
                && (rLeftovers * nCols < cLeftovers * nRows)) )
        {
            // Do rows
            int rowOffset = ro;
            for(int i = proportions.length - 1; i >= 0; i--) {
                int divRows = rPortionSizes[i];
                if(i < rLeftovers)
                    divRows++;
                divisions[i] = new GridDivision2D(rowOffset, co, divRows, nCols);
                rowOffset += divRows;
            }
            return divisions;
        } else {
            // Do cols
            int colOffset = co;
            for(int i = proportions.length - 1; i >= 0; i--) {
                int divCols = cPortionSizes[i];
                if(i < cLeftovers)
                    divCols++;
                divisions[i] = new GridDivision2D(ro, colOffset, nRows, divCols);
                colOffset += divCols;
            }
            return divisions;
        }
    }
        
    private static GridDivision2D[] divide2D(GridDivision2D d, int nRowDivs,
            int nColDivs) 
    {
        int nRowsPerDivision = d.nRows / nRowDivs;
        int nColsPerDivision = d.nCols / nColDivs;

        int nRowsLeftOver = d.nRows % nRowDivs;
        int nColsLeftOver = d.nCols % nColDivs;

        List<GridDivision2D> subdivisions = new ArrayList<GridDivision2D>();

        int rowOffset = d.rowOffset;
        for(int i = 0; i < nRowDivs; i++) {
            int divisionRows = nRowsPerDivision;
            if(i < nRowsLeftOver)
                divisionRows++;
            int colOffset = d.colOffset;
            for(int j = 0; j < nColDivs; j++) {
                int divisionCols = nColsPerDivision;
                if(j < nColsLeftOver)
                    divisionCols++;
                subdivisions.add(new GridDivision2D(rowOffset, colOffset, divisionRows, divisionCols));
                colOffset += divisionCols;
            }
            rowOffset += divisionRows;
        }
        
        return subdivisions.toArray(new GridDivision2D[subdivisions.size()]);
    }
    
    public static PartitionMap[] getAllPossiblePartitionings(GridDivision2D root, int nDivisions) {
        final int[] nRowDivs = factorsOf(nDivisions);
        final int[] nColDivs = cofactorsOf(nDivisions, nRowDivs);
        final int nAlternatives = nRowDivs.length;
        
        PartitionMap[] allAlternatives = new PartitionMap[nAlternatives];
        for(int a = 0; a < nAlternatives; a++) {
            PartitionMap map = new PartitionMap(root);
            map.setSubdivisions(divide2D(map, nRowDivs[a], nColDivs[a]));
            allAlternatives[a] = map;
        }
        
        return allAlternatives;
    }
    
    public static PartitionMap[] getAllPossiblePartitionings(GridDivision2D root, int nDivisions[]) {
        int depth = nDivisions.length;
        int[][] nRowDivs = new int[depth][];
        int[][] nColDivs = new int[depth][];
        
        for(int d = 0; d < depth; d++) {
            nRowDivs[d] = factorsOf(nDivisions[d]);
            nColDivs[d] = cofactorsOf(nDivisions[d], nRowDivs[d]);
        }
        
        ArrayList<int[]> allPermutations = new ArrayList<int[]>();
        findAllPermutations(allPermutations, nRowDivs, new int[depth], 0);
        
        PartitionMap[] allAlternatives = new PartitionMap[allPermutations.size()];
        for(int i = allPermutations.size() - 1; i >= 0; i--) {
            PartitionMap map = new PartitionMap(root);
            divide(map, nRowDivs, nColDivs, allPermutations.get(i), 0);
        }
        
        return allAlternatives;
    }
    
    private static void findAllPermutations(ArrayList<int[]> bag, 
            int[][] nRowDivs, int[] address, int depth) 
    {
        if(depth == address.length) {
            bag.add(Arrays.copyOf(address, address.length));
            return;
        }
        
        for(int i = 0; i < nRowDivs[depth].length - 1; i++) {
            address[depth] = i;
            findAllPermutations(bag, nRowDivs, address, depth + 1);
        }
    }
    
    private static void divide(PartitionMap map, final int[][] nRowDivs, 
            final int[][] nColDivs, final int[] address, final int depth) 
    {
        if(depth == address.length)
            return;
        
        GridDivision2D[] subdivs = divide2D(map, nRowDivs[depth][address[depth]], nColDivs[depth][address[depth]]);
        map.setSubdivisions(subdivs);
        for(int i = map.getNumberOfSubPartitions() - 1; i >= 0; i--) {
            divide(map.getSubPartition(i), nRowDivs, nColDivs, address, depth + 1);
        }
    }
    
    protected static GridSize2D[] getAllPossibleBlockDimensions(int blockSize) {
        int[] rowSizes = factorsOf(blockSize);
        int[] colSizes = cofactorsOf(blockSize, rowSizes);
        GridSize2D[] dims = new GridSize2D[rowSizes.length];
        for(int i = 0; i < dims.length; i++) {
            dims[i] = new GridSize2D(rowSizes[i], colSizes[i]);
        }
        return dims;
    }
    
    public static int getBestBlockDimensionIndex(GridDivision2D d, GridSize2D[] candidates) {
        int bestIndex = -1;
        int minLeftover = Integer.MAX_VALUE;
        for(int i = 0; i < candidates.length; i++) {
            int nRowDivs = d.nRows/candidates[i].nRows;
            int nColDivs = d.nCols/candidates[i].nCols;
            int nFullyDividedRows = nRowDivs * candidates[i].nRows;
            int nFullyDividedCols = nColDivs * candidates[i].nCols;
            int nFullyDividedCells = nFullyDividedCols * nFullyDividedRows;
            int leftover = d.getNCells() - nFullyDividedCells;
            if(leftover < minLeftover) {
                bestIndex = i;
                minLeftover = leftover;
            }
        }
        
        return bestIndex;
    }
    
    public static GridDivision2D[] partitionByBlock(GridDivision2D d, GridSize2D blockDims) {
        int nRowDivs = d.nRows / blockDims.nRows;
        
        if(d.nRows % blockDims.nRows > 0) {
            nRowDivs++;
        }
        
        int nColDivs = d.nCols / blockDims.nCols;
        
        if(d.nCols % blockDims.nCols > 0) {
            nColDivs++;
        }
        
        ArrayList<GridDivision2D> subdivisions = new ArrayList<GridDivision2D>();
        
        for(int i = 0; i < nRowDivs; i++) {
            for(int j = 0; j < nColDivs; j++) {
                subdivisions.add(GridDivision2D.make(
                        i*blockDims.nRows, 
                        j*blockDims.nCols, 
                        Math.min((i+1)*blockDims.nRows - 1, d.nRows - 1), 
                        Math.min((j + 1)*blockDims.nCols - 1, d.nCols - 1)));
            }
        }
        
        return subdivisions.toArray(new GridDivision2D[subdivisions.size()]);
    }
    // </editor-fold>
    

    ///// CONSTRUCTOR /////////////////////////////////////////////////////////
    
    
    public PartitionMaker(String name) {
        this.name = name;
    }
    
    
    ///// OBJECT METHODS //////////////////////////////////////////////////////

    
    public abstract PartitionMap[] allPartitionCandidates(GridDivision2D d, ClusterParams clusterParams);
    
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    ///// MAIN ////////////////////////////////////////////////////////////////
    
    private static ClusterParams createHomogenousComputer(int cpuPerNode, int nNodes) {
        ProcessorParams cpuGroup = new ProcessorParams(cpuPerNode, 1.0f);
        
        ComputeNodeParams node = new ComputeNodeParams(0, 0, 0);
        node.addCpuGroup(cpuGroup);
        
        ComputerParams computer = new ComputerParams(node, nNodes, 0, 0, 0);
        
        ClusterParams cluster = new ClusterParams(0, 0, 0);
        cluster.addComputer(computer);
        
        return cluster;
    }

    private static ClusterParams createComputerWithMultipleCpuTypes(int nNodes, int[] nCpus, float[] speed) {
        if(nCpus.length != speed.length)
            throw new IllegalArgumentException("nCpus[] and speed[] do not agree in length.");
                
        ComputeNodeParams node = new ComputeNodeParams(0, 0, 0);
        for(int i = nCpus.length - 1; i >= 0; i--) {
            node.addCpuGroup(new ProcessorParams(nCpus[i], speed[i]));
        }
        
        ComputerParams computerParams = new ComputerParams(node, nNodes, 0, 0, 0);
        
        ClusterParams cluster = new ClusterParams(0, 0, 0);
        cluster.addComputer(computerParams);
        
        return cluster;
    }
    
    public static void main(String[] args) {
        ClusterParams params = createComputerWithMultipleCpuTypes(4, new int[]{4, 16}, new float[]{1f, 0.7f});
        //ClusterParams params = createHomogenousComputer(8, 5);
        GridDivision2D bounds = new GridDivision2D(91, 91);
        
        PartitionMap[] candidates = PartitionMaker.TWO_DIMENTIONAL_HOMOGENOUS_NODES.allPartitionCandidates(bounds, params);
        for(PartitionMap candidate : candidates) {
            System.out.println(candidate.getDescription());
            
            Penalty p = penaltyOf(params, candidate);
            System.out.println("  Leftover Penalty: " + p.leftoverPenalty);
            System.out.println("  Border Penalty: " + p.borderPenalty);
            System.out.println();
        }
    }
    
    ///// --hk ////////////////////////////////////////////////////////////////
}
