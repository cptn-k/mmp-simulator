/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

/**
 *
 * @author hamed
 */
public class ComputerParams {
    private ComputeNodeParams nodeConfiguration;
    private int nodeCount;
    private float interconnectGap;
    private float interconnectLatency;
    private float interconnectOverhead;

    public ComputerParams(ComputeNodeParams nodeConfiguration, int count, float interconnectGap, float interconnectLatency, float interconnectOverhead) {
        this.nodeConfiguration = nodeConfiguration;
        this.nodeCount = count;
        this.interconnectGap = interconnectGap;
        this.interconnectLatency = interconnectLatency;
        this.interconnectOverhead = interconnectOverhead;
    }

    public float getInterconnectGap() {
        return interconnectGap;
    }

    public float getInterconnectLatency() {
        return interconnectLatency;
    }

    public float getInterconnectOverhead() {
        return interconnectOverhead;
    }

    public float getMessageTransmissionTime(int nbytes) {
        return nbytes * interconnectGap + interconnectLatency + interconnectOverhead * 2;
    }

    public ComputeNodeParams getNodeParams() {
        return nodeConfiguration;
    }

    public int getNumberOfNodes() {
        return nodeCount;
    }

    public float getTotalSpeed() {
        return nodeConfiguration.getTotalSpeed() * nodeCount;
    }
    
}
