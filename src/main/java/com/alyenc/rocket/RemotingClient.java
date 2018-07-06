package com.alyenc.rocket;

import com.alyenc.rocket.netty.NettyRequestProcessor;
import com.alyenc.rocket.protocol.RemotingMessage;

import java.util.concurrent.ExecutorService;

public interface RemotingClient {

    void start();

    void shutdown();
    /**
     * @description 远程同步调用
     * @author alyenc
     * @date 2018/7/5 17:58
     * @param addr
     * @param request
     * @param timeoutMillis
     * @return ResponseMessage
     */
    RemotingMessage invokeSync(final String addr, final RemotingMessage request, final long timeoutMillis) throws Exception;

    void registerProcessor(final int requestCode, final NettyRequestProcessor processor,
                           final ExecutorService executor);

    void setCallbackExecutor(final ExecutorService callbackExecutor);

}
