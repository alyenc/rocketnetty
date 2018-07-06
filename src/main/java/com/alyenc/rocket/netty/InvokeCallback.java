package com.alyenc.rocket.netty;

import com.alyenc.rocket.protocol.ResponseFuture;

public interface InvokeCallback {
    void operationComplete(final ResponseFuture response);
}
