package com.conote.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Four-byte big-endian length followed by one UTF-8 JSON document. */
public final class FrameCodec {
    public static final int MAX_FRAME_BYTES = 1_048_576;

    private FrameCodec() { }

    public static String read(InputStream input) throws IOException {
        int first = input.read();
        if (first == -1) {
            throw new EOFException("Connection closed between frames");
        }
        int length;
        try {
            int second = new DataInputStream(input).readUnsignedByte();
            int third = new DataInputStream(input).readUnsignedByte();
            int fourth = new DataInputStream(input).readUnsignedByte();
            length = (first << 24) | (second << 16) | (third << 8) | fourth;
        } catch (EOFException exception) {
            throw new InvalidFrameException("Truncated frame header", exception);
        }
        if (length < 1 || length > MAX_FRAME_BYTES) {
            throw new InvalidFrameException("Frame length out of range: " + Integer.toUnsignedString(length));
        }

        byte[] bytes = new byte[length];
        try {
            new DataInputStream(input).readFully(bytes);
        } catch (EOFException exception) {
            throw new InvalidFrameException("Truncated frame body", exception);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new InvalidFrameException("Frame is not valid UTF-8", exception);
        }
    }

    public static void write(OutputStream output, String json) throws IOException {
        if (json == null) {
            throw new IllegalArgumentException("JSON cannot be null");
        }
        byte[] bytes;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(json));
            bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
        } catch (CharacterCodingException exception) {
            throw new InvalidFrameException("JSON is not valid UTF-16 input", exception);
        }
        if (bytes.length < 1 || bytes.length > MAX_FRAME_BYTES) {
            throw new InvalidFrameException("Frame length out of range: " + bytes.length);
        }
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeInt(bytes.length);
        stream.write(bytes);
        stream.flush();
    }

    public static final class InvalidFrameException extends IOException {
        public InvalidFrameException(String message) {
            super(message);
        }

        public InvalidFrameException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
