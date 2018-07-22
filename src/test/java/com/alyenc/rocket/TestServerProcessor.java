package com.alyenc.rocket;

import com.alyenc.rocket.netty.NettyProcessor;
import com.alyenc.rocket.protocol.RemotingMessage;
import com.alyenc.rocket.protocol.RemotingMessageType;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestServerProcessor implements NettyProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TestServerProcessor.class);

    @Override
    public RemotingMessage process(ChannelHandlerContext ctx, RemotingMessage message) {
        RemotingMessage resp = new RemotingMessage();
        resp.setBody("response message".getBytes());
        resp.setMsgType(RemotingMessageType.RESPONSE_MESSAGE);

        return resp;
    }

    @Override
    public boolean reject() {
        return false;
    }
}
