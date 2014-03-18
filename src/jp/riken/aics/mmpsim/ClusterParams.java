/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author hamed
 */
public class ClusterParams {
    private ArrayList<ComputerParams> computers = new ArrayList<ComputerParams>();
    private float networkGap;
    private float networkLatency;
    private float networkOverhead;

    public ClusterParams(float networkGap, float networkLatency, float networkOverhead) {
        this.networkGap = networkGap;
        this.networkLatency = networkLatency;
        this.networkOverhead = networkOverhead;
    }

    public float getNetworkGap() {
        return networkGap;
    }

    public float getNetworkLatency() {
        return networkLatency;
    }

    public float getNetworkOverhead() {
        return networkOverhead;
    }

    public float getMessageTransmissionTime(int nbytes) {
        return nbytes * networkGap + networkLatency + networkOverhead * 2;
    }

    public void addComputer(ComputerParams computer) {
        computers.add(computer);
    }
    
    public int getNumberOfComputers() {
        return computers.size();
    }
    
    public ComputerParams getComputerParams(int index) {
        return computers.get(index);
    }
    
    public Iterator<ComputerParams> computersIterator() {
        return computers.iterator();
    }
    
    public float[] getComputerProportions() {
        float sum = 0;
        float[] proportions = new float[getNumberOfComputers()];
        
        for(int i = proportions.length - 1; i >= 0; i--) {
            proportions[i] = getComputerParams(i).getTotalSpeed();
            sum += proportions[i];
        }
        
        for(int i = proportions.length - 1; i >= 0; i--) {
            proportions[i] /= sum;
        }
        
        return proportions;
    }
    
    public float getTotalSpeed() {
        float sum = 0;
        for (ComputerParams c : computers) {
            sum += c.getTotalSpeed();
        }
        return sum;
    }
    
    public int getTotalCpus() {
        int sum = 0;
        for (ComputerParams c : computers) {
            sum += c.getNodeParams().getTotalCpus() * c.getNumberOfNodes();
        }
        return sum;
    }
}
