package skadistats.clarity.decoder.s2.field;

import skadistats.clarity.ClarityException;
import skadistats.clarity.decoder.Util;
import skadistats.clarity.decoder.s2.DumpEntry;
import skadistats.clarity.decoder.s2.S2UnpackerFactory;
import skadistats.clarity.decoder.unpacker.Unpacker;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.state.EntityState;

import java.util.List;

public class VarArrayField extends Field {

    private final Unpacker baseUnpacker;
    private final Unpacker elementUnpacker;

    public VarArrayField(FieldProperties properties) {
        super(properties);
        baseUnpacker = S2UnpackerFactory.createUnpacker(properties, "uint32");
        elementUnpacker = S2UnpackerFactory.createUnpacker(properties, properties.getType().getGenericType().getBaseType());
    }

    @Override
    public void initInitialState(EntityState state, int idx) {
        state.set(idx, null);
    }

    @Override
    public void accumulateName(FieldPath fp, int pos, List<String> parts) {
        assert fp.last == pos || fp.last == pos + 1;
        addBasePropertyName(parts);
        if (fp.last > pos) {
            parts.add(Util.arrayIdxToString(fp.path[pos + 1]));
        }
    }

    @Override
    public Unpacker getUnpackerForFieldPath(FieldPath fp, int pos) {
        assert fp.last == pos || fp.last == pos + 1;
        if (pos == fp.last) {
            return baseUnpacker;
        } else {
            return elementUnpacker;
        }
    }

    @Override
    public Field getFieldForFieldPath(FieldPath fp, int pos) {
        assert fp.last == pos || fp.last == pos + 1;
        return this;
    }

    @Override
    public FieldType getTypeForFieldPath(FieldPath fp, int pos) {
        assert fp.last == pos || fp.last == pos + 1;
        if (fp.last == pos) {
            return properties.getType();
        } else {
            return properties.getType().getGenericType();
        }
    }

    @Override
    public Object getValueForFieldPath(FieldPath fp, int pos, EntityState state) {
        assert fp.last == pos || fp.last == pos + 1;
        EntityState subState = state.sub(fp.path[pos]);
        if (fp.last == pos) {
            return subState.length();
        } else {
            return subState.get(fp.path[pos + 1]);
        }
    }

    @Override
    public void setValueForFieldPath(FieldPath fp, int pos, EntityState state, Object value) {
        assert fp.last == pos || fp.last == pos + 1;
        int i = fp.path[pos];
        EntityState subState = state.sub(i);
        if (fp.last == pos) {
            subState.capacity((Integer) value, true);
        } else {
            int j = fp.path[pos + 1];
            subState.capacity(j + 1, false);
            subState.set(j, value);
        }
    }

    @Override
    public FieldPath getFieldPathForName(FieldPath fp, String property) {
        if (property.length() != 4) {
            throw new ClarityException("unresolvable fieldpath");
        }
        fp.path[fp.last] = Integer.valueOf(property);
        return fp;
    }

    @Override
    public void collectDump(FieldPath fp, String namePrefix, List<DumpEntry> entries, EntityState state) {
        EntityState subState = state.sub(fp.path[fp.last]);
        fp.last++;
        for (int i = 0; i < subState.length(); i++) {
            if (subState.has(i)) {
                fp.path[fp.last] = i;
                entries.add(new DumpEntry(fp, joinPropertyName(namePrefix, properties.getName(), Util.arrayIdxToString(i)), subState.get(i)));
            }
        }
        fp.last--;
    }

    @Override
    public void collectFieldPaths(FieldPath fp, List<FieldPath> entries, EntityState state) {
        EntityState subState = state.sub(fp.path[fp.last]);
        fp.last++;
        for (int i = 0; i < subState.length(); i++) {
            if (subState.has(i)) {
                fp.path[fp.last] = i;
                entries.add(new FieldPath(fp));
            }
        }
        fp.last--;
    }

}
