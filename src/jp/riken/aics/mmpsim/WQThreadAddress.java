/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.util.Arrays;

/**
 *
 * @author hamed
 */
public class WQThreadAddress {
    public static int SIZE_IN_BITS = Integer.SIZE * 4;
    
    private int[] address;
    
    public WQThreadAddress(int[] address) {
        this.address = new int[4];
        Arrays.fill(this.address, -1);
        for(int i = 0; i < address.length && i < 4; i++)
            this.address[i] = address[i];
    }
    
    public WQThreadAddress(int computer, int node, int cpuGroup, int cpu) {
        address = new int[]{computer, node, cpuGroup, cpu};
    }
    
    public int getComputerRank() {
        return address[0];
    }
    
    public int getNodeRank() {
        return address[1];
    }
    
    public int getCpuGroupRank() {
        return address[2];
    }
    
    public int getCpuRank() {
        return address[3];
    }
    
    public boolean includes(WQThreadAddress other) {
        int i = 0;
        for(; i < address.length; i++) {
            if(address[i] == -1)
                break;
            if(address[i] != other.address[i])
                return false;
        }
        for(; i < address.length; i++) {
            if(address[i] != -1)
                return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof WQThreadAddress)) 
            return false;
        return Arrays.equals(((WQThreadAddress)other).address, address);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + Arrays.hashCode(this.address);
        return hash;
    }
    
    @Override
    public String toString() {
        return Arrays.toString(address);
    }
}
