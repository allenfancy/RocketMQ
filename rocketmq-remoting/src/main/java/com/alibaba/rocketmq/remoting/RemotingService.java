package com.alibaba.rocketmq.remoting;
/**
 * 
 * @author allen
 * 远程服务
 */
public interface RemotingService {
	
	/*
	 * 开始
	 */
    public void start();

    /*
     * 关闭
     */
    public void shutdown();

    /*
     * 注册RPC 
     * @see RPCHook
     */
    public void registerRPCHook(RPCHook rpcHook);
}
