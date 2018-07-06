package com.alyenc.rocket.netty;

import com.alyenc.rocket.protocol.RemotingMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class NettyEncoder extends MessageToByteEncoder<RemotingMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, RemotingMessage requestMessage, ByteBuf byteBuf) {

    }
}
