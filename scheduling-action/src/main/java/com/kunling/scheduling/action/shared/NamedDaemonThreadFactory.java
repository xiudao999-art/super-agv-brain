package com.kunling.scheduling.action.shared;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 为 JDK 8 线程池提供可识别、且不会阻止 JVM 退出的后台线程。 */
public final class NamedDaemonThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final AtomicInteger sequence = new AtomicInteger();

    public NamedDaemonThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, namePrefix + sequence.incrementAndGet());
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((failedThread, error) ->
                java.util.logging.Logger.getLogger(NamedDaemonThreadFactory.class.getName())
                        .log(java.util.logging.Level.SEVERE,
                                failedThread.getName() + " 未捕获异常", error));
        return thread;
    }
}
