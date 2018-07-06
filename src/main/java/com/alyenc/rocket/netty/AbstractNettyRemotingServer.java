package com.alyenc.rocket.netty;

import com.alyenc.rocket.common.Pair;
import com.alyenc.rocket.protocol.RemotingMessage;
import com.alyenc.rocket.protocol.ResponseFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

public class AbstractNettyRemotingServer {

    private static final Logger logger = LoggerFactory.getLogger(AbstractNettyRemotingServer.class);

    protected Pair<NettyRequestProcessor, ExecutorService> defaultRequestProcessor;

    protected final ConcurrentMap<Integer, ResponseFuture> respMap =
            new ConcurrentHashMap<Integer, ResponseFuture>(256);

    protected final HashMap<Integer, Pair<NettyRequestProcessor, ExecutorService>> procMap =
            new HashMap<Integer, Pair<NettyRequestProcessor, ExecutorService>>(64);

    /**
     * @description 远程同步调用
     * @author alyenc
     * @date 2018/7/5 17:58
     * @param channel
     * @param request
     * @param timeoutMillis
     * @return ResponseMessage
     * @throws InterruptedException
     */
    RemotingMessage invokeSyncImpl(final Channel channel, final RemotingMessage request, final long timeoutMillis) throws InterruptedException {
        final int opaque = request.getOpaque();

        try {
            final ResponseFuture respFuture = new ResponseFuture(opaque, timeoutMillis, null);
            this.respMap.put(opaque, respFuture);
            final SocketAddress addr = channel.remoteAddress();
            channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture f) throws Exception {
                    if (f.isSuccess()) {
                        respFuture.setSendRequestOK(true);
                        return;
                    } else {
                        respFuture.setSendRequestOK(false);
                    }

                    respMap.remove(opaque);
                    respFuture.putResponse(null);
                    logger.warn("send a request command to channel <" + addr + "> failed.");
                }
            });

            RemotingMessage response = respFuture.waitResponse(timeoutMillis);
            if (null == response) {
                if (respFuture.isSendRequestOK()) {

                } else {

                }
            }
            return response;
        } finally {
            this.respMap.remove(opaque);
        }
    }

    public void processMessageReceived(ChannelHandlerContext ctx, RemotingMessage msg) {
        final RemotingMessage cmd = msg;
        if (cmd != null) {
            switch (cmd.getMsgType()) {
                case REQUEST_MESSAGE:
                    processRequest(ctx, cmd);
                    break;
                case RESPONSE_MESSAGE:
                    processResponse(ctx, cmd);
                    break;
                default:
                    break;
            }
        }
    }

    public void processRequest(final ChannelHandlerContext ctx, final RemotingMessage msg) {
        final Pair<NettyRequestProcessor, ExecutorService> matched = this.procMap.get(msg.getCode());
        final Pair<NettyRequestProcessor, ExecutorService> pair = null == matched ? this.defaultRequestProcessor : matched;
        final int opaque = msg.getOpaque();

        if (pair != null) {
            Runnable run = new Runnable() {
                public void run() {
                    try {
                        final RemotingMessage response = pair.getObject1().processRequest(ctx, msg);
                    } catch (Throwable e) {
                        logger.error("process request exception", e);
                        logger.error(msg.toString());
                    }
                }
            };
        }
    }

    public void processResponse(final ChannelHandlerContext ctx, final RemotingMessage msg) {

    }
}
