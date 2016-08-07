package com.alibaba.rocketmq.common;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Description 
 * 线程工厂类
 * @author allen
 *
 */
public class ThreadFactoryImpl implements ThreadFactory {
	//线程的下标
    private final AtomicLong threadIndex = new AtomicLong(0);
    //线程名称前缀
    private final String threadNamePrefix;


    public ThreadFactoryImpl(final String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }


    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, threadNamePrefix + this.threadIndex.incrementAndGet());

    }
}
