package com.alyenc.rocket.netty;

import com.alyenc.rocket.RemotingServer;
import com.alyenc.rocket.common.Pair;
import com.alyenc.rocket.netty.config.NettyServerConfig;
import com.alyenc.rocket.protocol.RemotingMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NettyRemotingServer extends AbstractNettyRemotingServer implements RemotingServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyRemotingServer.class);

    private final ServerBootstrap serverBootstrap;
    private final NettyServerConfig nettyServerConfig;
    private final EventLoopGroup eventLoopGroupSelector;
    private final EventLoopGroup eventLoopGroupBoss;

    private DefaultEventExecutorGroup defaultEventExecutorGroup;

    private final ExecutorService publicExecutor;

    private static final String HANDSHAKE_HANDLER_NAME = "handshakeHandler";

    private final Timer timer = new Timer("ServerHouseKeepingService", true);

    private int port = 0;

    public NettyRemotingServer(final NettyServerConfig config) {
        this.serverBootstrap = new ServerBootstrap();
        this.nettyServerConfig = config;

        int publicThreadNum = nettyServerConfig.getServerCallbackExecutorThreads();
        if (publicThreadNum <= 0) {
            publicThreadNum = 4;
        }

        this.publicExecutor = Executors.newFixedThreadPool(publicThreadNum, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyServerPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });

        this.eventLoopGroupBoss = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyBoss_%d", this.threadIndex.incrementAndGet()));
            }
        });

        this.eventLoopGroupSelector = new NioEventLoopGroup(nettyServerConfig.getServerSelectorThreads(), new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);
            private int threadTotal = nettyServerConfig.getServerSelectorThreads();

            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyServerNIOSelector_%d_%d", threadTotal, this.threadIndex.incrementAndGet()));
            }
        });
    }

    public void start() {
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(
            nettyServerConfig.getServerWorkerThreads(),
            new ThreadFactory() {
                private AtomicInteger threadIndex = new AtomicInteger(0);

                public Thread newThread(Runnable r) {
                    return new Thread(r, "NettyServerCodecThread_" + this.threadIndex.incrementAndGet());
                }
            });
        logger.info("执行初始化");
        ServerBootstrap childHandler =
            this.serverBootstrap.group(this.eventLoopGroupBoss, this.eventLoopGroupSelector)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
//                        .childOption(ChannelOption.SO_SNDBUF, nettyServerConfig.getServerSocketSndBufSize())
//                        .childOption(ChannelOption.SO_RCVBUF, nettyServerConfig.getServerSocketRcvBufSize())
                .localAddress(new InetSocketAddress(this.nettyServerConfig.getListenPort()))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                            .addLast(defaultEventExecutorGroup, HANDSHAKE_HANDLER_NAME, new HandShakeHandler())
                            .addLast(defaultEventExecutorGroup,
                                new NettyEncoder(),
                                new NettyDecoder(),
                                new IdleStateHandler(0, 0, nettyServerConfig.getServerChannelMaxIdleTimeSeconds()),
                                new NettyConnectManageHandler(),
                                new NettyServerHandler()
                            );
                    }
                });

        try {
            ChannelFuture sync = this.serverBootstrap.bind().sync();
            InetSocketAddress addr = (InetSocketAddress) sync.channel().localAddress();
            this.port = addr.getPort();
        } catch (InterruptedException e1) {
            throw new RuntimeException("this.serverBootstrap.bind().sync() InterruptedException", e1);
        }
    }

    public void shutdown() {

    }

    public void registerProcessor(int requestCode, NettyRequestProcessor processor, ExecutorService executor) {
        ExecutorService executorThis = executor;
        if (null == executor) {
            executorThis = this.publicExecutor;
        }

        Pair<NettyRequestProcessor, ExecutorService> pair = new Pair<NettyRequestProcessor, ExecutorService>(processor, executorThis);
        this.procMap.put(requestCode, pair);
    }

    public void registerDefaultProcessor(NettyRequestProcessor processor, ExecutorService executor) {
        this.defaultRequestProcessor = new Pair<NettyRequestProcessor, ExecutorService>(processor, executor);
    }

    public RemotingMessage invokeSync(Channel channel, RemotingMessage request, long timeoutMillis) throws InterruptedException {
        return this.invokeSyncImpl(channel, request, timeoutMillis);
    }

    class HandShakeHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) {

        }
    }

    class NettyServerHandler extends SimpleChannelInboundHandler<RemotingMessage> {

        protected final HashMap<Integer, Pair<NettyRequestProcessor, ExecutorService>> procMap =
                new HashMap<Integer, Pair<NettyRequestProcessor, ExecutorService>>(64);

        protected Pair<NettyRequestProcessor, ExecutorService> defaultRequestProcessor;

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, RemotingMessage msg) {
            processMessageReceived(channelHandlerContext, msg);
        }
    }

    class NettyConnectManageHandler extends ChannelDuplexHandler {
        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                            ChannelPromise promise) throws Exception {

        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {

        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {

        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        }
    }
}
