package com.alyenc.rocket;

import com.alyenc.rocket.netty.NettyProcessor;
import com.alyenc.rocket.protocol.RemotingMessage;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestClientProcessor implements NettyProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TestServerProcessor.class);

    @Override
    public RemotingMessage process(ChannelHandlerContext ctx, RemotingMessage message) {
        logger.info("我收到回复了");
        return null;
    }

    @Override
    public boolean reject() {
        return false;
    }
}
