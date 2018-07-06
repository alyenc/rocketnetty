package com.alyenc.rocket;

import com.alyenc.rocket.netty.NettyRequestProcessor;
import com.alyenc.rocket.protocol.RemotingMessage;
import io.netty.channel.Channel;

import java.util.concurrent.ExecutorService;

/**
 * @description
 * @package com.alyenc.lycan.remoting
 * @author alyenc
 * @email alyenc@outlook.com
 * @date 2018/7/5 22:43
 * @version v1.0.0
 */
public interface RemotingServer {

    /**
     * @description
     * @author alyenc
     * @date 2018/7/5 22:42
     */
    void start();

    /**
     * @description
     * @author alyenc
     * @date 2018/7/5 22:42
     */
    void shutdown();

    /**
     * @description 注册处理器
     * @author alyenc
     * @date 2018/7/5 22:40
     * @param requestCode
     * @param processor
     * @param executor
     */
    void registerProcessor(final int requestCode, final NettyRequestProcessor processor,
                           final ExecutorService executor);

    /**
     * @description 注册默认处理器
     * @author alyenc
     * @date 2018/7/5 22:40
     * @param processor
     * @param executor
     */
    void registerDefaultProcessor(final NettyRequestProcessor processor, final ExecutorService executor);

    /**
     * @description 远程同步调用
     * @author alyenc
     * @date 2018/7/5 17:58
     * @param channel
     * @param request
     * @param timeoutMillis
     * @return ResponseMessage
     */
    RemotingMessage invokeSync(final Channel channel, final RemotingMessage request, final long timeoutMillis) throws InterruptedException;
}
