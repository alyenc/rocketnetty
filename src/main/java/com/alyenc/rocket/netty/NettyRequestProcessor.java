package com.alyenc.rocket.netty;

import com.alyenc.rocket.protocol.RemotingMessage;
import io.netty.channel.ChannelHandlerContext;

public interface NettyRequestProcessor {

    RemotingMessage processRequest(ChannelHandlerContext ctx, RemotingMessage request)
            throws Exception;

    boolean rejectRequest();
}
