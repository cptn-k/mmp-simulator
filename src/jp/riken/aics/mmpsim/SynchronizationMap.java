/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author hamed
 */
public class SynchronizationMap {
    public final static WQCellAddress BARRIER = new WQCellAddress(null, 0, 0, 0, 0);
    public final static WQCellAddress BOUNDARY_EXCHANGE_TRIGGER = new WQCellAddress(null, 0, 0, 0, 0);
    
    public static class Connection {
        public final static int SIZE_IN_BITS = WQCellAddress.SIZE_IN_BITS * 2;
        public final WQCellAddress origin;
        public final WQCellAddress target;

        public Connection(WQCellAddress origin, WQCellAddress target) {
            this.origin = origin;
            this.target = target;
        }
        
        @Override
        public String toString() {
            return origin.toString() + " --> " + target.toString();
        }
    }
    
    private static class Group {
        public final WQThreadAddress address;
        public final ArrayList<Connection> incommingConnections = new ArrayList<Connection>();
        public final ArrayList<Connection> outgoingConnections = new ArrayList<Connection>();
        public Group(WQThreadAddress address) {
            this.address = address;
        }
    }
    
    private ArrayList<Group> groups = new ArrayList<Group>();
    
    private Group findOrMakeGroup(WQThreadAddress loc) {
         for(Group g : groups) {
             if(g.address.equals(loc)) {
                 return g;
             }
         }
         Group g = new Group(loc);
         groups.add(g);
         return g;
    }
    
    public void add(WQCellAddress origin, WQCellAddress target) {
        Connection c = new Connection(origin, target);
        
        Group sourceGroup = findOrMakeGroup(origin.getProcessLoc());
        sourceGroup.outgoingConnections.add(c);
        
        if(target.getProcessLoc() != null) {
            Group targetGroup = findOrMakeGroup(target.getProcessLoc());
            targetGroup.incommingConnections.add(c);
        }
    }
    
    public List<Connection> getOutgoingConnectionFrom(WQThreadAddress loc) {
        return findOrMakeGroup(loc).outgoingConnections;
    }
    
    public List<Connection> getIncommingConnectionsTo(WQThreadAddress loc) {
        return findOrMakeGroup(loc).incommingConnections;
    }    
    
    public int getSizeInBits() {
        int sum = 0;
        for(Group g : groups) {
            sum += g.outgoingConnections.size() * Connection.SIZE_IN_BITS;
            sum += g.outgoingConnections.size() * Connection.SIZE_IN_BITS;
            sum += WQThreadAddress.SIZE_IN_BITS;
        }
        return sum;
    }
    
    public String dump() {
        StringBuilder builder = new StringBuilder();
        for(Group g : groups) {
            builder.append(g.address.toString()).append('\n');
            builder.append("  Outgoing:\n");
            for(Connection c : g.outgoingConnections) {
                builder.append("    ").append(c.toString()).append('\n');
            }
            builder.append("  Incomming:\n");
            for(Connection c : g.incommingConnections) {
                builder.append("    ").append(c.toString()).append('\n');
            }
        }
        return builder.toString();
    }
}
