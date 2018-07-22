package com.alyenc.rocket.netty;

import com.alyenc.rocket.protocol.RemotingMessage;
import io.netty.channel.ChannelHandlerContext;

/**
 * @description 所有的客户端请求处理器都需要继承这个类
 * @package com.alyenc.rocket.netty
 * @author alyenc
 * @email alyenc@outlook.com
 * @date 2018/7/22 12:13
 * @version v1.0.0
 */
public interface NettyProcessor {

    /**
     * @description 处理请求
     * @author alyenc
     * @date 2018/7/22 12:13
     * @param ctx
     * @param request
     * @return RemotingMessage
     */
    RemotingMessage process(ChannelHandlerContext ctx, RemotingMessage message)
            throws Exception;

    /**
     * @description 拒接请求
     * @author alyenc
     * @date 2018/7/22 12:14
     * @return boolean
     */
    boolean reject();
}
