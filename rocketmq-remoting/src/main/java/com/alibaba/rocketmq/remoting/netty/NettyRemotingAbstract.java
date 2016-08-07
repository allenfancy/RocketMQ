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
package com.alibaba.rocketmq.remoting.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.rocketmq.remoting.ChannelEventListener;
import com.alibaba.rocketmq.remoting.InvokeCallback;
import com.alibaba.rocketmq.remoting.RPCHook;
import com.alibaba.rocketmq.remoting.common.Pair;
import com.alibaba.rocketmq.remoting.common.RemotingHelper;
import com.alibaba.rocketmq.remoting.common.SemaphoreReleaseOnlyOnce;
import com.alibaba.rocketmq.remoting.common.ServiceThread;
import com.alibaba.rocketmq.remoting.exception.RemotingSendRequestException;
import com.alibaba.rocketmq.remoting.exception.RemotingTimeoutException;
import com.alibaba.rocketmq.remoting.exception.RemotingTooMuchRequestException;
import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;
import com.alibaba.rocketmq.remoting.protocol.RemotingSysResponseCode;


/**
 * @Description Server 与 Client  公用抽象类
 * 
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-13
 */
public abstract class NettyRemotingAbstract {
	
    private static final Logger plog = LoggerFactory.getLogger(RemotingHelper.RemotingLogName);

    // 信号量，Oneway情况会使用，防止本地Netty缓存请求过多
    protected final Semaphore semaphoreOneway;

    // 信号量，异步调用情况会使用，防止本地Netty缓存请求过多
    protected final Semaphore semaphoreAsync;

    // 缓存所有对外请求的应答
    protected final ConcurrentHashMap<Integer /* opaque */, ResponseFuture> responseTable =
            new ConcurrentHashMap<Integer, ResponseFuture>(256);

    // 默认请求代码处理器
    protected Pair<NettyRequestProcessor, ExecutorService> defaultRequestProcessor;

    // 注册的各个RPC处理器
    protected final HashMap<Integer/* request code */, Pair<NettyRequestProcessor, ExecutorService>> processorTable =
            new HashMap<Integer, Pair<NettyRequestProcessor, ExecutorService>>(64);

    //Netty事件执行者
    protected final NettyEventExecuter nettyEventExecuter = new NettyEventExecuter();

    //事件通道
    public abstract ChannelEventListener getChannelEventListener();


    public abstract RPCHook getRPCHook();


    public void putNettyEvent(final NettyEvent event) {
        this.nettyEventExecuter.putNettyEvent(event);
    }
    
    //Netty事件 执行者 继承  服务线程
    class NettyEventExecuter extends ServiceThread {
    	//链接类型阻塞队列
        private final LinkedBlockingQueue<NettyEvent> eventQueue = new LinkedBlockingQueue<NettyEvent>();
        //最大尺寸，阻塞队列的大小
        private final int MaxSize = 10000;
        
        //放入Netty事件队列中
        public void putNettyEvent(final NettyEvent event) {
            if (this.eventQueue.size() <= MaxSize) {
                this.eventQueue.add(event);
            }
            else {
                plog.warn("event queue size[{}] enough, so drop this event {}", this.eventQueue.size(),
                    event.toString());
            }
        }


        @Override
        public void run() {
            plog.info(this.getServiceName() + " service started");
            
            //通道事件监听器
            final ChannelEventListener listener = NettyRemotingAbstract.this.getChannelEventListener();
            while (!this.isStoped()) { //当线程没有停止的时候，
                try {
                	//每隔3秒从阻塞队列去拿出事件，进行消费
                    NettyEvent event = this.eventQueue.poll(3000, TimeUnit.MILLISECONDS);
                    if (event != null && listener != null) {
                        switch (event.getType()) {
                        case IDLE:
                            listener.onChannelIdle(event.getRemoteAddr(), event.getChannel());
                            break;
                        case CLOSE:
                            listener.onChannelClose(event.getRemoteAddr(), event.getChannel());
                            break;
                        case CONNECT:
                            listener.onChannelConnect(event.getRemoteAddr(), event.getChannel());
                            break;
                        case EXCEPTION:
                            listener.onChannelException(event.getRemoteAddr(), event.getChannel());
                            break;
                        default:
                            break;
                        }
                    }
                }
                catch (Exception e) {
                    plog.warn(this.getServiceName() + " service has exception. ", e);
                }
            }
            plog.info(this.getServiceName() + " service end");
        }


        @Override
        public String getServiceName() {
            return NettyEventExecuter.class.getSimpleName();
        }
    }


    public NettyRemotingAbstract(final int permitsOneway, final int permitsAsync) {
        this.semaphoreOneway = new Semaphore(permitsOneway, true);
        this.semaphoreAsync = new Semaphore(permitsAsync, true);
    }

    /**
     * 处理【请求命令】的方法
     * @param ctx
     * @param cmd
     */
    public void processRequestCommand(final ChannelHandlerContext ctx, final RemotingCommand cmd) {
    	//根据远程命令的code从ProcessorTable的缓冲中获取
        final Pair<NettyRequestProcessor, ExecutorService> matched = this.processorTable.get(cmd.getCode());
        //如果不存在缓冲中没有匹配到的话，使用defaultRequestProcessor，否则使用matched
        final Pair<NettyRequestProcessor, ExecutorService> pair =
                null == matched ? this.defaultRequestProcessor : matched;
        //如果不为空的话
        if (pair != null) {
            Runnable run = new Runnable() {
                @Override
                public void run() {
                    try {
                    	//获取RPCHook
                        RPCHook rpcHook = NettyRemotingAbstract.this.getRPCHook();
                        if (rpcHook != null) {
                            rpcHook//处理前的请求，
                                .doBeforeRequest(RemotingHelper.parseChannelRemoteAddr(ctx.channel()), cmd);
                        }
                        //获取请求处理器，处理请求，并获取响应的结果
                        final RemotingCommand response = pair.getObject1().processRequest(ctx, cmd);
                        if (rpcHook != null) {
                        	//处理请求之后
                            rpcHook.doAfterResponse(RemotingHelper.parseChannelRemoteAddr(ctx.channel()),
                                cmd, response);
                        }
                        //如果是OneWayRPC的话进行操作
                        if (!cmd.isOnewayRPC()) {
                            if (response != null) {
                                response.setOpaque(cmd.getOpaque());
                                response.markResponseType();
                                try {
                                	//写数据并且刷新数据
                                    ctx.writeAndFlush(response);
                                }
                                catch (Throwable e) {
                                    plog.error("process request over, but response failed", e);
                                    plog.error(cmd.toString());
                                    plog.error(response.toString());
                                }
                            }
                            else {
                                // 收到请求，但是没有返回应答，可能是processRequest中进行了应答，忽略这种情况
                            }
                        }
                    }
                    catch (Throwable e) {
                        plog.error("process request exception", e);
                        plog.error(cmd.toString());
                        
                        if (!cmd.isOnewayRPC()) {
                            final RemotingCommand response =
                                    RemotingCommand.createResponseCommand(
                                        RemotingSysResponseCode.SYSTEM_ERROR,//
                                        RemotingHelper.exceptionSimpleDesc(e));
                            response.setOpaque(cmd.getOpaque());
                            ctx.writeAndFlush(response);
                        }
                    }
                }
            };

            try {
                // 这里需要做流控，要求线程池对应的队列必须是有大小限制的
            	// 提交线程任务到线程池中
                pair.getObject2().submit(run);
            }
            catch (RejectedExecutionException e) { //提交任务的时候，如果超过了线程池的大小，就会使用拒绝策略抛出异常
                // 每个线程10s打印一次
                if ((System.currentTimeMillis() % 10000) == 0) {
                    plog.warn(RemotingHelper.parseChannelRemoteAddr(ctx.channel()) //
                            + ", too many requests and system thread pool busy, RejectedExecutionException " //
                            + pair.getObject2().toString() //
                            + " request code: " + cmd.getCode());
                }
                
                if (!cmd.isOnewayRPC()) {
                    final RemotingCommand response =
                            RemotingCommand.createResponseCommand(RemotingSysResponseCode.SYSTEM_BUSY,
                                "too many requests and system thread pool busy, please try another server");
                    response.setOpaque(cmd.getOpaque());
                    ctx.writeAndFlush(response);
                }
            }
        }//这是pair不为空的处理
        else {//这是pair为空的处理
            String error = " request type " + cmd.getCode() + " not supported";
            final RemotingCommand response =
                    RemotingCommand.createResponseCommand(RemotingSysResponseCode.REQUEST_CODE_NOT_SUPPORTED,
                        error);
            response.setOpaque(cmd.getOpaque());
            ctx.writeAndFlush(response);
            plog.error(RemotingHelper.parseChannelRemoteAddr(ctx.channel()) + error);
        }
    }

    //处理响应命令
    public void processResponseCommand(ChannelHandlerContext ctx, RemotingCommand cmd) {
    	//请求应答封装
        final ResponseFuture responseFuture = responseTable.get(cmd.getOpaque());
        if (responseFuture != null) {
            responseFuture.setResponseCommand(cmd);
            responseFuture.release();
            responseTable.remove(cmd.getOpaque());

            if (responseFuture.getInvokeCallback() != null) {
                boolean runInThisThread = false;
                ExecutorService executor = this.getCallbackExecutor();
                if (executor != null) {
                    try {
                        executor.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    responseFuture.executeInvokeCallback();
                                }
                                catch (Throwable e) {
                                    plog.warn("excute callback in executor exception, and callback throw", e);
                                }
                            }
                        });
                    }
                    catch (Exception e) {
                        runInThisThread = true;
                        plog.warn("excute callback in executor exception, maybe executor busy", e);
                    }
                }
                else {
                    runInThisThread = true;
                }

                if (runInThisThread) {
                    try {
                        responseFuture.executeInvokeCallback();
                    }
                    catch (Throwable e) {
                        plog.warn("executeInvokeCallback Exception", e);
                    }
                }
            }
            else {
                responseFuture.putResponse(cmd);
            }
        }
        else {
            plog.warn("receive response, but not matched any request, "
                    + RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
            plog.warn(cmd.toString());
        }

    }

    /**
     * 处理接收到的信息
     * @param ctx
     * @param msg
     * @throws Exception
     */
    public void processMessageReceived(ChannelHandlerContext ctx, RemotingCommand msg) throws Exception {
        final RemotingCommand cmd = msg;
        if (cmd != null) {
            switch (cmd.getType()) {
            case REQUEST_COMMAND: //请求命令处理
                processRequestCommand(ctx, cmd);
                break;
            case RESPONSE_COMMAND://响应命名
                processResponseCommand(ctx, cmd);
                break;
            default:
                break;
            }
        }
    }


    abstract public ExecutorService getCallbackExecutor();

    /**
     * @description 扫描缓存响应的MAP集合
     */
    public void scanResponseTable() {
        Iterator<Entry<Integer, ResponseFuture>> it = this.responseTable.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Integer, ResponseFuture> next = it.next();
            ResponseFuture rep = next.getValue();

            if ((rep.getBeginTimestamp() + rep.getTimeoutMillis() + 1000) <= System.currentTimeMillis()) {
                it.remove();
                try {
                	//执行回调
                    rep.executeInvokeCallback();
                }
                catch (Throwable e) {
                    plog.warn("scanResponseTable, operationComplete Exception", e);
                }
                finally {
                    rep.release();
                }

                plog.warn("remove timeout request, " + rep);
            }
        }
    }

    /***
     * @Descirption 					调用同步实现
     * @param channel					netty的通道
     * @param request					请求命令
     * @param timeoutMillis				请求超时时间
     * @return
     * @throws InterruptedException
     * @throws RemotingSendRequestException
     * @throws RemotingTimeoutException
     */
    public RemotingCommand invokeSyncImpl(final Channel channel, final RemotingCommand request,
            final long timeoutMillis) throws InterruptedException, RemotingSendRequestException,
            RemotingTimeoutException {
        try {
        	//根据条件创建 ResponseFuture
            final ResponseFuture responseFuture =
                    new ResponseFuture(request.getOpaque(), timeoutMillis, null, null);
            this.responseTable.put(request.getOpaque(), responseFuture);
            //向通道中写入并且Flush数据，并且添加监听器
            channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) throws Exception {
                    if (f.isSuccess()) {
                        responseFuture.setSendRequestOK(true);
                        return;
                    }
                    else {
                        responseFuture.setSendRequestOK(false);
                    }

                    responseTable.remove(request.getOpaque());
                    responseFuture.setCause(f.cause());
                    responseFuture.putResponse(null);
                    plog.warn("send a request command to channel <" + channel.remoteAddress() + "> failed.");
                    plog.warn(request.toString());
                }
            });
            //等待时间
            RemotingCommand responseCommand = responseFuture.waitResponse(timeoutMillis);
            if (null == responseCommand) {
                if (responseFuture.isSendRequestOK()) { //远程请求超时
                    throw new RemotingTimeoutException(RemotingHelper.parseChannelRemoteAddr(channel),
                        timeoutMillis, responseFuture.getCause());
                }
                else {
                	//请求异常
                    throw new RemotingSendRequestException(RemotingHelper.parseChannelRemoteAddr(channel),
                        responseFuture.getCause());
                }
            }

            return responseCommand;
        }
        finally {
        	//最终移除缓存
            this.responseTable.remove(request.getOpaque());
        }
    }

    /***
     * @Description     	调用异步实现
     * @param channel
     * @param request
     * @param timeoutMillis
     * @param invokeCallback
     * @throws InterruptedException
     * @throws RemotingTooMuchRequestException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     */
    public void invokeAsyncImpl(final Channel channel, final RemotingCommand request,
            final long timeoutMillis, final InvokeCallback invokeCallback) throws InterruptedException,
            RemotingTooMuchRequestException, RemotingTimeoutException, RemotingSendRequestException {
        boolean acquired = this.semaphoreAsync.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
        if (acquired) {
            final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(this.semaphoreAsync);

            final ResponseFuture responseFuture =
                    new ResponseFuture(request.getOpaque(), timeoutMillis, invokeCallback, once);
            this.responseTable.put(request.getOpaque(), responseFuture);
            try {
                channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture f) throws Exception {
                        if (f.isSuccess()) {
                            responseFuture.setSendRequestOK(true);
                            return;
                        }
                        else {
                            responseFuture.setSendRequestOK(false);
                        }

                        responseFuture.putResponse(null);
                        responseTable.remove(request.getOpaque());
                        try {
                            responseFuture.executeInvokeCallback();
                        }
                        catch (Throwable e) {
                            plog.warn("excute callback in writeAndFlush addListener, and callback throw", e);
                        }
                        finally {
                            responseFuture.release();
                        }

                        plog.warn("send a request command to channel <{}> failed.",
                            RemotingHelper.parseChannelRemoteAddr(channel));
                        plog.warn(request.toString());
                    }
                });
            }
            catch (Exception e) {
                responseFuture.release();
                plog.warn(
                    "send a request command to channel <" + RemotingHelper.parseChannelRemoteAddr(channel)
                            + "> Exception", e);
                throw new RemotingSendRequestException(RemotingHelper.parseChannelRemoteAddr(channel), e);
            }
        }
        else {
            if (timeoutMillis <= 0) {
                throw new RemotingTooMuchRequestException("invokeAsyncImpl invoke too fast");
            }
            else {
                String info =
                        String
                            .format(
                                "invokeAsyncImpl tryAcquire semaphore timeout, %dms, waiting thread nums: %d semaphoreAsyncValue: %d", //
                                timeoutMillis,//
                                this.semaphoreAsync.getQueueLength(),//
                                this.semaphoreAsync.availablePermits()//
                            );
                plog.warn(info);
                plog.warn(request.toString());
                throw new RemotingTimeoutException(info);
            }
        }
    }

    /***
     * @Description				调用OneWay的实现
     * @param channel
     * @param request
     * @param timeoutMillis
     * @throws InterruptedException
     * @throws RemotingTooMuchRequestException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     */
    public void invokeOnewayImpl(final Channel channel, final RemotingCommand request,
            final long timeoutMillis) throws InterruptedException, RemotingTooMuchRequestException,
            RemotingTimeoutException, RemotingSendRequestException {
    	
        request.markOnewayRPC();
        boolean acquired = this.semaphoreOneway.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
        if (acquired) {
            final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(this.semaphoreOneway);
            try {
                channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture f) throws Exception {
                        once.release();
                        if (!f.isSuccess()) {
                            plog.warn("send a request command to channel <" + channel.remoteAddress()
                                    + "> failed.");
                            plog.warn(request.toString());
                        }
                    }
                });
            }
            catch (Exception e) {
                once.release();
                plog.warn("write send a request command to channel <" + channel.remoteAddress() + "> failed.");
                throw new RemotingSendRequestException(RemotingHelper.parseChannelRemoteAddr(channel), e);
            }
        }
        else {
            if (timeoutMillis <= 0) {
                throw new RemotingTooMuchRequestException("invokeOnewayImpl invoke too fast");
            }
            else {
                String info =
                        String
                            .format(
                                "invokeOnewayImpl tryAcquire semaphore timeout, %dms, waiting thread nums: %d semaphoreAsyncValue: %d", //
                                timeoutMillis,//
                                this.semaphoreAsync.getQueueLength(),//
                                this.semaphoreAsync.availablePermits()//
                            );
                plog.warn(info);
                plog.warn(request.toString());
                throw new RemotingTimeoutException(info);
            }
        }
    }
}
