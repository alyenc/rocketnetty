package com.alyenc.rocket;

import com.alyenc.rocket.common.Message;
import com.alyenc.rocket.common.RemotingUtil;
import com.alyenc.rocket.netty.NettyRemotingClient;
import com.alyenc.rocket.netty.config.NettyClientConfig;
import com.alyenc.rocket.protocol.RemotingMessage;
import com.alyenc.rocket.protocol.RemotingMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class TestClient {
    private static final Logger logger = LoggerFactory.getLogger(TestServerProcessor.class);

    public static void main(String[] args) throws Exception {
        NettyClientConfig nettyClientConfig = new NettyClientConfig();
        RemotingClient remotingClient = new NettyRemotingClient(nettyClientConfig);
        remotingClient.start();

        Message msg = new Message();
        RemotingMessage request = null;
        request = RemotingMessage.createRequestMessage(39);
        request.setMsgType(RemotingMessageType.REQUEST_MESSAGE);
        request.setBody("request message".getBytes());

        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 8888);
        RemotingMessage response = remotingClient.invokeSync(RemotingUtil.socketAddress2String(addr), request, 1000);
        System.out.println(new String(response.getBody()));
    }
}
