/**
 * Copyright (C) 2010-2013 Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.rocketmq.remoting;

import java.util.List;
import java.util.concurrent.ExecutorService;

import com.alibaba.rocketmq.remoting.exception.RemotingConnectException;
import com.alibaba.rocketmq.remoting.exception.RemotingSendRequestException;
import com.alibaba.rocketmq.remoting.exception.RemotingTimeoutException;
import com.alibaba.rocketmq.remoting.exception.RemotingTooMuchRequestException;
import com.alibaba.rocketmq.remoting.netty.NettyRequestProcessor;
import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;


/**
 * 远程通信，Client接口
 * 
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-13
 */
public interface RemotingClient extends RemotingService {

	/*
	 * 更新NameServer地址列表
	 */
    public void updateNameServerAddressList(final List<String> addrs);

    /*
     * 获取NameServer地址类别
     */
    public List<String> getNameServerAddressList();

    /*
     * 同步调用
     */
    public RemotingCommand invokeSync(final String addr, final RemotingCommand request,
            final long timeoutMillis) throws InterruptedException, RemotingConnectException,
            RemotingSendRequestException, RemotingTimeoutException;

    /**
     * @description 			异步调用
     * @param addr				请求地址
     * @param request			远程请求协议
     * @param timeoutMillis		超时时间
     * @param invokeCallback	异步调用应答回调接口
     * @throws InterruptedException
     * @throws RemotingConnectException
     * @throws RemotingTooMuchRequestException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     */
    public void invokeAsync(final String addr, final RemotingCommand request, final long timeoutMillis,
            final InvokeCallback invokeCallback) throws InterruptedException, RemotingConnectException,
            RemotingTooMuchRequestException, RemotingTimeoutException, RemotingSendRequestException;

   /**
    * @description  									一种方式调用
    * @param addr										地址
    * @param request									远程请求协议
    * @param timeoutMillis								链接超时时间
    * @throws InterruptedException
    * @throws RemotingConnectException
    * @throws RemotingTooMuchRequestException
    * @throws RemotingTimeoutException
    * @throws RemotingSendRequestException
    */
    public void invokeOneway(final String addr, final RemotingCommand request, final long timeoutMillis)
            throws InterruptedException, RemotingConnectException, RemotingTooMuchRequestException,
            RemotingTimeoutException, RemotingSendRequestException;

    /**
     * @description 		注册处理器
     * @param requestCode 	请求Code
     * @param processor		Netty请求处理器
     * @param executor		执行线程池
     */
    public void registerProcessor(final int requestCode, final NettyRequestProcessor processor,
            final ExecutorService executor);

    /**
     * @description 通道是否可以写
     * @param addr	地址
     * @return 
     */
    public boolean isChannelWriteable(final String addr);
}
