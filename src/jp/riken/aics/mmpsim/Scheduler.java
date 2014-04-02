/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 *
 * @author hamed
 */
public class Scheduler {
    
    ///// NESTED DATA TYPES ///////////////////////////////////////////////////
    
    public static interface ScheduleToken {
        // Nothing.
    }
    
    private static class ScheduledItem implements ScheduleToken {
        public double triggerTime = 0;
        public Runnable task;
        public ScheduledItem preceeder = null;
        public ScheduledItem dependant = null;
        public Scheduler creator;
    }
    
    private class Runner extends Thread {
        
        boolean isStopped;
        
        public Runner() {
            setName("Scheduler Thread " + runnerCounter);
            isStopped = false;
            runnerCounter++;
        }
        
        @Override
        public void run() {
            while(true) {
                if(isStopped)
                    return;
                
                long currentSystemTime = System.currentTimeMillis();
                long systemTimeDiff = currentSystemTime - lastSystemTime;
                double targetTime = now + systemTimeDiff * TIME_SCALE * speedFactor;
                lastSystemTime = currentSystemTime;
                
                boolean allClear = true;
                
                do {
                    allClear = true;
                    
                    synchronized(newItemsLock) {
                        if(newItems.size() > 0) {
                            for(ScheduledItem item : newItems) {
                                schedule(item);
                            }
                            newItems.clear();
                        }
                    }

                    Iterator<ScheduledItem> it = list.descendingIterator();
                    while(it.hasNext()) {
                        ScheduledItem item = it.next();
                        if(item.triggerTime <= targetTime) {
                            now = item.triggerTime;
                            item.task.run();
                            item.preceeder = null;
                            item.dependant = null;
                            it.remove();
                            allClear = false;
                        }
                    }
                } while(!allClear);
                
                now = targetTime;
                
                if(isStopped)
                    return;
                
                try {
                    sleep(33);
                } catch (InterruptedException ex) {
                    // nothing.
                }
            }
        }
        
        public void die() {
            isStopped = true;
        }
        
        public boolean isRunning() {
            return !isStopped;
        }
    }
    
    ///// FIELDS //////////////////////////////////////////////////////////////
    private final static double TIME_SCALE = 0.3;
    
    private static int runnerCounter = 0;
    
    private double   speedFactor = 1;
    private double   now = 0;
    private long     lastSystemTime = 0;    
    private Runner   runner = null;
    private Runnable runOnException = null;
    private LinkedList<ScheduledItem> list;
    private ArrayList<ScheduledItem>  newItems;
    
    private final Object newItemsLock = new Object();
    
    ///// METHODS /////////////////////////////////////////////////////////////
  
    public Scheduler() {
        list = new LinkedList<ScheduledItem>();
        newItems = new ArrayList<ScheduledItem>();
    }
    
    public void setSpeedFactor(double sf) {
        speedFactor = sf;
    }
    
    public double getSpeedFactor() {
        return speedFactor;
    }
    
    public final void start() {
        if(runner != null)
            if(runner.isRunning())
                return;
        
        now = 0;
        lastSystemTime = System.currentTimeMillis();
        runner = new Runner();
        runner.start();
    }
    
    public boolean isRunning() {
        if(runner == null)
            return false;
        
        return runner.isRunning();
    }
    
    public boolean isStopped() {
        return runner == null;
    }
    
    public void stop() {
        if(runner != null) {
            runner.die();
            runner = null;
            list.clear();
            newItems.clear();
            synchronized(newItemsLock) {
                newItems.clear();
            }
        }
    }
    
    public void pause() {
        if(runner != null) {
            runner.die();
            runner = null;
        }
    }
    
    public void resume() {
        if(runner != null)
            return;        
        lastSystemTime = System.currentTimeMillis();
        runner = new Runner();
        runner.start();
    }
        
    public double now() {
        return now;
    }
    
    public double getNow2() {
        long currentSystemTime = System.currentTimeMillis();
        long systemTimeDiff = currentSystemTime - lastSystemTime;
        return now + systemTimeDiff * TIME_SCALE * speedFactor;        
    }
    
    public ScheduleToken runWithDelay(Runnable task, double delay) {
        ScheduledItem newItem = new ScheduledItem();
        newItem.triggerTime = now + delay;
        newItem.task = task;
        newItem.creator = this;
        
        synchronized(newItemsLock) {
            newItems.add(newItem);        
        }
        
        return newItem;
    }
    
    public ScheduleToken runWithDelayAfter(Runnable task, double delay, ScheduleToken preceeder) {
        if(preceeder == null) {
            return runWithDelay(task, delay);
        }
        
        if(!(preceeder instanceof ScheduledItem)) {
            throw new IllegalArgumentException("Only objects created by the scheduler are accepted as the last argument.");
        }
        
        ScheduledItem preceedingItem = (ScheduledItem)preceeder;
        if(preceedingItem.creator != this) {
            throw new IllegalArgumentException("Only objects created by the scheduler are accepted as the last argument.");
        }

        ScheduledItem newItem = new ScheduledItem();
        newItem.triggerTime = now + delay;
        
        if(preceedingItem.triggerTime > now) {
            newItem.triggerTime = preceedingItem.triggerTime + delay;
        }
        
        newItem.task = task;
        newItem.creator = this;
        newItem.preceeder = preceedingItem;
        preceedingItem.dependant = newItem;
        
        synchronized(newItemsLock) {
            newItems.add(newItem);        
        }
        
        return newItem;
    }
    
    private void schedule(ScheduledItem newItem) {
        ListIterator<ScheduledItem> it = list.listIterator();
        
        while(true) {
            if(!it.hasNext()) {
                it.add(newItem);
                break;
            }

            ScheduledItem listItem = it.next();
            if(listItem.triggerTime < newItem.triggerTime) {
                it.previous();
                it.add(newItem);
                break;
            }
        }
    }
    
    public static void main(String[] args) {        
        Scheduler runner = new Scheduler();
        
        runner.start();
        
        for(int i = 0; i < 20; i++) {            
            final int id = i;
            final double d = Math.random() * 100;
            Runnable sch = new Runnable() {
               @Override
                public void run() {
                   System.out.println(d + " - " + id);
                }
            };
            runner.runWithDelay(sch, d);
        }
        
        runner.runWithDelay(new Runnable() {
            @Override
            public void run() {
                System.exit(0);
            }
        }, 110);
       
    }
}
