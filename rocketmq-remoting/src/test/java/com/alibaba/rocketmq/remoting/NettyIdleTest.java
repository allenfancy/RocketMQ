package com.alibaba.rocketmq.remoting;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.Executors;

import org.junit.Test;

import com.alibaba.rocketmq.remoting.exception.RemotingConnectException;
import com.alibaba.rocketmq.remoting.exception.RemotingSendRequestException;
import com.alibaba.rocketmq.remoting.exception.RemotingTimeoutException;
import com.alibaba.rocketmq.remoting.netty.NettyClientConfig;
import com.alibaba.rocketmq.remoting.netty.NettyRemotingClient;
import com.alibaba.rocketmq.remoting.netty.NettyRemotingServer;
import com.alibaba.rocketmq.remoting.netty.NettyRequestProcessor;
import com.alibaba.rocketmq.remoting.netty.NettyServerConfig;
import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;

import io.netty.channel.ChannelHandlerContext;

/**
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-6
 */
public class NettyIdleTest {
	// 创建客户端
	public static RemotingClient createRemotingClient() {
		NettyClientConfig config = new NettyClientConfig();
		config.setClientChannelMaxIdleTimeSeconds(15);
		RemotingClient client = new NettyRemotingClient(config);
		client.start();
		return client;
	}

	// 创建服务器
	public static RemotingServer createRemotingServer() throws InterruptedException {
		//创建Netty服务端配置
		NettyServerConfig config = new NettyServerConfig();
		//设置空闲最大时间
		config.setServerChannelMaxIdleTimeSeconds(30);
		//创建服务
		RemotingServer remotingServer = new NettyRemotingServer(config);
		remotingServer.registerProcessor(0, new NettyRequestProcessor() {
			private int i = 0;

			@Override
			public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) {
				System.out.println("processRequest=" + request + " " + (i++));
				request.setRemark("hello, I am respponse " + ctx.channel().remoteAddress());
				return request;
			}
		}, Executors.newCachedThreadPool());
		remotingServer.start();
		return remotingServer;
	}

	@Test
	public void test_idle_event() throws InterruptedException, RemotingConnectException, RemotingSendRequestException,
			RemotingTimeoutException {
		RemotingServer server = createRemotingServer();
		RemotingClient client = createRemotingClient();
		for (int i = 0; i < 10; i++) {
			RemotingCommand request = RemotingCommand.createRequestCommand(0, null);
			RemotingCommand response = client.invokeSync("localhost:8888", request, 1000 * 3);
			System.out.println(i + " invoke result = " + response);
			System.out.println(response);
			Thread.sleep(1000 * 10);
		}

		Thread.sleep(1000 * 60);

		client.shutdown();
		server.shutdown();
		System.out.println("-----------------------------------------------------------------");
	}

}
