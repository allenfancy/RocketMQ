package com.alibaba.rocketmq.remoting;

import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;

/**
 * RPC钩子
 * @author allen
 *
 */
public interface RPCHook {
	/**
	 * @description 		处理请求之前
	 * @param remoteAddr
	 * @param request
	 */
    public void doBeforeRequest(final String remoteAddr, final RemotingCommand request);

    /**
     * @description			处理请求之后
     * @param remoteAddr
     * @param request
     * @param response
     */
    public void doAfterResponse(final String remoteAddr, final RemotingCommand request,
            final RemotingCommand response);
}
