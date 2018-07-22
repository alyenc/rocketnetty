package com.alyenc.rocket.netty;

import com.alyenc.rocket.common.RemotingUtil;
import com.alyenc.rocket.protocol.RemotingMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class NettyEncoder extends MessageToByteEncoder<RemotingMessage> {

    private static final Logger logger = LoggerFactory.getLogger(NettyEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, RemotingMessage message, ByteBuf byteBuf) {
        try {
            ByteBuffer header = message.encodeHeader();
            byteBuf.writeBytes(header);
            byte[] body = message.getBody();
            if (body != null) {
                byteBuf.writeBytes(body);
            }
        } catch (Exception e) {
            if (message != null) {
                logger.error(message.toString());
            }
            RemotingUtil.closeChannel(channelHandlerContext.channel());
        }
    }
}
