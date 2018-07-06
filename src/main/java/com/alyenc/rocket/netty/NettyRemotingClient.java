package com.alyenc.rocket.netty;

import com.alyenc.rocket.RemotingClient;
import com.alyenc.rocket.common.Pair;
import com.alyenc.rocket.common.RemotingUtil;
import com.alyenc.rocket.netty.config.NettyClientConfig;
import com.alyenc.rocket.protocol.RemotingMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NettyRemotingClient extends AbstractNettyRemotingServer implements RemotingClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyRemotingClient.class);

    private static final long LOCK_TIMEOUT_MILLIS = 3000;

    private final NettyClientConfig nettyClientConfig;
    private final Bootstrap bootstrap = new Bootstrap();
    private final EventLoopGroup eventLoopGroupWorker;

    private DefaultEventExecutorGroup defaultEventExecutorGroup;

    private ExecutorService callbackExecutor;

    private final ExecutorService publicExecutor;

    private final Lock lockChannelMap = new ReentrantLock();

    private final ConcurrentMap<String, ChannelWrapper> channelMap = new ConcurrentHashMap<String, ChannelWrapper>();

    public NettyRemotingClient(final NettyClientConfig nettyClientConfig) {
        this.nettyClientConfig = nettyClientConfig;

        int publicThreadNum = nettyClientConfig.getClientCallbackExecutorThreads();
        if (publicThreadNum <= 0) {
            publicThreadNum = 4;
        }

        this.eventLoopGroupWorker = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyClientSelector_%d", this.threadIndex.incrementAndGet()));
            }
        });

        this.publicExecutor = Executors.newFixedThreadPool(publicThreadNum, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyClientPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });
    }

    public void start() {
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(
                nettyClientConfig.getClientWorkerThreads(),
                new ThreadFactory() {

                    private AtomicInteger threadIndex = new AtomicInteger(0);

                    public Thread newThread(Runnable r) {
                        return new Thread(r, "NettyClientWorkerThread_" + this.threadIndex.incrementAndGet());
                    }
                });

        Bootstrap handler = this.bootstrap.group(this.eventLoopGroupWorker).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyClientConfig.getConnectTimeoutMillis())
//                .option(ChannelOption.SO_SNDBUF, nettyClientConfig.getClientSocketSndBufSize())
//                .option(ChannelOption.SO_RCVBUF, nettyClientConfig.getClientSocketRcvBufSize())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(
                                defaultEventExecutorGroup,
                                new NettyEncoder(),
                                new NettyDecoder(),
                                new IdleStateHandler(0, 0, nettyClientConfig.getClientChannelMaxIdleTimeSeconds()),
                                new NettyConnectManageHandler(),
                                new NettyClientHandler());
                    }
                });

    }

    public void shutdown() {

    }

    public RemotingMessage invokeSync(String addr, RemotingMessage request, long timeoutMillis) throws Exception {
        final Channel channel = this.getAndCreateChannel(addr);
        if (channel != null && channel.isActive()) {
            try {

                RemotingMessage response = this.invokeSyncImpl(channel, request, timeoutMillis);
                return response;
            } catch (Exception e) {
                logger.warn("invokeSync: send request exception, so close the channel[{}]", addr);
                this.closeChannel(addr, channel);
                throw e;
            }
        } else {
            this.closeChannel(addr, channel);
            throw new Exception(addr);
        }
    }

    public void registerProcessor(int requestCode, NettyRequestProcessor processor, ExecutorService executor) {
        ExecutorService executorThis = executor;
        if (null == executor) {
            executorThis = this.publicExecutor;
        }

        Pair<NettyRequestProcessor, ExecutorService> pair = new Pair<NettyRequestProcessor, ExecutorService>(processor, executorThis);
        this.procMap.put(requestCode, pair);
    }

    public void setCallbackExecutor(ExecutorService callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    private Channel getAndCreateChannel(final String addr) throws InterruptedException {
        ChannelWrapper cw = this.channelMap.get(addr);
        if (cw != null && cw.isOK()) {
            return cw.getChannel();
        }

        return this.createChannel(addr);
    }

    private Channel createChannel(final String addr) throws InterruptedException {
        ChannelWrapper cw = this.channelMap.get(addr);
        if (cw != null && cw.isOK()) {
            cw.getChannel().close();
            channelMap.remove(addr);
        }

        if (this.lockChannelMap.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            try {
                boolean createNewConnection;
                cw = this.channelMap.get(addr);
                if (cw != null) {

                    if (cw.isOK()) {
                        cw.getChannel().close();
                        this.channelMap.remove(addr);
                        createNewConnection = true;
                    } else if (!cw.getChannelFuture().isDone()) {
                        createNewConnection = false;
                    } else {
                        this.channelMap.remove(addr);
                        createNewConnection = true;
                    }
                } else {
                    createNewConnection = true;
                }

                if (createNewConnection) {
                    ChannelFuture channelFuture = this.bootstrap.connect(RemotingUtil.string2SocketAddress(addr));
                    logger.info("createChannel: begin to connect remote host[{}] asynchronously", addr);
                    cw = new ChannelWrapper(channelFuture);
                    this.channelMap.put(addr, cw);
                }
            } catch (Exception e) {
                logger.error("createChannel: create channel exception", e);
            } finally {
                this.lockChannelMap.unlock();
            }
        } else {
            logger.warn("createChannel: try to lock channel table, but timeout, {}ms", LOCK_TIMEOUT_MILLIS);
        }

        if (cw != null) {
            ChannelFuture channelFuture = cw.getChannelFuture();
            if (channelFuture.awaitUninterruptibly(this.nettyClientConfig.getConnectTimeoutMillis())) {
                if (cw.isOK()) {
                    logger.info("createChannel: connect remote host[{}] success, {}", addr, channelFuture.toString());
                    return cw.getChannel();
                } else {
                    logger.warn("createChannel: connect remote host[" + addr + "] failed, " + channelFuture.toString(), channelFuture.cause());
                }
            } else {
                logger.warn("createChannel: connect remote host[{}] timeout {}ms, {}", addr, this.nettyClientConfig.getConnectTimeoutMillis(),
                        channelFuture.toString());
            }
        }

        return null;
    }

    public void closeChannel(final Channel channel) {
        if (null == channel) {
            return;
        }

        try {
            if (this.lockChannelMap.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromMap = true;
                    ChannelWrapper prevCW = null;
                    String addrRemote = null;
                    for (Map.Entry<String, ChannelWrapper> entry : channelMap.entrySet()) {
                        String key = entry.getKey();
                        ChannelWrapper prev = entry.getValue();
                        if (prev.getChannel() != null) {
                            if (prev.getChannel() == channel) {
                                prevCW = prev;
                                addrRemote = key;
                                break;
                            }
                        }
                    }

                    if (null == prevCW) {
                        logger.info("eventCloseChannel: the channel[{}] has been removed from the channel table before", addrRemote);
                        removeItemFromMap = false;
                    }

                    if (removeItemFromMap) {
                        this.channelMap.remove(addrRemote);
                        logger.info("closeChannel: the channel[{}] was removed from channel table", addrRemote);
                        RemotingUtil.closeChannel(channel);
                    }
                } catch (Exception e) {
                    logger.error("closeChannel: close the channel exception", e);
                } finally {
                    this.lockChannelMap.unlock();
                }
            } else {
                logger.warn("closeChannel: try to lock channel table, but timeout, {}ms", LOCK_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            logger.error("closeChannel exception", e);
        }
    }

    public void closeChannel(final String addr, final Channel channel) {
        if (null == channel) {
            return;
        }
        final String addrRemote = null == addr ? RemotingUtil.parseChannelRemoteAddr(channel) : addr;

        try {
            if (this.lockChannelMap.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    final ChannelWrapper prevCW = this.channelMap.get(addrRemote);

                    logger.info("closeChannel: begin close the channel[{}] Found: {}", addrRemote, prevCW != null);

                    if (null == prevCW) {
                        logger.info("closeChannel: the channel[{}] has been removed from the channel table before", addrRemote);
                        removeItemFromTable = false;
                    } else if (prevCW.getChannel() != channel) {
                        logger.info("closeChannel: the channel[{}] has been closed before, and has been created again, nothing to do.",
                                addrRemote);
                        removeItemFromTable = false;
                    }

                    if (removeItemFromTable) {
                        this.channelMap.remove(addrRemote);
                        logger.info("closeChannel: the channel[{}] was removed from channel table", addrRemote);
                    }

                    RemotingUtil.closeChannel(channel);
                } catch (Exception e) {
                    logger.error("closeChannel: close the channel exception", e);
                } finally {
                    this.lockChannelMap.unlock();
                }
            } else {
                logger.warn("closeChannel: try to lock channel table, but timeout, {}ms", LOCK_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            logger.error("closeChannel exception", e);
        }
    }

    static class ChannelWrapper {
        private final ChannelFuture channelFuture;

        ChannelWrapper(ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
        }

        boolean isOK() {
            return this.channelFuture.channel() != null && this.channelFuture.channel().isActive();
        }

        public boolean isWritable() {
            return this.channelFuture.channel().isWritable();
        }

        private Channel getChannel() {
            return this.channelFuture.channel();
        }

        ChannelFuture getChannelFuture() {
            return channelFuture;
        }
    }

    class NettyClientHandler extends SimpleChannelInboundHandler<RemotingMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RemotingMessage msg) throws Exception {
            processMessageReceived(ctx, msg);
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
