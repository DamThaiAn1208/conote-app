package com.conote.common.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class FrameCodecTest {
    @Test
    void readsSplitFramesAndKeepsConnectionAligned() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FrameCodec.write(output, "{\"text\":\"Tiếng Việt\"}");
        FrameCodec.write(output, "{\"operation\":\"PING\"}");
        InputStream oneByteAtATime = new ByteArrayInputStream(output.toByteArray()) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(length, 1));
            }
        };

        assertEquals("{\"text\":\"Tiếng Việt\"}", FrameCodec.read(oneByteAtATime));
        assertEquals("{\"operation\":\"PING\"}", FrameCodec.read(oneByteAtATime));
        assertThrows(EOFException.class, () -> FrameCodec.read(oneByteAtATime));
    }

    @Test
    void rejectsInvalidLengthAndTruncatedBody() {
        assertThrows(FrameCodec.InvalidFrameException.class,
                () -> FrameCodec.read(new ByteArrayInputStream(new byte[] {0, 0, 0, 0})));
        assertThrows(FrameCodec.InvalidFrameException.class,
                () -> FrameCodec.read(new ByteArrayInputStream(new byte[] {0, 0, 0, 5, '{'})));
        assertThrows(FrameCodec.InvalidFrameException.class,
                () -> FrameCodec.read(new ByteArrayInputStream(new byte[] {0, 0})));
    }

    @Test
    void rejectsInvalidUtf8AndOversizedWrites() {
        assertThrows(FrameCodec.InvalidFrameException.class,
                () -> FrameCodec.read(new ByteArrayInputStream(new byte[] {0, 0, 0, 1, (byte) 0xff})));
        assertThrows(FrameCodec.InvalidFrameException.class,
                () -> FrameCodec.write(new ByteArrayOutputStream(), "x".repeat(FrameCodec.MAX_FRAME_BYTES + 1)));
    }
}
