/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import net.hkhandan.util.Selector;

/**
 *
 * @author hamed
 */
public enum Direction {
    ABOVE,
    BELOW,
    LEFT,
    RIGHT,
    UPPER_RIGHT,
    LOWER_RIGHT,
    UPPER_LEFT,
    LOWER_LEFT;
    
    public Direction getOpposite() {
        return Selector.with(this, this)
                .when(ABOVE).is(BELOW)
                .when(BELOW).is(ABOVE)
                .when(LEFT).is(RIGHT)
                .when(RIGHT).is(LEFT)
                .when(UPPER_RIGHT).is(LOWER_LEFT)
                .when(LOWER_LEFT).is(UPPER_RIGHT)
                .when(UPPER_LEFT).is(LOWER_RIGHT)
                .when(LOWER_RIGHT).is(UPPER_LEFT)
                .end();
    }
}
