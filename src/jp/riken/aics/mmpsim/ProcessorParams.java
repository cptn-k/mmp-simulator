/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

///// NESTED DATA TYPES ///////////////////////////////////////////////////

public class ProcessorParams {
    public static enum Type {
        CPU,
        CUDA
    };
    
    private final int nMultiProcessors;
    private final int nCores;
    private final float speedPerCore;
    private final Type type;

    public ProcessorParams(int nCores, float speed) {
        this.nCores = nCores;
        this.speedPerCore = speed;
        this.type = Type.CPU;
        this.nMultiProcessors = 1;
    }
    
    public ProcessorParams(int nMultiProcessors, int nCores, float speed) {
        this.nMultiProcessors = nMultiProcessors;
        this.speedPerCore = speed;
        this.nCores = nCores;
        this.type = Type.CUDA;
    }

    public float getTotalSpeed() {
        return speedPerCore * nCores * nMultiProcessors;
    }

    public float getSpeedPerCore() {
        return speedPerCore;
    }

    public int getNCores() {
        return nCores;
    }
    
    public int getNMultiprocessors() {
        return nMultiProcessors;
    }
    
    public Type getType() {
        return type;
    }
    
    @Override
    public boolean equals(Object other) {
        if(!(other instanceof ProcessorParams))
            return false;
        ProcessorParams o = (ProcessorParams)other;
        return (o.nCores == nCores 
                && o.speedPerCore == speedPerCore
                && o.type == type
                && o.nMultiProcessors == nMultiProcessors);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + this.nMultiProcessors;
        hash = 73 * hash + this.nCores;
        hash = 73 * hash + Float.floatToIntBits(this.speedPerCore);
        hash = 73 * hash + (this.type != null ? this.type.hashCode() : 0);
        return hash;
    }
}
