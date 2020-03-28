package net.sony.util;


public class BigIntegerUtils {

    public static byte[] projectArray(byte[] src, int projectionSize) {
        byte[] result = new byte[projectionSize];
        System.arraycopy(src, Math.max(0, src.length - result.length), result, Math.max(0, result.length - src.length), Math.min(src.length, src.length + (result.length - src.length)));
        return result;
    }

}
