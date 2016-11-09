package org.elasticsearch.plugin.readonlyrest.util;

import com.google.common.base.Charsets;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;

public class CharUtil {
    public static char[] utf8BytesToChars(byte[] utf8Bytes) {

        ByteBuffer byteBuffer = ByteBuffer.wrap(utf8Bytes);
        CharBuffer charBuffer = Charsets.UTF_8.decode(byteBuffer);
        char[] chars = Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
        byteBuffer.clear();
        charBuffer.clear();
        return chars;
    }

    public static byte[] toUtf8Bytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = Charsets.UTF_8.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0);
        return bytes;
    }
}
