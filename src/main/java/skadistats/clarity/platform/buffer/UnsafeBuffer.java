package skadistats.clarity.platform.buffer;

import skadistats.clarity.io.Util;
import skadistats.clarity.util.ClassReflector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public class UnsafeBuffer {

    private static final long base;

    private static final MethodHandle mhGetInt;
    private static final MethodHandle mhGetLong;

    public static final boolean available;

    static {
        ClassReflector reflector = new ClassReflector("sun.misc.Unsafe");

        Object unsafe = reflector.getDeclaredField(null, "theUnsafe");

        Integer bo = (Integer) reflector.getDeclaredField(null, "ARRAY_BYTE_BASE_OFFSET");
        base = bo != null ? bo.longValue() : 0L;

        // public int getInt(Object o, long offset)
        mhGetInt = reflector.getPublicVirtual("getInt", MethodType.methodType(int.class, Object.class, long.class)).bindTo(unsafe);
        // public long getLong(Object o, long offset)
        mhGetLong = reflector.getPublicVirtual("getLong", MethodType.methodType(long.class, Object.class, long.class)).bindTo(unsafe);

        available = base != 0L
                && mhGetInt != null
                && mhGetLong != null;
    }


    public static class B32 implements Buffer.B32 {

        private final byte[] data;

        public B32(byte[] data) {
            this.data = data;
        }

        @Override
        public int get(int n) {
            n *= 4;
            if (n < 0 || n > data.length + 8) {
                throw new ArrayIndexOutOfBoundsException(n);
            }
            try {
                return (int) mhGetInt.invokeExact((Object)data, base + (long)n);
            } catch (Throwable e) {
                Util.uncheckedThrow(e);
                return 0;
            }
        }

    }


    public static class B64 implements Buffer.B64 {

        private final byte[] data;

        public B64(byte[] data) {
            this.data = data;
        }

        @Override
        public long get(int n) {
            n *= 8;
            if (n < 0 || n > data.length + 8) {
                throw new ArrayIndexOutOfBoundsException(n);
            }
            try {
                return (long) mhGetLong.invokeExact((Object)data, base + (long)n);
            } catch (Throwable e) {
                Util.uncheckedThrow(e);
                return 0;
            }
        }

    }

}
