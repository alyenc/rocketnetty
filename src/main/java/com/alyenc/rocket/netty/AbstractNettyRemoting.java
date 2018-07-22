package com.alyenc.rocket.netty;

import com.alyenc.rocket.common.Pair;
import com.alyenc.rocket.protocol.RemotingMessage;
import com.alyenc.rocket.protocol.RemotingMessageType;
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
import java.util.concurrent.RejectedExecutionException;

public class AbstractNettyRemoting {

    private static final Logger logger = LoggerFactory.getLogger(AbstractNettyRemoting.class);

    protected Pair<NettyProcessor, ExecutorService> defaultRequestProcessor;

    protected final ConcurrentMap<Integer, ResponseFuture> respMap =
            new ConcurrentHashMap<Integer, ResponseFuture>(256);

    protected final HashMap<Integer, Pair<NettyProcessor, ExecutorService>> procMap =
            new HashMap<Integer, Pair<NettyProcessor, ExecutorService>>(64);

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
            channel.writeAndFlush(request).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    respFuture.setSendRequestOK(true);
                    return;
                } else {
                    respFuture.setSendRequestOK(false);
                }

                respMap.remove(opaque);
                respFuture.putResponse(null);
                logger.warn("send a request command to channel <" + addr + "> failed.");
            });

            RemotingMessage response = respFuture.waitResponse(timeoutMillis);
            if (null == response) {
                if (respFuture.isSendRequestOK()) {
                    System.out.println("send OK");
                } else {
                    System.out.println("send not OK");
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
        final Pair<NettyProcessor, ExecutorService> matched = this.procMap.get(msg.getCode());
        final Pair<NettyProcessor, ExecutorService> pair = null == matched ? this.defaultRequestProcessor : matched;
        final int opaque = msg.getOpaque();

        if (pair != null) {
            Runnable run = () -> {
                try {
                    final RemotingMessage response = pair.getObject1().process(ctx, msg);

                    if (response != null) {
                        response.setOpaque(opaque);
                        response.setMsgType(RemotingMessageType.RESPONSE_MESSAGE);
                        try {
                            ctx.writeAndFlush(response);
                        } catch (Throwable e) {
                            logger.error("process request over, but response failed", e);
                            logger.error(msg.toString());
                            logger.error(response.toString());
                        }
                    } else {

                    }
                } catch (Throwable e) {
                    logger.error("process request exception", e);
                    logger.error(msg.toString());
                }
            };

            if (pair.getObject1().reject()) {
                final RemotingMessage response = RemotingMessage.createRequestMessage(11);
                response.setOpaque(opaque);
                ctx.writeAndFlush(response);
                return;
            }

            try {
                final RequestTask requestTask = new RequestTask(run, ctx.channel(), msg);
                pair.getObject2().submit(requestTask);
            } catch (RejectedExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    public void processResponse(final ChannelHandlerContext ctx, final RemotingMessage msg) {
        final int opaque = msg.getOpaque();
        final ResponseFuture responseFuture = respMap.get(opaque);
        if (responseFuture != null) {
            responseFuture.setResponse(msg);
            respMap.remove(opaque);
            responseFuture.putResponse(msg);
        } else {
            logger.warn("receive response, but not matched any request");
            logger.warn(msg.toString());
        }
    }
}
