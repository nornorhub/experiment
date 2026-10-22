package com.nornorhub.ghostbus.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Minimal protobuf wire-format reader, just enough to decode GTFS-realtime feeds. */
final class ProtoReader {

    static final int VARINT = 0;
    static final int FIXED64 = 1;
    static final int LENGTH_DELIMITED = 2;
    static final int FIXED32 = 5;

    private final byte[] buf;
    private final int limit;
    private int pos;
    private int tag;

    ProtoReader(byte[] buf) {
        this(buf, 0, buf.length);
    }

    private ProtoReader(byte[] buf, int start, int limit) {
        this.buf = buf;
        this.pos = start;
        this.limit = limit;
    }

    /** Advances to the next field; returns false at the end of the message. */
    boolean next() throws IOException {
        if (pos >= limit) {
            return false;
        }
        tag = (int) readVarint();
        if (tag == 0) {
            throw new IOException("Invalid protobuf tag at " + pos);
        }
        return true;
    }

    int field() {
        return tag >>> 3;
    }

    int wireType() {
        return tag & 7;
    }

    long readVarint() throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (pos >= limit) {
                throw new IOException("Truncated varint");
            }
            byte b = buf[pos++];
            result |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("Malformed varint");
    }

    int readInt32() throws IOException {
        return (int) readVarint();
    }

    float readFloat() throws IOException {
        return Float.intBitsToFloat((int) readLittleEndian(4));
    }

    double readDouble() throws IOException {
        return Double.longBitsToDouble(readLittleEndian(8));
    }

    String readString() throws IOException {
        int len = readLength();
        String s = new String(buf, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    ProtoReader readMessage() throws IOException {
        int len = readLength();
        ProtoReader sub = new ProtoReader(buf, pos, pos + len);
        pos += len;
        return sub;
    }

    void skip() throws IOException {
        switch (wireType()) {
            case VARINT:
                readVarint();
                break;
            case FIXED64:
                advance(8);
                break;
            case LENGTH_DELIMITED:
                advance(readLength());
                break;
            case FIXED32:
                advance(4);
                break;
            default:
                throw new IOException("Unsupported wire type " + wireType());
        }
    }

    private int readLength() throws IOException {
        long len = readVarint();
        if (len < 0 || len > limit - pos) {
            throw new IOException("Bad length " + len);
        }
        return (int) len;
    }

    private long readLittleEndian(int bytes) throws IOException {
        if (limit - pos < bytes) {
            throw new IOException("Truncated fixed value");
        }
        long v = 0;
        for (int i = 0; i < bytes; i++) {
            v |= (long) (buf[pos++] & 0xff) << (8 * i);
        }
        return v;
    }

    private void advance(int n) throws IOException {
        if (n > limit - pos) {
            throw new IOException("Truncated field");
        }
        pos += n;
    }
}
