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
public class ComputeNodeParams {
    private float memoryGap;
    private float memoryLatency;
    private float memoryIOOverhead;
    private ArrayList<CpuGroupParams> cpuGroups = new ArrayList<CpuGroupParams>();

    /**
     *
     * @param memoryBandwidth in bytes per second
     * @param memoryLatency
     * @param memoryOverhead
     */
    public ComputeNodeParams(float memoryGap, float memoryLatency, float memoryIOOverhead) {
        this.memoryGap = memoryGap;
        this.memoryLatency = memoryLatency;
        this.memoryIOOverhead = memoryIOOverhead;
    }

    public float getMemoryGap() {
        return memoryGap;
    }

    public float getMemoryLatency() {
        return memoryLatency;
    }

    public float getMemoryIOOverhead() {
        return memoryIOOverhead;
    }

    public float getMessageTransmissionTime(int nbytes) {
        return nbytes * memoryGap + memoryLatency + memoryIOOverhead * 2;
    }

    public void addCpuGroup(CpuGroupParams g) {
        cpuGroups.add(g);
    }

    public int getNumberOfCpuGroups() {
        return cpuGroups.size();
    }
    
    public CpuGroupParams getCpuGroupParams(int index) {
        return cpuGroups.get(index);
    }
    
    public int[] getNumberOfCpusPerGroup() {
        int[] numbers = new int[cpuGroups.size()];
        for(int i = cpuGroups.size() - 1; i >= 0; i--) {
            numbers[i] = cpuGroups.get(i).getCount();
        }
        return numbers;
    }
    
    public float[] getCpuGroupProportions() {
        float[] proportions = new float[getNumberOfCpuGroups()];
        float sum = 0;
        
        for(int i = proportions.length - 1; i >= 0; i--) {
            proportions[i] = getCpuGroupParams(i).getTotalSpeed();
            sum += proportions[i];
        }
        
        for(int i = proportions.length - 1; i >= 0; i--) {
            proportions[i] /= sum;
        }
        
        return proportions;
    }

    public float getTotalSpeed() {
        float sum = 0;
        for (CpuGroupParams g : cpuGroups) {
            sum += g.getTotalSpeed();
        }
        return sum;
    }

    public int getTotalCpus() {
        int sum = 0;
        for (CpuGroupParams g : cpuGroups) {
            sum += g.getCount();
        }
        return sum;
    }
    
}
