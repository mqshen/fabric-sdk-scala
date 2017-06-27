package com.ynet.belink.common;

import java.util.Map;

/**
 * Created by goldratio on 27/06/2017.
 */
public interface Configurable {

    /**
     * Configure this class with the given key-value pairs
     */
    void configure(Map<String, ?> configs);

}
