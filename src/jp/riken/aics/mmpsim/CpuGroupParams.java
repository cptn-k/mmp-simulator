/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

///// NESTED DATA TYPES ///////////////////////////////////////////////////

public class CpuGroupParams {
    private float speed;
    private int count;

    public CpuGroupParams(float speed, int count) {
        this.speed = speed;
        this.count = count;
    }

    public float getTotalSpeed() {
        return speed * count;
    }

    public float getIndividualSpeed() {
        return speed;
    }

    public int getCount() {
        return count;
    }
    
    @Override
    public boolean equals(Object other) {
        if(!(other instanceof CpuGroupParams))
            return false;
        CpuGroupParams o = (CpuGroupParams)other;
        return (o.count == count && o.speed == speed);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + Float.floatToIntBits(this.speed);
        hash = 47 * hash + this.count;
        return hash;
    }
}
