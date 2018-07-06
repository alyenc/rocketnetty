package com.alyenc.rocket.protocol;

import java.util.concurrent.atomic.AtomicInteger;

public class RemotingMessage {

    private int code;

    private RemotingMessageType msgType;

    private static AtomicInteger requestId = new AtomicInteger(0);

    private transient byte[] body;

    private int opaque = requestId.getAndIncrement();

    public static RemotingMessage createRequestMessage(int code) {
        RemotingMessage msg = new RemotingMessage();
        msg.setCode(code);
        return msg;
    }

    public int getOpaque() {
        return opaque;
    }

    public void setOpaque(int opaque) {
        this.opaque = opaque;
    }

    public RemotingMessageType getMsgType() {
        return msgType;
    }

    public void setMsgType(RemotingMessageType msgType) {
        this.msgType = msgType;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
