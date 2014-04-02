/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import net.hkhandan.util.TextTools;

/**
 *
 * @author hamed
 */
public class SimulationModel {
    
    private static boolean DUMP_TALKS = false;
    private static boolean DUMP_BARRIER = false;
    private static boolean DUMP_BUFFER_OP = false;
    private static char EOL = '\n';
    
    ///// NESTED DATA TYPES ///////////////////////////////////////////////////
   
    // <editor-fold defaultstate="collapsed" desc="Enummerations">     
    public static enum MessageCode {
        RUN,
        INIT,
        CELL_READ_REQUEST,
        CELL_READ_RESPONSE,
        INTERNAL_PROXY_READ,
        BORDER_EXCHANGE
    }
    
    private static enum ConnectionType {
        INCOMMING,
        OUTGOING
    }
    // </editor-fold> 
    
    // <editor-fold defaultstate="collapsed" desc="Interfaces">    
    private static interface CellCallBacks {
        public void cellChanged(final CellInfo cell);
        public void cellBlocked(final CellInfo cell);
        public void cellReleased(final CellInfo cell);
        public void cellPendingIO(final CellInfo cell);
        public void fault(final CellInfo cell, String message);
    }
    
    private static interface ThreadCallBacks extends CellCallBacks {
        public void threadFinishedTimeStep(final WQThreadAddress loc, int timeStep);
        public void threadFinished(final WQThreadAddress loc);
        public void message(String msg, boolean fatal);
    }
    
    private static interface ClusterCallBacks extends ThreadCallBacks {
        public void jobComplete();        
    }
    
    public static interface ModelCallbakcs extends ClusterCallBacks {
        public void modelRefreshed(SimulationModel model);
        public void partitionMakerRejected(PartitionMaker pm);
    }

    private static interface CellToProcessInterface {
        public void runComplete(final Cell sender, final int timestep);
        public void read(Cell sender, CellLocation2D target, int timeStep);
        public void readAllNeighbours(Cell sender, int timeStep);
        public void addWaitTime(double t);
        public void addComputeTime(double t);
        public void sendReadResponse(CellInfo sender, WQCellAddress client, int timeStep);
    }

    private static interface NetworkClient {
        public void processMessage(Message msg);
        public WQThreadAddress getAddress();
    }
    // </editor-fold> 
  
    // <editor-fold defaultstate="collapsed" desc="Supporting Classes"> 
    private static class Message {
        private final MessageCode code;
        private final WQThreadAddress sender;
        private final WQThreadAddress receiver;
        private final Object payload;
        private final int size;

        public Message(MessageCode code, WQThreadAddress sender, 
                WQThreadAddress receiver, Object payload, int size) 
        {
            this.code = code;
            this.sender = sender;
            this.receiver = receiver;
            this.payload = payload;
            this.size = size;
        }

        public MessageCode getCode() {
            return code;
        }

        public Object getPayload() {
            return payload;
        }
        
        public int getSize() {
            return size;
        }

        public WQThreadAddress getSender() {
            return sender;
        }

        public WQThreadAddress getReceiver() {
            return receiver;
        }
        
        @Override 
        public String toString() {
            return "Message[code: " + code + ", sender: " + sender + ", receiver: " + receiver + ", payload: " + payload + ", size: " + size + "bit]";
        }
    }
    
    private static class Barrier {
        private ArrayList<Runnable> monitors = new ArrayList<Runnable>();
        private int nUsers = 0;
        private int downCounter = 0;
        private String name = "Barrier";
        private Barrier parent = null;
        
        private Runnable onLiftAction = new Runnable() {
            @Override
            public void run() {
                for(Runnable client : monitors) {
                    client.run();
                }
                downCounter = monitors.size();
            }
        };
        
        public Barrier() {
            // Nothing
        }
        
        public Barrier(Barrier parent) {
            this.parent = parent;
            parent.addMonitoringUser(onLiftAction);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void addUser() {
            nUsers++;
            downCounter = nUsers;
        }
        
        public void addMonitor(Runnable monitor) {
            monitors.add(monitor);
        }
        
        public void addMonitoringUser(Runnable monitor) {
            addUser();
            addMonitor(monitor);
        }
        
        public void signal() {
            if(downCounter == 0) {
                throw new Error("Barrier signals more than users. Forgot to reset/add user?");
            }
            
            downCounter--;
            
            if(downCounter == 0) {
                if(parent != null) {
                    parent.signal();
                    return;
                }
                
                onLiftAction.run();
            }
        }

        public void reset() {
            downCounter = nUsers;
        }
        
        public void clearUsersAndMonitors() {
            nUsers = 0;
            monitors.clear();
        }
    }
    
    private static class ReadMessagePayload {
        public final static int SIZE_IN_BITS = Integer.SIZE + CellLocation2D.SIZE_IN_BITS*3;
        public final int timeStep;
        public final CellLocation2D targetLocation;
        public final CellLocation2D senderAbsLocation;
        public final CellLocation2D senderRelativeLocation;

        public ReadMessagePayload(int timeStep, CellLocation2D targetLocation, CellLocation2D senderAbsLocation, CellLocation2D senderRelativeLocation) {
            this.timeStep = timeStep;
            this.targetLocation = targetLocation;
            this.senderAbsLocation = senderAbsLocation;
            this.senderRelativeLocation = senderRelativeLocation;
        }

        @Override
        public String toString() {
            return ReadMessagePayload.class.getSimpleName() + "[ts: " + timeStep + "]";
        }
    }
    
    private static class ReadResponseMessagePayload extends ReadMessagePayload {
        public final int sizeInBits;

        public ReadResponseMessagePayload(int size, int timeStep, CellLocation2D targetLocation, CellLocation2D senderAbsLocation, CellLocation2D senderRelativeLocation) {
            super(timeStep, targetLocation, senderAbsLocation, senderRelativeLocation);
            this.sizeInBits = size + SIZE_IN_BITS;
        }        
    }
    
    private static class BorderExchangeMessagePayload {
        public final int sizeInBits;
        public final int timeStep;
        public final GridDivision2D area;

        public BorderExchangeMessagePayload(int cellSizeInBits, int timeStep, GridDivision2D area) {
            this.area = area;
            this.sizeInBits = cellSizeInBits * area.getNCells() + Integer.SIZE;
            this.timeStep = timeStep;
        }
        
        @Override 
        public String toString() {
            return "area: " + area + ", timeStep: " + timeStep;
        }
    }
    
    private static class NeighbourInfo {
        final ProxyCell[]    proxyCells;
        final GridDivision2D localContactRange;
        final GridDivision2D remoteContactRange;
        final GridDivision2D proxyRange;
        final Direction      relativePosition;
        final boolean        isOnTheSameNode;
        final PartitionMap   partition;
        final WQThreadAddress address;

        public NeighbourInfo(ProxyCell[] proxyCells, GridDivision2D localContactRange, GridDivision2D remoteContactRange, GridDivision2D proxyRange, Direction relativePosition, boolean isOnTheSameNode, PartitionMap partition) {
            this.proxyCells = proxyCells;
            this.localContactRange = localContactRange;
            this.remoteContactRange = remoteContactRange;
            this.proxyRange = proxyRange;
            this.relativePosition = relativePosition;
            this.isOnTheSameNode = isOnTheSameNode;
            this.partition = partition;
            address = new WQThreadAddress(partition.getAddress());
        }        
    }
    
    private static class ReadQueueItem {
        public final WQCellAddress client;
        public final int timeStep;

        public ReadQueueItem(WQCellAddress client, int timeStep) {
            this.client = client;
            this.timeStep = timeStep;
        }            
    }
    // </editor-fold>
    
    private static class NetworkInterface implements NetworkClient {
        private final WQThreadAddress address;
        private final double latency;
        private final double overhead;
        private final double gap;
        private final Scheduler scheduler;
        private NetworkInterface parent = null;
        private Scheduler.ScheduleToken lastToken = null;
        private double waitTime = 0;

        ArrayList<NetworkClient> clients = new ArrayList<NetworkClient>();
        
        public NetworkInterface(Scheduler scheduler, WQThreadAddress address, 
                double latency, double overhead, double gap) 
        {
            this.latency = latency;
            this.overhead = overhead;
            this.gap = gap;
            this.scheduler = scheduler;
            this.address = address;
        }
        
        public void sendMessage(final Message msg) {
            if(!address.includes(msg.receiver)) {
                if(parent != null) {
                    parent.sendMessage(msg);
                    return;
                }
                throw new Error(address.toString() + ": Receiver out of domain: " + msg);
            }
            
            NetworkClient target = null;
            
            if(msg.getReceiver() != null) {
                target = findClient(msg.receiver);
                if(target == null) {
                    throw new Error(address.toString() + ": Target for message not found: " + msg);
                }
            }
            
            double delay = gap * msg.getSize() + latency + overhead*2;
            
            waitTime += delay;
            
            final NetworkClient finalTarget = target;
            
            lastToken = scheduler.runWithDelayAfter(new Runnable() {
                @Override
                public void run() {
                    if(msg.getReceiver() == null) {
                        for(NetworkClient client : clients) {
                            client.processMessage(msg);
                        }
                    } else {
                        finalTarget.processMessage(msg);
                    }
                }
            }, delay, lastToken);
        }
        
        private NetworkClient findClient(WQThreadAddress address) {
            for(NetworkClient client : clients) {
                if(client.getAddress().includes(address)) {
                    return client;
                }
            }
            return null;
        }
        
        public void addClient(NetworkClient client) {
            clients.add(client);
        }
        
        private void addSubnetwork(NetworkInterface subNet) {
            clients.add(subNet);
            subNet.parent = this;
        }
        
        public Iterator<NetworkClient> getClients() {
            return clients.iterator();
        }
        
        public void reset() {
            lastToken = null;
            waitTime = 0;
        }
        
        public double getNetworkTrafficTime() {
            return waitTime;
        }

        @Override
        public void processMessage(Message msg) {
            sendMessage(msg);
        }

        @Override
        public WQThreadAddress getAddress() {
            return address;
        }
        
        @Override
        public String toString() {
            return String.format("Address: %s, L:%f, O:%f, G:%f", address.toString(), latency, overhead, gap);
        }
    }
    
    private static class Cell implements CellInfo {        
        private final Scheduler              scheduler;
        private final WQCellAddress          address;
        private final CellToProcessInterface interfaceToProceess;
        private final CellCallBacks          callbacks;
        private final float                  speed;
        private final Object                 readQueueLock = new Object();

        private CellExecutionManager exeman;
        private Barrier              barrier = null;
        private int                  bufferDepth;
        private Direction[]          boundaryExchangeTargets = null;

        private ArrayList<ReadQueueItem> readQueue = null;
        private int[] pendingBufferReads;
        private int   earliestAvailableTimeStep = 0;
        private int   timeStep = 0;

        private boolean isProbbed    = false;
        private boolean isRunning    = false;
        private boolean isBlocked    = false;
        private boolean isPendingBufferRead = false;
        private int     pendingReadResponses = 0;
        private int     nNeighbors   = 0;
        private double  waitStart    = 0;
        private double  computeStart = 0;
        
        public Cell(WQCellAddress address,
                float speed,
                CellExecutionManager exeman,
                int numConnections,
                Scheduler scheduler,
                CellToProcessInterface interfaceToProceess,
                CellCallBacks callbacks)
        {
            this.address = address;
            this.exeman = exeman;
            this.speed = speed;
            this.scheduler = scheduler;            
            this.interfaceToProceess = interfaceToProceess;
            this.callbacks = callbacks;
            this.nNeighbors = numConnections;
            
            if(numConnections > 0) {
                readQueue = new ArrayList<ReadQueueItem>(numConnections);
            }
            
            pendingBufferReads = new int[exeman.getBufferDepth()];
            
            internalReset();
        }
        
        private void internalReset() {            
            isRunning    = false;
            isBlocked    = false;
            waitStart    = 0;
            computeStart = 0;
            timeStep     = 0;
            bufferDepth = exeman.getBufferDepth();
            pendingReadResponses = nNeighbors;
            pendingBufferReads = new int[bufferDepth];
            Arrays.fill(pendingBufferReads, 0); 
            pendingBufferReads[bufferDepth - 1] = nNeighbors;
            earliestAvailableTimeStep = 1 - bufferDepth;

            synchronized(readQueueLock) {
                if(readQueue != null) {
                    readQueue.clear();
                }
            }
        }
        
        public void setProbbed(boolean value) {
            isProbbed = value;
        }
        
        public boolean isProbbed() {
            return isProbbed;
        }
        
        public void alterExecutionManager(CellExecutionManager exeman) {
            this.exeman = exeman;
            internalReset();
        }

        public void setNumberOfNeighbors(int n) {
            nNeighbors = n;
            internalReset();
        }
        
        public void setAtBarrier(Barrier b) {
            this.barrier = b;
            barrier.addMonitoringUser(new Runnable() {
                @Override
                public void run() {
                    barrierLifted();
                }
            });
        }

        public void addBoundaryExchangeTarget(Direction target) {
            if(boundaryExchangeTargets == null) {
                boundaryExchangeTargets = new Direction[1];
                boundaryExchangeTargets[0] = target;
                return;
            }
            
            int len = boundaryExchangeTargets.length;
            
            boundaryExchangeTargets = Arrays.copyOf(
                                            boundaryExchangeTargets, len + 1);
            
            boundaryExchangeTargets[len] = target;            
        }

        private void reset() {
            internalReset();
        }

        public void run() {
            waitStart = scheduler.now();
            
            if(barrier != null) {
                isBlocked = true;
                callbacks.cellBlocked(this);
                
                if(DUMP_BARRIER || isProbbed)
                    System.out.println("Cell " + address + " blocked by barrier");
                
                barrier.signal();
                return;
            }
            
            runStage2();
        }
        
        private void barrierLifted() {
            if(DUMP_BARRIER || isProbbed)
               System.out.println("Cell " + address + " resumed from barrier");
            
            runStage2();
        }
                
        private void runStage2() {
            if(pendingBufferReads[0] > 0) {
                if((DUMP_BUFFER_OP || isProbbed) && !isPendingBufferRead)
                    System.out.println("Cell " + address 
                            + ": block by read request " 
                            + Arrays.toString(pendingBufferReads) + " ts: " 
                            + timeStep + " earliest ts: " 
                            + earliestAvailableTimeStep);
                
                isBlocked = true;
                isPendingBufferRead = true;
                callbacks.cellBlocked(this);                               
                return;
            }
            
            if((DUMP_BUFFER_OP || isProbbed) && isPendingBufferRead) {
                System.out.println("Cell " + address + ": resuming from buffer read block " 
                        + " buffer: " + Arrays.toString(pendingBufferReads) 
                        + " ts: " + timeStep + " earliest ts: " 
                        + earliestAvailableTimeStep);
            }
            
            isPendingBufferRead = false;
            isBlocked = false;                        
            isRunning = true; // needed here to block access before 
                              // current timestep result is ready
            timeStep++;            
            
            for(int i = bufferDepth - 1; i > 0; i--) {
                pendingBufferReads[i - 1] = pendingBufferReads[i];
            }

            pendingBufferReads[bufferDepth - 1] = nNeighbors;

            earliestAvailableTimeStep = timeStep - bufferDepth + 1;
            
            if(isProbbed) {
                System.out.println("Cell " + address + ": Buffer shifted. " 
                        + " buffer: " + Arrays.toString(pendingBufferReads) 
                        + " ts: " + timeStep + " earliest ts: " 
                        + earliestAvailableTimeStep);
            }
            
            callbacks.cellChanged(this);

            runStage3();
        }
        
        private void runStage3() {            
            computeStart = scheduler.now();
                        
            if(nNeighbors > 0) {
                interfaceToProceess.readAllNeighbours(this, timeStep - 1);
                pendingReadResponses = nNeighbors;
                callbacks.cellPendingIO(this);
                isBlocked = true;
                
                if(DUMP_TALKS || isProbbed)
                    System.out.println("Cell " + address 
                            + ": pending read response.");
                
                return;
            }
            
            runStage4();
        }
        
        private void runStage4() {
            interfaceToProceess.addWaitTime(scheduler.now() - waitStart);
            isBlocked = false;
            
            callbacks.cellChanged(this);
            
            scheduler.runWithDelay(new Runnable() {
                @Override
                public void run() {
                    postRunStage();
                }
            }, exeman.getExecutionTime(this) / speed);            
        }
        
        private void postRunStage() {
            isRunning = false;
            interfaceToProceess.addComputeTime(scheduler.now() - computeStart);
            
            synchronized(readQueueLock) {
                if(readQueue != null) {
                    if(!readQueue.isEmpty()) {
                        ArrayList<ReadQueueItem> toDelete = new ArrayList<ReadQueueItem>();
                        
                        for(ReadQueueItem item: readQueue) {
                            if(item.timeStep > timeStep)
                                continue;
                            
                            toDelete.add(item);
                            
                            interfaceToProceess.sendReadResponse(this, 
                                    item.client, item.timeStep);
                            
                            pendingBufferReads[item.timeStep - earliestAvailableTimeStep]--;

                            if(isPendingBufferRead) {            
                                runStage2();
                            }
                        } // for item
                        
                        readQueue.removeAll(toDelete);
                    } // if
                } // if
            } // synchronized
            
            callbacks.cellChanged(this);

            interfaceToProceess.runComplete(this, timeStep);            
        }
        
        public void mockRead(int ts, WQCellAddress clientCellAddress) {
            if(ts < earliestAvailableTimeStep) {
                callbacks.fault(this, "Read request for unavailable timestep " 
                        + ts + " from cell " + clientCellAddress);
                return;
            }
            
            if((DUMP_BUFFER_OP && isPendingBufferRead) || isProbbed)
                System.out.println("Cell " + address + ": read by " 
                        + clientCellAddress + " buffer: " 
                        + Arrays.toString(pendingBufferReads) + " rq ts: " 
                        + ts + " ts: " + timeStep + " earliest ts: " 
                        + earliestAvailableTimeStep);

            if((isRunning && ts >= timeStep) || (!isRunning && ts > timeStep)) 
            {
                synchronized(readQueueLock) {
                    if(readQueue == null) {
                        readQueue = new ArrayList<ReadQueueItem>();
                    }
                    
                    readQueue.add(new ReadQueueItem(clientCellAddress, ts));
                }
                if((DUMP_BUFFER_OP && isPendingBufferRead) || isProbbed)
                    System.out.println("^ Queued");
                return;
            }
            
            pendingBufferReads[ts - earliestAvailableTimeStep]--;

            if(isPendingBufferRead) {            
                runStage2();
            }
            
            interfaceToProceess.sendReadResponse(this, clientCellAddress, ts);
        }
        
        public void mockReadByProxy(int ts) {
            if(ts < earliestAvailableTimeStep || ts > timeStep) {
                callbacks.fault(this, "Read request for unavailable timestep " 
                        + ts + " Earliest ts: " + earliestAvailableTimeStep 
                        + " Current ts: " + timeStep);
                return;
            }
            
            pendingBufferReads[ts - earliestAvailableTimeStep]--;            
            
            if((DUMP_BUFFER_OP && isPendingBufferRead) || isProbbed)
                System.out.println("Cell " + address + ": read by proxy" 
                        + " buffer: " 
                        + Arrays.toString(pendingBufferReads) + " rq ts: " 
                        + ts + " ts: " + timeStep + " earliest ts: " 
                        + earliestAvailableTimeStep);
        }
        
        public void processReadResponse(int ts, WQCellAddress sender) {
            if(ts != timeStep - 1) {
                callbacks.fault(this, "Response timestep (" + ts + ") from cell " 
                        + sender + " does not match the needed timestep (" 
                        + (timeStep - 1) + ")");
                return;
            }
            
            pendingReadResponses--;

            if(DUMP_TALKS || isProbbed) {
                System.out.println("Cell " + address + ": received read response from "
                    + sender + " ts: " + ts + " remaining: " + pendingReadResponses);
            }
            
            if(pendingReadResponses == 0) {
                if(DUMP_TALKS || isProbbed) {
                    System.out.println("Cell " + address + ": all read responses received.");
                }
                runStage4();
            } else if(pendingReadResponses < 0) {
                callbacks.fault(this, "Received more read responses than anticipated (" + nNeighbors + ")");
            }                    
        }
        
        public String getDump() {
            StringBuilder b = new StringBuilder();
            
            b.append("Cell ").append(address).append(EOL)
                    .append("  speed: ").append(speed).append(EOL)
                    .append("  timeStep: ").append(timeStep).append(EOL)
                    .append("  earliestAvilableTimestep: ")
                        .append(earliestAvailableTimeStep).append(EOL)
                    
                    .append("  pendingBufferReads: ").append(
                            Arrays.toString(pendingBufferReads)).append(EOL)
                    
                    .append("  pendingReadResponses: ")
                        .append(pendingReadResponses).append(EOL)
                    
                    .append("  isBlocked: ").append(isBlocked).append(EOL)
                    .append("  isPendingBufferRead: ")
                        .append(isPendingBufferRead).append(EOL)
                    
                    .append("  isRunning: ").append(isRunning).append(EOL)
                    .append("  nNeighbours: ").append(nNeighbors).append(EOL)
                    .append("  isAtBarrier: ").append(isAtBarrier()).append(EOL)
                    .append("  boundaryExchangeTargets: ").append(
                            Arrays.toString(boundaryExchangeTargets))
                        .append(EOL)
                    
                    .append("  readQueue: ").append(readQueue).append(EOL);
            
            return b.toString();
        }
        
        // -- CellInfo -- //
        
        @Override
        public int getTimeStep() {
            return timeStep;
        }

        @Override
        public int getExecutingProcessorUnitId() {
            return isRunning?0:-1;
        }
        
        @Override 
        public WQCellAddress getAddress() {
            return address;
        }
        
        @Override
        public boolean isAtBarrier() {
            return barrier != null;
        }

        @Override
        public Direction[] getBoundaryExchangeTargets() {
            return boundaryExchangeTargets;
        }
        
        // -- Object -- //
        
        @Override
        public String toString() {
            return String.format("Cell[loc: " + address + " timestep: " + timeStep + "]");
        }
    }

    private static class ProxyCell implements CellInfo {
        private final WQCellAddress address;
        private final Object readQueueLock = new Object();
        private final CellCallBacks callbacks;
        private final CellToProcessInterface interfaceToProcess;
        private int timestep = 0;
        private ArrayList<ReadQueueItem> readQueue = null;
        private boolean duplicate = false;

        public ProxyCell(WQCellAddress address, CellCallBacks callbacks, CellToProcessInterface interfaceToProcess) {
            this.address = address;
            this.callbacks = callbacks;
            this.interfaceToProcess = interfaceToProcess;
        } 
        
        private void reset() {
            timestep = 0;
            synchronized(readQueueLock) {
                if(readQueue != null) {
                    readQueue.clear();
                }
            }
        }
        
        public void flagAsDuplicate() {
            duplicate = true;
        }
        
        public boolean isDuplicate() {
            return duplicate;
        }

        public void setTimestep(int ts) {
            timestep = ts;
            
            synchronized(readQueueLock) {
                if(readQueue != null) {
                    if(!readQueue.isEmpty()) {
                        ArrayList<ReadQueueItem> toDelete = new ArrayList<ReadQueueItem>();
                        
                        for(ReadQueueItem item: readQueue) {
                            if(item.timeStep > timestep)
                                continue;
                            
                            toDelete.add(item);
                            
                            interfaceToProcess.sendReadResponse(this, item.client, item.timeStep);
                        } // for item
                        
                        readQueue.removeAll(toDelete);
                    } // if
                } // if
            } // synchronized
        }
        
        public void mockRead(int ts, WQCellAddress clientCellAddress) {
            if(ts > timestep) {
                synchronized(readQueueLock) {
                    if(readQueue == null) {
                        readQueue = new ArrayList<ReadQueueItem>();
                    }
                    readQueue.add(new ReadQueueItem(clientCellAddress, ts));                    
                }
                return;
            }
            
           interfaceToProcess.sendReadResponse(this, clientCellAddress, ts);
        }
        
        @Override
        public WQCellAddress getAddress() {
            return address;
        }

        @Override
        public int getExecutingProcessorUnitId() {
            return -1;
        }
        
        @Override
        public int getTimeStep() {
            return timestep;
        }

        @Override
        public boolean isAtBarrier() {
            return false;
        }

        @Override
        public Direction[] getBoundaryExchangeTargets() {
            return null;
        }
    }
    
    private static class CpuThreadModel implements NetworkClient {        
        private class ConnectionCounter implements CellDependancy.ToRun {
            private int count = 0;
            @Override
            public void run(int row, int col) {
                if(rootPartition.contains(row, col))
                    count++;
            }
            public int getCount() {
                return count;
            }
        }
        
        private final WQThreadAddress  address;
        private final float            speed;
        private final Scheduler        scheduler;
        private final NetworkInterface nic;
        private final Barrier          barrier;
        private final Barrier          jobCompleteBarrier;
        private final ThreadCallBacks  callbacks;
        
        private CellExecutionManager exeman;
        
        private PartitionMap rootPartition = null;
        private PartitionMap partition = null;
        
        private EnumMap<Direction, NeighbourInfo[]> neighbours 
                = new EnumMap<Direction, NeighbourInfo[]>(
                        Direction.class);
        
        private Cell[][] cells;
        private ArrayList<ProxyCell> proxies = new ArrayList<ProxyCell>();

        private boolean isRunning   = false;
        private double  waitTime    = 0;
        private double  computeTime = 0;
        private long    progress    = 0;
        private long    maxProgress = 0;

        private final CellToProcessInterface interfaceForCells = new CellToProcessInterface() {
            @Override
            public void runComplete(Cell sender, int ts) {                
                if(sender.getBoundaryExchangeTargets() != null) {
                    for(Direction d: sender.getBoundaryExchangeTargets()) {
                        sendBorderTo(d, ts);
                    }
                }
                
                progress++;
                
                CellLocation2D next = exeman.nextCell(sender.getAddress(),
                        partition.nRows, partition.nCols);
                
                if(next == null) {
                    callbacks.threadFinishedTimeStep(address, ts);
                    if(ts == exeman.getNTimeSteps()) {
                        callbacks.threadFinished(address);
                        jobCompleteBarrier.signal();
                        return;
                    }
                    next = exeman.getStartCell();
                }
                
                getCell(next.getRow(), next.getCol()).run();
            }
            
            @Override
            public void read(Cell sender, CellLocation2D target, int timeStep) {
                if(partition.contains(target)) {
                    if(DUMP_TALKS || sender.isProbbed())
                        callbacks.message(sender.getAddress() + " --> " 
                                + target + ": local read request (" + timeStep 
                                + ")", false);            
                    
                    Cell targetCell = resolveCell(target.getRow(), target.getCol());
                    targetCell.mockRead(timeStep, sender.getAddress());
                    return;
                }
                
                MessageCode code = MessageCode.CELL_READ_REQUEST;
                
                ReadMessagePayload payload = new ReadMessagePayload(timeStep, 
                        target, sender.getAddress().getAbsLocation(), 
                        sender.getAddress());                
                
                WQThreadAddress targetAddress = null; 
                
                ProxyCell proxy = getProxyFor(target);
                
                if(proxy != null) {
                    code = MessageCode.INTERNAL_PROXY_READ;
                    targetAddress = address;
                    
                    if(DUMP_TALKS || sender.isProbbed())
                        callbacks.message(sender.getAddress() + " --> " + targetAddress + " " + target + ": proxy read request (" + timeStep + ")", false);            
                } else {
                    targetAddress = new WQThreadAddress(rootPartition.getInnermostContainer(target).getAddress());
                    
                    if(DUMP_TALKS || sender.isProbbed())
                        callbacks.message(sender.getAddress() + " --> " + targetAddress + " " + target + ": remote read request (" + timeStep + ")", false);            
                }
                
                Message message = new Message(code, address, targetAddress, payload, ReadMessagePayload.SIZE_IN_BITS);
                nic.sendMessage(message);
            }

            @Override
            public void readAllNeighbours(final Cell sender, final int timeStep) {
                exeman.cellDependancy.iterateAround(
                        sender.getAddress().getAbsLocation(), 
                        new CellDependancy.ToRun() 
                {
                    @Override
                    public void run(int row, int col) {
                        CellLocation2D loc = new CellLocation2D(row, col);
                        if(!rootPartition.contains(loc))
                            return;
                        read(sender, loc, timeStep);
                    }
                });
            }
            
            @Override
            public void sendReadResponse(CellInfo sender, WQCellAddress client, int timeStep) {
                if(DUMP_TALKS)
                    callbacks.message(sender.getAddress() + " --> " + client + ": read response (" + timeStep + ")", false);
                
                ReadResponseMessagePayload payload 
                        = new ReadResponseMessagePayload(
                            exeman.getLinkedVariablesSize(), 
                            timeStep, 
                            client.getAbsLocation(), 
                            sender.getAddress().getAbsLocation(), 
                            sender.getAddress());
                
                Message msg = new Message(MessageCode.CELL_READ_RESPONSE, 
                        address, client.getProcessLoc(), 
                        payload, payload.sizeInBits);
                
                nic.sendMessage(msg);                
            }
            
            @Override
            public void addWaitTime(double t) {
                waitTime += t;
            }

            @Override
            public void addComputeTime(double t) {
                computeTime += t;
            }
        };

        private CpuThreadModel(
                WQThreadAddress      address,
                float                speed,                
                CellExecutionManager exeman,
                Scheduler            scheduler,
                NetworkInterface     nic,
                Barrier              barrier,
                Barrier              jobCompleteBarrier,
                ThreadCallBacks      callBacks) 
        {
            this.address            = address;
            this.speed              = speed;
            this.exeman             = exeman;
            this.scheduler          = scheduler;
            this.nic                = nic;
            this.barrier            = barrier;
            this.jobCompleteBarrier = jobCompleteBarrier;
            this.callbacks          = callBacks;            
            jobCompleteBarrier.addUser();
            nic.addClient(this);
        }
        
        public Cell getCell(int row, int col) {
            if(cells == null)
                return null;
            
            return cells[row][col];
        }
        
        public void reset() {
            for(Cell[] row : cells) {
                for(Cell c : row) {
                    c.reset();
                }
            }
            for(ProxyCell cell: proxies) {
                cell.reset();
            }
            isRunning = false;
            waitTime = 0;
            computeTime = 0;
            progress = 0;
        }
        
        public void run() {
            if(isRunning)
                return;
            
            isRunning = true;
            if(partition.isEmpty())
                return;
            
            for(Direction d: Direction.values()) {
                sendBorderTo(d, 0);
            }
            
            CellLocation2D startLoc = exeman.getStartCell();
            getCell(startLoc.getRow(), startLoc.getCol()).run();
        }

        private Cell resolveCell(int absRow, int absCol) {
            if(partition == null)
                return null;
            
            if(partition.contains(absRow, absCol))
                return getCell(absRow - partition.rowOffset,
                        absCol - partition.colOffset);
            
            return null;
        }
        
        private ProxyCell getProxyFor(CellLocation2D absLoc) {
            return getProxyFor(absLoc.getRow(), absLoc.getCol());
        }
        
        private ProxyCell getProxyFor(int row, int col) {
            if(proxies == null)
                return null;
            
            for(ProxyCell cell: proxies) {
                final WQCellAddress cellAddress = cell.getAddress();
                if(cellAddress.getAbsRow() == row
                        && cellAddress.getAbsCol() == col)
                {
                    return cell;
                }
            }
            
            return null;
        }
        
        private int getNumberOfConnections(int absRow, int absCol, CellDependancy dependancy) {
            if(dependancy == CellDependancy.INDEPENDANT)
                return 0;
            
            ConnectionCounter connectionCounter = new ConnectionCounter();
            dependancy.iterateAround(absRow, absCol, connectionCounter);            
            return connectionCounter.getCount();
        }
        
        public void repartition(PartitionMap partitionMap, 
                CellExecutionManager exeman, int gbs) 
        {
            rootPartition = partitionMap;
            partition = rootPartition.getSubPartition(address.getComputerRank())
                    .getSubPartition(address.getNodeRank())
                    .getSubPartition(address.getCpuGroupRank())
                    .getSubPartition(address.getCpuRank());
            
            this.exeman = exeman;
            
            neighbours.clear();            
            proxies.clear();
            
            final int border = (gbs > 1)?gbs:1;
            
            for(Direction pos: Direction.values()) {
                PartitionMap[] items = partition.getNeighbors(pos);
                NeighbourInfo[] neighbourInfos = new NeighbourInfo[items.length];
                
                for(int i = 0; i < items.length; i++) {
                    PartitionMap item = items[i];
                    
                    WQThreadAddress neighbourAddress 
                            = new WQThreadAddress(item.getAddress());
                    
                    GridDivision2D proxyRange 
                            = partition.getBorder(pos, border).mirror(pos)
                                    .intersect(item);
                    
                    GridDivision2D localContactRange 
                            = proxyRange.mirror(pos.getOpposite());
                    
                    GridDivision2D remoteContactRange = item.grow(border).intersect(partition);
                    
                    boolean areOnTheSameNode 
                            = areOnTheSameNode(partition.getAddress(), 
                                    items[0].getAddress());
                    
                    ProxyCell[] tmpProxies = null;

                    if(!areOnTheSameNode && exeman.cellDependancy != CellDependancy.INDEPENDANT && gbs > 0)
                    {
                        tmpProxies = new ProxyCell[proxyRange.getNCells()];
                        int proxyCellCoutner = 0;

                        for(int row = proxyRange.nRows - 1; row >= 0; row--) {
                            for(int col = proxyRange.nCols - 1; col >= 0; col--) {
                                int absRow = proxyRange.rowOffset + row;
                                int absCol = proxyRange.colOffset + col;
                                
                                WQCellAddress proxyCellAddress 
                                        = new WQCellAddress(neighbourAddress, 
                                                absRow - item.rowOffset, 
                                                absCol - item.colOffset,
                                                absRow, absCol);                                                

                                ProxyCell proxyCell 
                                        = new ProxyCell(proxyCellAddress, 
                                            callbacks, interfaceForCells);
                                
                                if(getProxyFor(absRow, absCol) != null) {
                                    proxyCell.flagAsDuplicate();
                                    System.out.println("Proxy cell is duplicate: " + proxyCell.getAddress());
                                }
                                
                                proxies.add(proxyCell);
                                
                                tmpProxies[proxyCellCoutner] = proxyCell;
                                proxyCellCoutner++;
                            } // for(col)
                        } // for(row)
                    } // if(!areOnTheSameNode)
                    
                    neighbourInfos[i] = new NeighbourInfo(tmpProxies,
                            localContactRange, remoteContactRange, proxyRange,
                            pos, areOnTheSameNode, item);
                } // for(i)
                
                neighbours.put(pos, neighbourInfos);
            } // for(pos)
            
            cells = new Cell[partition.nRows][partition.nCols];
            
            for(int row = partition.nRows - 1; row >= 0; row--) {
                for(int col = partition.nCols - 1; col >= 0; col--) {
                    int absRow = partition.rowOffset + row;
                    int absCol = partition.colOffset + col;
                    
                    int nConnections = getNumberOfConnections(
                            absRow, absCol, exeman.cellDependancy);
                    
                    WQCellAddress cellAddress = new WQCellAddress(
                            address, row, col, absRow, absCol);
                    
                    cells[row][col] = new Cell(cellAddress, speed, exeman, 
                            nConnections, scheduler, interfaceForCells, 
                            callbacks);
                }
            }
            
            if(exeman.cellDependancy != CellDependancy.INDEPENDANT) {
                for(Direction d: Direction.values()) {
                    NeighbourInfo[] infoArray = neighbours.get(d);
                    
                    if(infoArray.length == 0)
                        continue;
                        
                    if(infoArray[0].isOnTheSameNode)
                        continue;
                    
                    CellLocation2D loc 
                            = exeman.getBoundaryExchangeTriggerCell(
                                    d, partition.nRows, 
                                    partition.nCols, gbs);
                    
                    if(loc != null) {
                        getCell(loc.getRow(), loc.getCol())
                                .addBoundaryExchangeTarget(d);
                    }
                }
            }
            
            CellLocation2D cellAtBarrierLoc = exeman.getCellAtBarrier(
                    partition.nRows, partition.nCols);
            
            if(cellAtBarrierLoc != null) {
                getCell(cellAtBarrierLoc.getRow(), cellAtBarrierLoc.getCol())
                        .setAtBarrier(barrier);
            }
            
            maxProgress = exeman.getNTimeSteps() * partition.getNCells();
        }
        
        private static boolean areOnTheSameNode(int[] a, int[] b) {
            return a[0] == b[0] && a[1] == b[1];
        }
        
        public double getTotalWaitingTime() {
            return waitTime; 
        }
        
        public double getTotalComputeTime() {
            return computeTime;
        }
        
        public double getProgress() {
            return (double)progress/(double)maxProgress;
        }
        
        private void sendBorderTo(final Direction d, final int timeStep) {
            final CellDependancy.ToRun runAround = new CellDependancy.ToRun() {
                @Override
                public void run(int row, int col) {
                    Cell c = resolveCell(row, col);
                    if(c != null) {
                        c.mockReadByProxy(timeStep);
                    }
                }
            };
                    
            for(NeighbourInfo neighbour: neighbours.get(d)) {
                if(neighbour.isOnTheSameNode)
                    continue;
                
                if(neighbour.proxyCells == null)
                    continue;
                
                BorderExchangeMessagePayload payload
                        = new BorderExchangeMessagePayload(
                                exeman.getLinkedVariablesSize(),
                                timeStep, neighbour.remoteContactRange);
                
                Message msg = new Message(MessageCode.BORDER_EXCHANGE, 
                        address, neighbour.address, payload, 
                        payload.sizeInBits);
                
                if(DUMP_TALKS)
                    System.out.println(address + " --> " + neighbour.address 
                            + ": " + payload);
                
                for(ProxyCell pc: neighbour.proxyCells) {
                    exeman.cellDependancy.iterateAround(pc.getAddress().getAbsLocation(), runAround);
                }

                nic.sendMessage(msg);                
            }
        }
        
        @Override
        public WQThreadAddress getAddress() {
            return address;
        }

        @Override
        public void processMessage(final Message msg) {
            switch(msg.getCode()) {                
                case CELL_READ_REQUEST: {
                    ReadMessagePayload payload = (ReadMessagePayload)msg.getPayload();
                    
                    Cell target = resolveCell(payload.targetLocation.getRow(), 
                            payload.targetLocation.getCol());
                    
                    WQCellAddress clientCellAddress 
                            = new WQCellAddress(msg.getSender(), 
                                payload.senderRelativeLocation.getRow(), 
                                payload.senderRelativeLocation.getCol(), 
                                payload.senderAbsLocation.getRow(), 
                                payload.senderAbsLocation.getCol());
                    
                    target.mockRead(payload.timeStep, clientCellAddress);
                                        
                    break;
                }
                    
                case CELL_READ_RESPONSE: {
                    ReadResponseMessagePayload payload 
                            = (ReadResponseMessagePayload)msg.payload;
                    
                    Cell target = resolveCell(payload.targetLocation.getRow(), 
                            payload.targetLocation.getCol());
                    
                    WQCellAddress senderAddress 
                            = new WQCellAddress(msg.getSender(), 
                                payload.senderRelativeLocation.getRow(), 
                                payload.senderRelativeLocation.getCol(), 
                                payload.senderAbsLocation.getRow(), 
                                payload.senderAbsLocation.getCol());
                    
                    target.processReadResponse(payload.timeStep, senderAddress);
                    
                    break;
                }
                    
                case INTERNAL_PROXY_READ: {
                    ReadMessagePayload payload = (ReadMessagePayload)msg.getPayload();
                    ProxyCell target = getProxyFor(payload.targetLocation);
                    
                    if(target == null) {
                        System.out.println(address + ": Desired proxy cell not found: " + payload.targetLocation);
                    }
                    
                    Cell sender = getCell(
                            payload.senderRelativeLocation.getRow(),
                            payload.senderRelativeLocation.getCol());
                    
                    target.mockRead(payload.timeStep, sender.getAddress());
                    
                    break;
                }
                
                case BORDER_EXCHANGE: {
                    final BorderExchangeMessagePayload payload 
                            = (BorderExchangeMessagePayload)msg.getPayload();

                    payload.area.iterateAllCells(new GridDivision2D.ToRun() {
                        @Override
                        public void run(int row, int col, int absRow, int absCol, Object[] params) {
                            ProxyCell pCell = getProxyFor(absRow, absCol);
                            if(pCell == null) {
                                callbacks.message(address + ": Proxy cell at (" + absRow 
                                        + ", " + absCol + ") is missing. Source: " + msg.getSender() , false);
                                return;
                            }
                            pCell.setTimestep(payload.timeStep);
                        }
                    });
                }
            } // switch(msg.getCode())
        }
        
        @Override
        public String toString() {
            return "CpuModel[locator: " + address.toString() + " division: " + partition + "]";
        }

        private String dump() {
            StringBuilder builder = new StringBuilder();
            builder.append(address.toString()).append('\n')
                    .append("  Bounds: ").append(partition.toString()).append('\n');
            builder.append("  Neighbours:\n");
            for(Direction d: Direction.values()) {
                NeighbourInfo[] infos = neighbours.get(d);
                if(infos.length > 0) {
                    builder.append("    ").append(d).append(":\n");
                    for(NeighbourInfo info: infos) {
                        builder.append("      Address: ")
                                .append(info.address).append('\n')
                                
                            .append("        LocalRange: ")
                                .append(info.localContactRange).append('\n')
                                
                            .append("        RemoteRange: ")
                                .append(info.remoteContactRange).append('\n')
                                
                            .append("        On The Same Node: ")
                                .append(Boolean.toString(info.isOnTheSameNode))
                                .append("\n")
                                
                            .append("        Number of Proxy Cells: ")
                                .append((info.proxyCells == null)
                                        ?"None"
                                        :Integer.toString(
                                                info.proxyCells.length))
                                .append('\n');                        
                    }
                }                
            }
            
            for(Cell[] row : cells) {
                for(Cell cell : row) {
                    if(cell.isAtBarrier()) {
                        builder.append("  Cell On Barrier: ")
                                .append((CellLocation2D)cell.getAddress())
                                .append('\n');
                    }
                    
                    Direction ds[] = cell.getBoundaryExchangeTargets();
                    if(ds != null) {
                        builder.append("  Cell ").append((CellLocation2D)cell.getAddress())
                                .append(" --> ");
                        for(Direction d: ds) {
                            builder.append(d).append(' ');
                        }
                        builder.append('\n');
                    }
                }
            }
            return builder.toString();
        }        
    }

    private static class ComputeNodeModel {
        private final NetworkInterface memoryInterface;
        private final NetworkInterface[] cpuGroupInternalChannels;
        private final Barrier barrier;
        private final Barrier jobCompleteBarrier;
        
        private final CpuThreadModel[][] threads;
        
        private ComputeNodeModel(
                WQThreadAddress address,
                ComputeNodeParams params, 
                Scheduler scheduler, 
                NetworkInterface parentNetwork,
                Barrier parentBarrier,
                Barrier parentJobCompleteBarrier,
                CellExecutionManager exeman,
                ThreadCallBacks callbacks) 
        {
            memoryInterface = new NetworkInterface(scheduler, address, 
                    params.getMemoryLatency(), params.getMemoryIOOverhead(), 
                    params.getMemoryGap());
            
            parentNetwork.addSubnetwork(memoryInterface);
            
            barrier = new Barrier(parentBarrier);
            barrier.setName("Node " + address + " Barrier");
            jobCompleteBarrier = new Barrier(parentJobCompleteBarrier);
            jobCompleteBarrier.setName("Node " + address + " Job Complete Barrier");
            
            int[] nThreads = params.getNumberOfCpusPerGroup();
            threads = new CpuThreadModel[params.getNumberOfCpuGroups()][];

            int computerRank = address.getComputerRank();
            int nodeRank = address.getNodeRank();
            
            cpuGroupInternalChannels = new NetworkInterface[params.getNumberOfCpuGroups()];
            for(int groupRank = cpuGroupInternalChannels.length - 1; groupRank >= 0; groupRank--) {
                cpuGroupInternalChannels[groupRank] = new NetworkInterface(
                        scheduler, 
                        new WQThreadAddress(computerRank, nodeRank, groupRank, -1), 
                        0, 0, 0);
                
                threads[groupRank] = new CpuThreadModel[nThreads[groupRank]];
                float speed = params.getCpuGroupParams(groupRank).getSpeedPerCore();
                
                for(int threadRank = nThreads[groupRank] - 1; threadRank >= 0; threadRank--) {
                    CpuThreadModel thread = new CpuThreadModel(
                            new WQThreadAddress(computerRank, nodeRank, groupRank, threadRank),
                            speed, exeman, scheduler, memoryInterface,
                            barrier, jobCompleteBarrier, callbacks);
                    
                    threads[groupRank][threadRank] = thread;
                }
            }
        }        

        private void reset() {
            barrier.reset();
            jobCompleteBarrier.reset();
            memoryInterface.reset();
            for(NetworkInterface ni : cpuGroupInternalChannels) {
                ni.reset();
            }
            for(CpuThreadModel[] group : threads) {
                for(CpuThreadModel thread : group) {
                    thread.reset();
                }
            }
        }

        private CellInfo resolveCell(int absRow, int absCol) {
            for(CpuThreadModel[] group : threads) {
                for(CpuThreadModel thread : group) {
                    CellInfo res = thread.resolveCell(absRow, absCol);
                    if(res != null)
                        return res;
                }
            }
            return null;
        }

        public void repartition(PartitionMap partitionMap, CellExecutionManager exeman, int gbs) {
            barrier.clearUsersAndMonitors();
            for(CpuThreadModel[] group : threads) {
                for(CpuThreadModel thread : group) {
                    thread.repartition(partitionMap, exeman, gbs);
                }
            }
        }

        private void run() {
            for(CpuThreadModel[] group : threads) {
                for(CpuThreadModel thread : group) {
                    thread.run();
                }
            }
        }
        
        public double getTotalWaitingTime() {
            double t = 0;
            for(CpuThreadModel[] group : threads) {
                for(CpuThreadModel thread : group) {
                    t += thread.getTotalWaitingTime();
                }
            }
            return t;
        }
        
        public double getTotalComputeTime() {
            double t = 0;
            for(CpuThreadModel[] group : threads) {
                for(CpuThreadModel thread : group) {
                    t += thread.getTotalComputeTime();
                }
            }
            return t;
        }
        
        public double getProgress() {
            double p = 0;
            for(CpuThreadModel[] group : threads) {
                double gp = 0;
                for(CpuThreadModel thread : group) {
                    gp += thread.getProgress();
                }
                p += gp/group.length;
            }
            return p/threads.length;
        }
        
        public String dump() {
            StringBuilder result = new StringBuilder();
            result.append("NIC\n");
            result.append(TextTools.indent(memoryInterface.toString(), 2)).append('\n');
            result.append("Groups\n");
            for(int i = 0; i < cpuGroupInternalChannels.length; i++) {
                result.append("  Item [").append(i).append("]\n");
                result.append("    NIC\n");
                result.append(TextTools.indent(cpuGroupInternalChannels[i].toString(), 6)).append('\n');
                result.append("    CpuThreads\n");
                for(int j = 0; j < threads[i].length; j++) {
                    result.append("      Item [").append(j).append("]\n");
                    result.append(TextTools.indent(threads[i][j].dump(), 8)).append('\n');
                }
            }
            return result.toString();
        }
    }

    private static class ComputerModel {
        private final NetworkInterface networkInterface;
        private final ComputeNodeModel[] nodes;
        private final Barrier barrier;
        private final Barrier jobCompleteBarrier;
        
        private ComputerModel(
                WQThreadAddress address, 
                ComputerParams params,
                Scheduler scheduler,
                NetworkInterface parentNetwork,
                Barrier parentBarrier,
                Barrier parentJobCompleteBarrier,
                CellExecutionManager exeman,
                ThreadCallBacks callbacks)
        {
            networkInterface = new NetworkInterface(scheduler, address,
                    params.getInterconnectLatency(),
                    params.getInterconnectOverhead(),
                    params.getInterconnectGap());
            
            parentNetwork.addSubnetwork(networkInterface);
            
            barrier = new Barrier(parentBarrier);
            barrier.setName("Computer " + address + " Barrier");
            jobCompleteBarrier = new Barrier(parentJobCompleteBarrier);
            jobCompleteBarrier.setName("Computer " + address + " Job Complete Barrier");
            
            int computerRank = address.getComputerRank();
            
            nodes = new ComputeNodeModel[params.getNumberOfNodes()];
            for(int i = nodes.length - 1; i >= 0; i--) {
                nodes[i] = new ComputeNodeModel(
                        new WQThreadAddress(computerRank, i, -1, -1),
                        params.getNodeParams(),
                        scheduler, networkInterface,
                        barrier, jobCompleteBarrier, exeman, callbacks);
            }
        }

        private void reset() {
            barrier.reset();
            jobCompleteBarrier.reset();
            networkInterface.reset();
            for(ComputeNodeModel node : nodes) {
                node.reset();
            }
        }

        private CellInfo resolveCell(int absRow, int absCol) {
            for(ComputeNodeModel node : nodes) {
                CellInfo res = node.resolveCell(absRow, absCol);
                if(res != null)
                    return res;
            }
            return null;
        }

        private void repartition(PartitionMap partitionMap, CellExecutionManager exeman, int gbs) {
            for(ComputeNodeModel node : nodes) {
                node.repartition(partitionMap, exeman, gbs);
            }
        }

        private void run() {
            for(ComputeNodeModel node : nodes) {
                node.run();
            }
        }
        
        public double getTotalWaitingTime() {
            double t = 0;
            for(ComputeNodeModel node : nodes) {
                return t += node.getTotalWaitingTime();
            }
            return t;
        }
        
        public double getTotalComputeTime() {
            double t = 0;
            for(ComputeNodeModel node : nodes) {
                return t += node.getTotalComputeTime();
            }
            return t;
        }
        
        public double getNetworkTrafficTime() {
            return networkInterface.getNetworkTrafficTime();
        }
        
        public double getProgress() {
            double p = 0;
            for(ComputeNodeModel node : nodes) {
                p += node.getProgress();
            }
            return p / nodes.length;
        }
        
        public String dump() {
            StringBuilder result = new StringBuilder();
            result.append("NIC\n");
            result.append(TextTools.indent(networkInterface.toString(), 2)).append('\n');
            result.append("Nodes\n");
            for(int i = 0; i < nodes.length; i++) {
                result.append("  Item [").append(i).append("]\n");
                result.append(TextTools.indent(nodes[i].dump(), 4)).append('\n');
            }
            return result.toString();
        }
    }
    
    private static class ClusterModel {
        private final NetworkInterface networkInterface;
        private final ComputerModel[]  computers;
        private final Barrier barrier = new Barrier();
        private final Barrier jobCompleteBarrier = new Barrier();
        
        public ClusterModel(
                ClusterParams params, 
                Scheduler scheduler, 
                CellExecutionManager exeman,
                final ClusterCallBacks callbacks) 
        {
            barrier.setName("Cluster Barrier");
            jobCompleteBarrier.setName("Cluster Job Complete Barrier");
            
            networkInterface = new NetworkInterface(scheduler,
                    new WQThreadAddress(-1, -1, -1, -1),
                    params.getNetworkLatency(), params.getNetworkOverhead(),
                    params.getNetworkGap());
            
            computers = new ComputerModel[params.getNumberOfComputers()];
            for(int i = computers.length - 1; i >=0; i--) {
                computers[i] = new ComputerModel(
                        new WQThreadAddress(i, -1, -1, -1),
                        params.getComputerParams(i), 
                        scheduler, networkInterface,
                        barrier, jobCompleteBarrier, exeman, callbacks);
            }
            
            jobCompleteBarrier.addMonitor(new Runnable() {
                @Override
                public void run() {
                    callbacks.jobComplete();
                }
            });
        }
        
        public void reset() {
            barrier.reset();
            jobCompleteBarrier.reset();
            networkInterface.reset();
            for(ComputerModel computer : computers) {
                computer.reset();
            }
        }
        
        private void run() {
            for(ComputerModel computer : computers) {
                computer.run();
            }
        }

        public void repartition(PartitionMap selectedPartitionMap, 
                CellExecutionManager exeman, int gbs) 
        {
            for(ComputerModel computer : computers) {
                computer.repartition(selectedPartitionMap, exeman, gbs);
            }
        }

        private CellInfo resolveCell(int absRow, int absCol) {
            for(ComputerModel computer : computers) {
                CellInfo res = computer.resolveCell(absRow, absCol);
                if(res != null)
                    return res;
            }
            return null;
        }
        
        public double getTotalWaitingTime() {
            double t = 0;
            for(ComputerModel computer:computers) {
                t += computer.getTotalWaitingTime();
            }
            return t;
        }
        
        public double getTotalComputeTime() {
            double t = 0;
            for(ComputerModel computer:computers) {
                t += computer.getTotalComputeTime();
            }
            return t;
        }
        
        public double getTotalNetworkTrafficTime() {
            double t = 0;
            for(ComputerModel computer:computers) {
                t += computer.getNetworkTrafficTime();
            }
            t += networkInterface.getNetworkTrafficTime();
            return t;
        }
        
        public double getProgress() {
            double p = 0;
            for(ComputerModel computer:computers) {
                p += computer.getProgress();
            }
            return p / computers.length;
        }

        public String dump() {
            StringBuilder result = new StringBuilder();
            result.append("NIC\n");
            result.append(TextTools.indent(networkInterface.toString(), 2)).append('\n');
            result.append("Computers\n");
            for(int i = 0; i < computers.length; i++) {
                result.append("  Item [").append(i).append("]\n");
                result.append(TextTools.indent(computers[i].dump(), 4)).append('\n');
            }
            return result.toString();
        }
    }

    ///// FIELDS //////////////////////////////////////////////////////////////
    
    private SimulationScenario scenario;
    private ModelCallbakcs callbacks;    
    private Scheduler scheduler = null;
    private ClusterModel cluster;
    private PartitionMap[] candidatePartitionMaps;
    private int selectedPartitionMapIndex = 0;

    ///// METHODS /////////////////////////////////////////////////////////////
    
    public SimulationModel(SimulationScenario scenario, ModelCallbakcs callbakcs)
    {
        this.callbacks = callbakcs;
        internalSetScenario(scenario);
    }
    
    public void setScenario(SimulationScenario scenario) {
        internalSetScenario(scenario);
    }
    
    private void internalSetScenario(SimulationScenario scenario) {
        this.scenario = (SimulationScenario)scenario.clone();
        
        if(scheduler != null)
            scheduler.stop();
        
        scheduler = null;
        cluster = null;
        candidatePartitionMaps = null;
        
        if(!regeneratePartitionMaps())
            return;
        
        scheduler = new Scheduler();        
        cluster = new ClusterModel(scenario.getClusterParams(), scheduler, 
                scenario.getCellExecutionManager(), callbacks);

        repartition();
    }
    
    public void run() {
        if(cluster == null)
            return;
        
        if(isRunning())
            stop();
        
        scheduler.start();
        cluster.run();
    }
    
    public void stop() {
        scheduler.stop();
        repartition();
        callbacks.modelRefreshed(this);
    }
    
    public void pause() {
        scheduler.pause();
    }
    
    public void resume() {
        if(cluster == null)
            return;
        
        scheduler.resume();
    }
    
    public void setSpeedFactor(double sf) {
        scheduler.setSpeedFactor(sf);
    }
    
    public boolean isRunning() {
        return scheduler.isRunning();
    }
    
    public boolean isStopped() {
        return scheduler.isStopped();
    }

    public PartitionMap getSelectedPartitionMap() {
        if(candidatePartitionMaps == null)
            return null;
        return candidatePartitionMaps[selectedPartitionMapIndex];
    }
    
    public int getSelectedPartitionMapIndex() {
        return selectedPartitionMapIndex;
    }
    
    public int getNumberOfCandidationPatitionMaps() {
        return candidatePartitionMaps.length;
    }
    
    public PartitionMap[] getCandidatePartitionMaps() {
        return candidatePartitionMaps;
    }
    
    public void setSelectedPartitionIndex(int index) {
        selectedPartitionMapIndex = index;
        repartition();
    }
    
    public void alterSize(int nRows, int nCols) {
        scenario.setNRows(nRows).setNCols(nCols);
        regeneratePartitionMaps();
        repartition();
    }
    
    public void alterPartitioning(PartitionMaker partitionMaker) {
        scenario.setPartitionMaker(partitionMaker);
        if(!regeneratePartitionMaps())
            return;
        repartition();
    }
    
    public void alterBorderProxyThinkness(int s) {
        scenario.setGhostBoundarySize(s);
        repartition();
    }
    
    private boolean regeneratePartitionMaps() {
        candidatePartitionMaps = scenario.getPartitionMaker().allPartitionCandidates(
                new GridDivision2D(scenario.getNRows(), scenario.getNCols()), 
                scenario.getClusterParams());
        
        
        if(candidatePartitionMaps == null) {
            callbacks.partitionMakerRejected(scenario.getPartitionMaker());
            selectedPartitionMapIndex = 0;
            return false;
        }
        
        int leastBorderPenalty = Integer.MAX_VALUE;
        float leastLeftoverPenalty = Float.MAX_VALUE;
        
        StringBuilder selectionReport = new StringBuilder();
        selectionReport.append("Partitioning Scores:\n");
        for(int i = 0; i < candidatePartitionMaps.length; i++) {
            PartitionMaker.Penalty p = PartitionMaker.penaltyOf(scenario.getClusterParams(), candidatePartitionMaps[i]);
            
            selectionReport.append("  [").append(i).append("] ")
                    .append(candidatePartitionMaps[i].toString())
                    .append(". Leftover Penalty: ").append(p.leftoverPenalty)
                    .append(", Border Penalty: ").append(p.borderPenalty).append('\n');
            
            if(p.leftoverPenalty < leastLeftoverPenalty) {
                leastLeftoverPenalty = p.leftoverPenalty;
                selectedPartitionMapIndex = i;
            } else if(p.leftoverPenalty == leastLeftoverPenalty && p.borderPenalty < leastBorderPenalty) {
                leastBorderPenalty = p.borderPenalty;
                selectedPartitionMapIndex = i;
            }
        }
        
        selectionReport.append("  Selected: ").append(selectedPartitionMapIndex);
        
        callbacks.message(selectionReport.toString(), false);
        
        return true;
    }
    
    public void alterExecutionManager(CellExecutionManager exeman) {
        scenario.setCellExecutionManager(exeman);
        cluster.repartition(getSelectedPartitionMap(), exeman, scenario.getGhostBoundarySize());
        cluster.reset();        
    }
    
    private void repartition() {        
        cluster.repartition(getSelectedPartitionMap(), 
                scenario.getCellExecutionManager(), 
                scenario.getGhostBoundarySize());
        cluster.reset();
        
        callbacks.modelRefreshed(this);
    }
    
    public CellInfo getCell(int absRow, int absCol) {
        if(cluster == null)
            return null;
        
        if(candidatePartitionMaps == null)
            return null;
        
        return cluster.resolveCell(absRow, absCol);
    }
    
    public boolean toggleProbe(int abdRow, int absCol) {
        Cell c = (Cell)cluster.resolveCell(abdRow, absCol);
        if(!c.isProbbed()) {
            callbacks.message(c.getDump(), false);
            System.out.println(resovleAddress(c.getAddress().getProcessLoc()).dump());
            c.setProbbed(true);            
            callbacks.message("Probbing cell: " + c.getAddress(), false);
            return true;
        } else {
            c.setProbbed(false);
            callbacks.message("Unprobbing cell: " + c.getAddress(), false);
            return false;
        }
    }
    
    private CpuThreadModel resovleAddress(WQThreadAddress address) {
        return cluster.computers[address.getComputerRank()]
                .nodes[address.getNodeRank()]
                .threads[address.getCpuGroupRank()][address.getCpuRank()];
    }
        
    public SimulationScenario getScenario() {
        return (SimulationScenario)scenario.clone();
    }
    
    public int getNRows() {
        return scenario.getNRows();
    }
    
    public int getNCols() {
        return scenario.getNCols();
    }
    
    public double currentTime() {
        return scheduler.now();
    }
    
    public double getTotalWaitingTime() {
        return cluster.getTotalWaitingTime();
    }
    
    public double getTotalComputeTime() {
        return cluster.getTotalComputeTime();
    }
    
    public double getTotalNeworkTrafficTime() {
        return cluster.getTotalNetworkTrafficTime();
    }
    
    public double getProgress() {
        return cluster.getProgress();
    }
    
    @Override
    public String toString() {
        return cluster.toString();
    }
}
