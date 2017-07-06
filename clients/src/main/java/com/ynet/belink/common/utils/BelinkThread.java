package com.ynet.belink.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by goldratio on 05/07/2017.
 */
public class BelinkThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static BelinkThread daemon(final String name, Runnable runnable) {
        return new BelinkThread(name, runnable, true);
    }

    public static BelinkThread nonDaemon(final String name, Runnable runnable) {
        return new BelinkThread(name, runnable, false);
    }

    public BelinkThread(final String name, boolean daemon) {
        super(name);
        configureThread(name, daemon);
    }

    public BelinkThread(final String name, Runnable runnable, boolean daemon) {
        super(runnable, name);
        configureThread(name, daemon);
    }

    private void configureThread(final String name, boolean daemon) {
        setDaemon(daemon);
        setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Uncaught exception in thread '{}':", name, e);
            }
        });
    }

}
