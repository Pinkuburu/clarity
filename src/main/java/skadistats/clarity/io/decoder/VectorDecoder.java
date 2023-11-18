package skadistats.clarity.io.decoder;

import skadistats.clarity.io.bitstream.BitStream;
import skadistats.clarity.model.Vector;

public class VectorDecoder implements Decoder<Vector> {

    private final Decoder<Float> floatDecoder;
    private final boolean normal;


    public VectorDecoder(Decoder<Float> floatDecoder, boolean normal) {
        this.floatDecoder = floatDecoder;
        this.normal = normal;
    }

    @Override
    public Vector decode(BitStream bs) {
        var v = new float[3];
        v[0] = floatDecoder.decode(bs);
        v[1] = floatDecoder.decode(bs);
        if (!normal) {
            v[2] = floatDecoder.decode(bs);
        } else {
            var s = bs.readBitFlag();
            var p = v[0] * v[0] + v[1] * v[1];
            if (p < 1.0f) v[2] = (float) Math.sqrt(1.0f - p);
            if (s) v[2] = -v[2];
        }
        return new Vector(v);
    }

}
