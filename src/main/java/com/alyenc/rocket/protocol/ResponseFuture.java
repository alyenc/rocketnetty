package com.alyenc.rocket.protocol;

import com.alyenc.rocket.netty.InvokeCallback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ResponseFuture {
    private final int opaque;
    private final long timeoutMillis;
    private final InvokeCallback invokeCallback;

    private volatile RemotingMessage response;
    private volatile boolean sendRequestOK = true;

    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    public ResponseFuture(int opaque, long timeoutMillis, InvokeCallback invokeCallback) {
        this.opaque = opaque;
        this.timeoutMillis = timeoutMillis;
        this.invokeCallback = invokeCallback;
    }

    public RemotingMessage waitResponse(final long timeoutMillis) throws InterruptedException {
        this.countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        return this.response;
    }

    public void putResponse(final RemotingMessage response) {
        this.response = response;
        this.countDownLatch.countDown();
    }

    public RemotingMessage getResponse() {
        return response;
    }

    public void setResponse(RemotingMessage response) {
        this.response = response;
    }

    public boolean isSendRequestOK() {
        return sendRequestOK;
    }

    public void setSendRequestOK(boolean sendRequestOK) {
        this.sendRequestOK = sendRequestOK;
    }
}
