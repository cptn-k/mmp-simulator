
package jp.riken.aics.mmpsim;

import java.awt.Color;
import java.util.ArrayList;
import jp.riken.aics.mmpsim.SimulationScenario.*;
import jp.riken.aics.mmpsim.SimulatorUIForm.CellState;
import jp.riken.aics.mmpsim.std.BarrierCellExecutionManager;
import jp.riken.aics.mmpsim.std.DecentralizedCellExecutionManager;
import jp.riken.aics.mmpsim.std.IndependentCellExecutionManager;
import jp.riken.aics.mmpsim.std.SpiralCellExecutionManager;

public class Simulator {
    private class SamplingJob implements Runnable {
        @Override
        public void run() {
            timeSamples[sampleCounter]           = model.currentTime();
            progressSamples[sampleCounter]       = model.getProgress();
            computeTimeSamples[sampleCounter]    = model.getTotalComputeTime();
            waitingTimeSamples[sampleCounter]    = model.getTotalWaitingTime();
            networkTrafficSamples[sampleCounter] = model.getTotalNeworkTrafficTime();

            ui.setProgress(progressSamples[sampleCounter]);
            
            sampleCounter++;
            
            ui.putPlot(SimulatorUIForm.CurveName.PROGRESS            , runCounter, timeSamples, progressSamples      , sampleCounter);
            ui.putPlot(SimulatorUIForm.CurveName.COMPUTE_TIME        , runCounter, timeSamples, computeTimeSamples   , sampleCounter);
            ui.putPlot(SimulatorUIForm.CurveName.IDLE_TIME           , runCounter, timeSamples, waitingTimeSamples   , sampleCounter);
            ui.putPlot(SimulatorUIForm.CurveName.NETWORK_TRAFFIC_TIME, runCounter, timeSamples, networkTrafficSamples, sampleCounter);            
            
            samplingScheduler.runWithDelay(new SamplingJob(), 50);
        }        
    }
    
    private final static float MEMORY_GAP               = 0.0001f;
    private final static float MEMORY_LATENCY           = 0f;
    private final static float MEMORY_OVERHEAD          = 0f;
    private final static float INTERCONNECT_GAP         = 0.003f;
    private final static float INTERCONNECT_LATENCY     = 0.003f;
    private final static float INTERCONNECT_OVERHEAD    = 0f;
    private final static float NETWORK_GAP              = 0f;
    private final static float NETWORK_LATENCY          = 0f;
    private final static float NETWORK_OVERHEAD         = 0f;
    private final static float CPU_SPEED                = 1;
    private final static int   LINKED_VARIABLES_SIZE    = 128;
    
    private SimulationModel model;
    private SimulatorUIForm ui;
    private int nTimeSteps = 4;
    private ArrayList<PartitionMaker> partitionMakers = new ArrayList<PartitionMaker>();
    private ArrayList<SimulationScenario> scenarios = new ArrayList<SimulationScenario>();
    private ArrayList<CellExecutionManager> executionManagers = new ArrayList<CellExecutionManager>();
    private int selectedPartitioningAlgorithmIndex = 0;
    private int selectedExecutionManagerIndex = 0;
    private SimulationScenario scenario;
    private double  speedFactor = 1;
    
    private int runCounter = -1;
    private int sampleCounter = 0;
    private final double[] timeSamples           = new double[2000];
    private final double[] networkTrafficSamples = new double[2000];
    private final double[] computeTimeSamples    = new double[2000];
    private final double[] waitingTimeSamples    = new double[2000];
    private final double[] progressSamples       = new double[2000];
    private Scheduler samplingScheduler = new Scheduler();
    
    private CellExecutionManager independantExeman;
    private CellExecutionManager barrierExeman;
    private CellExecutionManager cascadingExeman;
    private CellExecutionManager spiralCascadingExeman;
    
    private final SimulationModel.ModelCallbakcs modelCallbakcs 
            = new SimulationModel.ModelCallbakcs() 
    {
        @Override
        public void cellChanged(CellInfo cell) {
            CellState state = CellState.IDLE;
            
            if(model == null)
                state = CellState.INIT;
            else if(model.isStopped())
                state = CellState.INIT;
            else if(cell.getExecutingProcessorUnitId() >= 0)
                state = CellState.ACTIVE;
            
            float ratio = ((float)cell.getTimeStep())/((float)nTimeSteps + 1);
            ui.updateCell(cell.getAddress().getAbsRow(), cell.getAddress().getAbsCol(), 
                    ratio, state);
        }

        @Override
        public void cellBlocked(CellInfo cell) {
            ui.updateCell(cell.getAddress().getAbsRow(), cell.getAddress().getAbsCol(),
                    0, CellState.BLOCKED);
        }
        
        @Override
        public void cellPendingIO(CellInfo cell) {
//            ui.updateCell(cell.getAddress().getAbsRow(), cell.getAddress().getAbsCol(),
//                    0, CellState.IO);
        }

        @Override
        public void cellReleased(CellInfo cell) {
            // nothing
        }

        @Override
        public void threadFinishedTimeStep(WQThreadAddress loc, int timeStep) {
            // Nothing
        }

        @Override
        public void threadFinished(WQThreadAddress loc) {
            // Nothing
        }

        @Override
        public void modelRefreshed(SimulationModel model) {
            int nRows = model.getNRows();
            int nCols = model.getNCols();
            ui.clear();
            drawPartition(model.getSelectedPartitionMap(), 0);
            for(int i = nRows - 1; i >= 0; i--) {
                for(int j = nCols - 1; j >= 0; j--) {
                    CellInfo cell = model.getCell(i, j);
                    cellChanged(cell);                    
                }
            }
        }

        @Override
        public void jobComplete() {
            ui.showMessage(String.format("Job complete. Virtual elapsed time: %.2fms", model.currentTime()));
            ui.setState(SimulatorUIForm.State.STOPPED);
            samplingScheduler.stop();
        }

        @Override
        public void partitionMakerRejected(PartitionMaker pm) {
            ui.showMessage("Partitioning Method Rejected.");
        }

        @Override
        public void message(String msg, boolean fatal) {
            ui.showMessage(msg);
            if(fatal) {
                model.stop();
                ui.setState(SimulatorUIForm.State.STOPPED);
            }
        }

        @Override
        public void fault(CellInfo cell, String message) {
            ui.updateCell(cell.getAddress().getAbsRow(), cell.getAddress().getAbsCol(),
                    0, CellState.FAULT);
            ui.showMessage(cell.getAddress() + ": " + message);
            model.pause();
        }
    };
    
    private final SimulatorUIForm.ActionListener uiActionListener
            = new SimulatorUIForm.ActionListener() 
    {
        @Override
        public void scenarioSelected(int index) {
            setScenario(index);
        }

        @Override
        public void partitioningMethodSelected(int index) {
            selectedPartitioningAlgorithmIndex = index;
            model.alterPartitioning(partitionMakers.get(index));
            ui.setAlternativePartitionings(model.getCandidatePartitionMaps());
        }

        @Override
        public void alternativePartitioningSelected(int index) {
            model.setSelectedPartitionIndex(index);
        }

        @Override
        public void dimensionsChanged(int nRows, int nCols) {
            model.alterSize(nRows, nCols);            
        }

        @Override
        public void synchroinzationSchemeSelected(int index) {
            selectedExecutionManagerIndex = index;
            model.alterExecutionManager(executionManagers.get(index));
        }

        @Override
        public void boundryBufferingSettingsChanged(boolean enabled) {
            model.alterBorderProxyThinkness(enabled?1:0);
        }

        @Override
        public void speedChanged(int speed) {
            if(speed < 50) {
                speedFactor = ((double)speed) * 0.9 / (50) + 0.1;
            } else {
                speedFactor = ((double)speed - 50) * 6 / (50) + 1;
            }
            model.setSpeedFactor(speedFactor);
        }

        @Override
        public void start() {
            StringBuilder message = new StringBuilder();
            message.append("Simulation started.\n")
                    .append("    Scenario name: ").append(scenario.getName()).append('\n')
                    .append("    Grid dimensions: ").append(scenario.getNRows()).append(" rows x ").append(scenario.getNCols()).append(" cols\n")
                    .append("    Partitioning method: ").append(partitionMakers.get(selectedPartitioningAlgorithmIndex)).append("\n")
                    .append("    Selected Partitioning: ").append(model.getSelectedPartitionMap().getDescription()).append("\n")
                    .append("    Ghost boundary size: ").append(scenario.getGhostBoundarySize()).append("\n")
                    .append("    Number of iterations: ").append(scenario.getCellExecutionManager().getNTimeSteps());
            ui.showMessage(message.toString());
            runCounter = (runCounter + 1)%10;
            model.run();
            samplingScheduler.start();
            sampleCounter = 0;
            samplingScheduler.runWithDelay(new SamplingJob(), 10);
        }

        @Override
        public void pause() {
            model.pause();
            samplingScheduler.pause();
            ui.showMessage("Simulation paused.");
        }

        @Override
        public void resume() {
            model.resume();
            samplingScheduler.resume();
            ui.showMessage("Simulation resumed.");
        }

        @Override
        public void reset() {
            model.stop();
            samplingScheduler.stop();
            ui.showMessage("Simulation stopped.");
        }

        @Override
        public void cellSelected(int row, int col) {
            if(model.toggleProbe(row, col)) {
                ui.hiliteCell(row, col, Color.BLUE);
            } else {
                ui.removeCellHitite(row, col);
            }
        }
    };
    
    
    // <editor-fold defaultstate="collapsed" desc="ClusterParams ceation methods">     
    private static ClusterParams createHomogenousComputer(int cpuPerNode, int nNodes) {
        ProcessorParams cpuGroup = new ProcessorParams(cpuPerNode, CPU_SPEED);
        
        ComputeNodeParams node = new ComputeNodeParams(
                MEMORY_GAP, MEMORY_LATENCY, MEMORY_OVERHEAD);
        node.addCpuGroup(cpuGroup);
        
        ComputerParams computer = new ComputerParams(node, nNodes, INTERCONNECT_GAP,
                INTERCONNECT_LATENCY, INTERCONNECT_OVERHEAD);
        
        ClusterParams cluster = new ClusterParams(NETWORK_GAP, NETWORK_LATENCY, NETWORK_OVERHEAD);
        cluster.addComputer(computer);
        
        return cluster;
    }
    
    private static ClusterParams createComputerWithMultipleCpuTypes(int nNodes, int[] nCpus, float[] speed) {
        if(nCpus.length != speed.length)
            throw new IllegalArgumentException("nCpus[] and speed[] do not agree in length.");
                
        ComputeNodeParams node = new ComputeNodeParams(MEMORY_GAP, MEMORY_LATENCY, MEMORY_OVERHEAD);
        for(int i = nCpus.length - 1; i >= 0; i--) {
            node.addCpuGroup(new ProcessorParams(nCpus[i], speed[i]));
        }
        
        ComputerParams computerParams = new ComputerParams(node, nNodes, INTERCONNECT_GAP, INTERCONNECT_LATENCY, INTERCONNECT_OVERHEAD);
        
        ClusterParams cluster = new ClusterParams(NETWORK_GAP, NETWORK_LATENCY, NETWORK_OVERHEAD);
        cluster.addComputer(computerParams);
        
        return cluster;
    }
    
    private static ClusterParams createClusterWithHeterogeneousComputers(int nNode[], int[] nCpus, float[] speed) {
        if(nCpus.length != speed.length)
            throw new IllegalArgumentException("nCpus[] and spee[] do not agree in length.");
                
        ClusterParams cluster = new ClusterParams(NETWORK_GAP, NETWORK_LATENCY, NETWORK_OVERHEAD);
        for(int j = 0; j < nNode.length; j++) {
            ComputeNodeParams node = new ComputeNodeParams(MEMORY_GAP, MEMORY_LATENCY, MEMORY_OVERHEAD);
            for(int i = nCpus.length - 1; i >= 0; i--) {
                node.addCpuGroup(new ProcessorParams(nCpus[i], speed[i]));
            }
            ComputerParams computerParams = new ComputerParams(node, nNode[j], INTERCONNECT_GAP, INTERCONNECT_LATENCY, INTERCONNECT_OVERHEAD);
            cluster.addComputer(computerParams);
        }
        return cluster;
    }
    // </editor-fold>
    
    public Simulator() {        
        setupExecutionManagers();
        
        partitionMakers.add(PartitionMaker.FIRST_DIMENSION_HOMOGENOUS);
        partitionMakers.add(PartitionMaker.FIRST_DIMENSION_PROPORTIONAL);
        partitionMakers.add(PartitionMaker.AUTO_DIMENSION_PROPORTIONAL);
        partitionMakers.add(PartitionMaker.TWO_DIMENSIONS_HOMOGENOUS);
        partitionMakers.add(PartitionMaker.TWO_DIMENTIONAL_HOMOGENOUS_NODES);
        partitionMakers.add(PartitionMaker.TWO_DIMENSIONS_FREE_RECURSIVE);
        
        setupScenarios();
        
        // UI Setup
        ui = new SimulatorUIForm(uiActionListener);
        ui.setScenarioNames(scenarios.toArray());
        ui.setPartioningMethodNames(partitionMakers.toArray());
        ui.setSynchronizationMethodNames(executionManagers.toArray());
        setScenario(0);
        ui.showMessage("Simulator ready.");
        
        ui.setVisible(true);
    }
    
    private void setupExecutionManagers() {            
        final double mu = 10;
        final double sigma = 3;
   
        independantExeman = new IndependentCellExecutionManager(nTimeSteps, mu, sigma);
        barrierExeman = new BarrierCellExecutionManager(nTimeSteps, mu, sigma);
        cascadingExeman = new DecentralizedCellExecutionManager( nTimeSteps, mu, sigma);
        spiralCascadingExeman = new SpiralCellExecutionManager(nTimeSteps, mu, sigma);
        
        executionManagers.add(independantExeman);
        executionManagers.add(barrierExeman);
        executionManagers.add(cascadingExeman);
        executionManagers.add(spiralCascadingExeman);      
        
        for(CellExecutionManager m: executionManagers) {
            m.setLinkedVariablesSize(LINKED_VARIABLES_SIZE)
                    .setBufferDepth(2);
        }
    }
    
    private void setupScenarios() {
        scenarios.add(new SimulationScenario(
                        createHomogenousComputer(2, 2),
                        cascadingExeman,
                        PartitionMaker.FIRST_DIMENSION_HOMOGENOUS)
                    .setNRows(35).setNCols(23)
                    .setNTimeSteps(10)
                    .setGhostBoundarySize(1)
                    .setName("2x4 - First Dimention Homogeneus"));

        scenarios.add(new SimulationScenario(
                        createComputerWithMultipleCpuTypes(4, new int[]{4, 16}, new float[]{1f, 0.7f}),
                        barrierExeman, 
                        PartitionMaker.FIRST_DIMENSION_PROPORTIONAL)
                    .setNRows(100).setNCols(100)
                    .setName("4x(4, 32) - First Dimention Proportional"));

        scenarios.add(new SimulationScenario(
                        createComputerWithMultipleCpuTypes(4, new int[]{4, 16}, new float[]{1f, 0.7f}),
                        barrierExeman, 
                        PartitionMaker.AUTO_DIMENSION_PROPORTIONAL)
                    .setNRows(100).setNCols(100)
                    .setNTimeSteps(10)
                    .setName("4x(4, 32) - Two Dimension Homoegeneous Nodes"));

        scenarios.add(new SimulationScenario(
                        createHomogenousComputer(8, 12),
                        cascadingExeman,
                        PartitionMaker.TWO_DIMENSIONS_HOMOGENOUS)
                    .setNRows(100).setNCols(170)
                    .setNTimeSteps(nTimeSteps)
                    .setName("8x12 - Two Dimensions Homogeneous")
                    .setGhostBoundarySize(0));

        scenarios.add(new SimulationScenario(
                        createHomogenousComputer(4, 12),
                        cascadingExeman,
                        PartitionMaker.TWO_DIMENSIONS_HOMOGENOUS)
                    .setNRows(100).setNCols(170)
                    .setNTimeSteps(nTimeSteps)
                    .setName("4x12 - Two Dimensions Homogeneous")
                    .setGhostBoundarySize(0));
        
        scenarios.add(new SimulationScenario(
                        createComputerWithMultipleCpuTypes(4, new int[]{4, 16}, new float[]{1f, 0.7f}),
                        barrierExeman,
                        PartitionMaker.TWO_DIMENTIONAL_HOMOGENOUS_NODES)
                .setNRows(100).setNCols(100)
                .setNTimeSteps(10)
                .setName("4x(4, 32) - Two Dimension Homoegeneous Nodes"));

        scenarios.add(new SimulationScenario(
                        createClusterWithHeterogeneousComputers(new int[]{3, 4}, new int[]{2, 16}, new float[]{1f, 0.2f}),
                        cascadingExeman,
                        PartitionMaker.TWO_DIMENSIONS_FREE_RECURSIVE)
                .setNRows(90).setNCols(100)
                .setNTimeSteps(10)
                .setName("(3, 4)x(4, 32) - Two Dimensions Free Recursive"));
    }
    
    private void setScenario(int index) {
        ui.setSelectedScenario(index);
        scenario = scenarios.get(index);
        
        model = new SimulationModel(scenario, modelCallbakcs);
        model.setSpeedFactor(speedFactor);
        
        selectedExecutionManagerIndex = executionManagers.indexOf(scenario.getCellExecutionManager());
        ui.setSelectedSynchronizationMethod(selectedExecutionManagerIndex);
        
        selectedPartitioningAlgorithmIndex = partitionMakers.indexOf(scenario.getPartitionMaker());
        ui.setSelectedPartitioningMethod(selectedPartitioningAlgorithmIndex);
        
        ui.setAlternativePartitionings(model.getCandidatePartitionMaps());
        ui.setSelectedAlternativePartitioning(model.getSelectedPartitionMapIndex());
        ui.setBoundaryBuffering(scenario.getGhostBoundarySize() > 0);
        
        drawPartition(model.getSelectedPartitionMap(), 0);
    }

    private void drawPartition(PartitionMap p, int level) {
        if(p == null)
            return;
        
        if(level == 0) {
            ui.setGridSize(p.nRows, p.nCols);
            ui.clearGroups();
        } else if(level == 1)
            ui.createGroup(p, 1, false);
        else if(level == 2)
            ui.createGroup(p, 3, false);
        else if(level == 3)
            ui.createGroup(p, 5, true);
        else
            ui.createGroup(p, 1, true);
        
        for(int i = p.getNumberOfSubPartitions() - 1; i >= 0; i--) {
            drawPartition(p.getSubPartition(i), level + 1);
        }
    }
            
    ///// MAIN ////////////////////////////////////////////////////////////////
    public static void main(String[] args) {
        new Simulator();
    }

}
