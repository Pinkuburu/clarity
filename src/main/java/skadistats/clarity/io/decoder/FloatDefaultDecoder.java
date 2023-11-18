package skadistats.clarity.io.decoder;

import skadistats.clarity.io.bitstream.BitStream;

public class FloatDefaultDecoder implements Decoder<Float> {

    private final int bitCount;
    private final float minValue;
    private final float maxValue;
    private final float decodeMultiplier;

    public FloatDefaultDecoder(int bitCount, float minValue, float maxValue) {
        this.bitCount = bitCount;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.decodeMultiplier = 1.0f / ((1 << bitCount) - 1);
    }

    @Override
    public Float decode(BitStream bs) {
        var v = bs.readUBitInt(bitCount) * decodeMultiplier;
        return minValue + (maxValue - minValue) * v;
    }

}
