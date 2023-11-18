package skadistats.clarity.io.s1;

import skadistats.clarity.io.bitstream.BitStream;
import skadistats.clarity.model.s1.S1FieldPath;

public class CsGoFieldReader extends S1FieldReader{

    @Override
    protected int readIndices(BitStream bs, S1DTClass dtClass) {
        var nway = bs.readBitFlag();
        var n = 0;
        var cursor = -1;
        while (true) {
            if (nway && bs.readBitFlag()) {
                // 1, 1 = increment
                cursor += 1;
            } else if (nway && bs.readBitFlag()) {
                // 1, 0, 1 = new index is 3 bits
                cursor += 1 + bs.readUBitInt(3);
            } else {
                var v = bs.readUBitInt(7);
                switch (v & ( 32 | 64)) {
                    case 32:
                        v = ((v &~ 96) | (bs.readUBitInt(2) << 5));
                        break;
                    case 64:
                        v = ((v &~ 96) | (bs.readUBitInt(4) << 5));
                        break;
                    case 96:
                        v = ((v &~ 96) | (bs.readUBitInt(7) << 5));
                        break;
                }
                if (v == 0xFFF) {
                    return n;
                }
                cursor += 1 + v;
            }
            fieldPaths[n++] = new S1FieldPath(dtClass.getIndexMapping()[cursor]);
        }
    }

}
