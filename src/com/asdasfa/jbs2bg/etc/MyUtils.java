package com.asdasfa.jbs2bg.etc;

import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author Totiman
 */
public class MyUtils {

    /**
     *
     * @param min
     * @param max
     * @return a random int between min (inclusive) and max (inclusive)
     */
    public static int random(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
