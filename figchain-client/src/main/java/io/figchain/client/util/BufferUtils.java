package io.figchain.client.util;

import java.nio.ByteBuffer;

/**
 * Utility class for ByteBuffer operations.
 */
public class BufferUtils {

    /**
     * Converts a ByteBuffer to a byte array without modifying its position.
     *
     * @param buffer the ByteBuffer to convert
     * @return the byte array, or null if the buffer is null
     */
    public static byte[] toByteArray(ByteBuffer buffer) {
        if (buffer == null) return null;
        // Duplicate to avoid modifying the original buffer's position
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
