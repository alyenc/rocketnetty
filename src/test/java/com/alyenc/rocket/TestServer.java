package com.alyenc.rocket;

import com.alyenc.rocket.netty.NettyRemotingServer;
import com.alyenc.rocket.netty.config.NettyServerConfig;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TestServer {

    private static final Logger logger = LoggerFactory.getLogger(TestServer.class);

    private final NettyServerConfig nettyServerConfig;

    private ExecutorService sendMessageExecutor;

    private RemotingServer remotingServer;

    public TestServer(NettyServerConfig nettyServerConfig){
        this.nettyServerConfig = nettyServerConfig;
    }

    public boolean initialize() {
        this.remotingServer = new NettyRemotingServer(this.nettyServerConfig);

        this.sendMessageExecutor = new ThreadPoolExecutor(
                10,
                10,
                1000 * 60,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new DefaultThreadFactory("SendMessageThread_"));

        //注册处理器
        this.registerProcessor();

        return true;
    }

    public void registerProcessor() {
        TestServerProcessor processor = new TestServerProcessor();
        this.remotingServer.registerProcessor(39, processor, this.sendMessageExecutor);

    }

    public void start(){
        if (this.remotingServer != null) {
            this.remotingServer.start();
        }
    }

    public void shutdown(){
        if (this.remotingServer != null) {
            this.remotingServer.shutdown();
        }

        if (this.sendMessageExecutor != null) {
            this.sendMessageExecutor.shutdown();
        }
    }

    public static void main(String[] args) {
        NettyServerConfig nettyServerConfig = new NettyServerConfig();
        TestServer server = new TestServer(nettyServerConfig);
        server.initialize();
        server.start();
    }
}
