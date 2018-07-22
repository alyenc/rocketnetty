package com.alyenc.rocket.protocol;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class RemotingMessage {

    private int code;

    private RemotingMessageType msgType;

    private static AtomicInteger requestId = new AtomicInteger(0);

    private transient byte[] body;

    private int opaque = requestId.getAndIncrement();

    private SerializeType serializeType = SerializeType.JSON;

    public static RemotingMessage createRequestMessage(int code) {
        RemotingMessage msg = new RemotingMessage();
        msg.setCode(code);
        return msg;
    }

    public ByteBuffer encodeHeader() {
        return encodeHeader(this.body != null ? this.body.length : 0);
    }

    public ByteBuffer encodeHeader(final int bodyLength) {
        // 1> header length size
        int length = 4;

        // 2> header data length
        byte[] headerData;
        headerData = RemotingSerializable.encode(this);

        length += headerData.length;

        // 3> body data length
        length += bodyLength;

        ByteBuffer result = ByteBuffer.allocate(4 + length - bodyLength);

        // length
        result.putInt(length);

        // header length
        result.put(protocolType(headerData.length, serializeType));

        // header data
        result.put(headerData);

        result.flip();

        return result;
    }

    public static byte[] protocolType(int source, SerializeType type) {
        byte[] result = new byte[4];

        result[0] = type.getCode();
        result[1] = (byte) ((source >> 16) & 0xFF);
        result[2] = (byte) ((source >> 8) & 0xFF);
        result[3] = (byte) (source & 0xFF);
        return result;
    }

    public static RemotingMessage decode(final byte[] array) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        return decode(byteBuffer);
    }

    public static RemotingMessage decode(final ByteBuffer byteBuffer) {
        int length = byteBuffer.limit();
        int oriHeaderLen = byteBuffer.getInt();
        int headerLength = oriHeaderLen & 0xFFFFFF;

        byte[] headerData = new byte[headerLength];
        byteBuffer.get(headerData);

        RemotingMessage cmd = headerDecode(headerData, getProtocolType(oriHeaderLen));

        int bodyLength = length - 4 - headerLength;
        byte[] bodyData = null;
        if (bodyLength > 0) {
            bodyData = new byte[bodyLength];
            byteBuffer.get(bodyData);
        }
        cmd.body = bodyData;

        return cmd;
    }

    private static RemotingMessage headerDecode(byte[] headerData, SerializeType type) {
        switch (type) {
            case JSON:
                RemotingMessage resultJson = RemotingSerializable.decode(headerData, RemotingMessage.class);
                resultJson.setSerializeType(type);
                return resultJson;
            default:
                break;
        }

        return null;
    }

    public static SerializeType getProtocolType(int source) {
        return SerializeType.valueOf((byte) ((source >> 24) & 0xFF));
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

    public SerializeType getSerializeType() {
        return serializeType;
    }

    public void setSerializeType(SerializeType serializeType) {
        this.serializeType = serializeType;
    }
}
