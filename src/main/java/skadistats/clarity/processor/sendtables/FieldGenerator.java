package skadistats.clarity.processor.sendtables;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.slf4j.Logger;
import skadistats.clarity.io.s2.Field;
import skadistats.clarity.io.s2.FieldType;
import skadistats.clarity.io.s2.S2DTClass;
import skadistats.clarity.io.s2.S2DecoderFactory;
import skadistats.clarity.io.s2.Serializer;
import skadistats.clarity.io.s2.SerializerId;
import skadistats.clarity.io.s2.field.ArrayField;
import skadistats.clarity.io.s2.field.VectorField;
import skadistats.clarity.io.s2.field.PointerField;
import skadistats.clarity.io.s2.field.SerializerField;
import skadistats.clarity.io.s2.field.ValueField;
import skadistats.clarity.logger.PrintfLoggerFactory;
import skadistats.clarity.model.BuildNumberRange;
import skadistats.clarity.wire.dota.s2.proto.S2NetMessages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static skadistats.clarity.LogChannel.sendtables;

public class FieldGenerator {

    private final Logger log = PrintfLoggerFactory.getLogger(sendtables);

    private final S2NetMessages.CSVCMsg_FlattenedSerializer protoMessage;
    private final FieldData[] fieldData;
    private final IntSet checkedNames;
    private final List<PatchFunc> patchFuncs;

    private final Map<SerializerId, Serializer> serializers = new HashMap<>();

    public FieldGenerator(S2NetMessages.CSVCMsg_FlattenedSerializer protoMessage, int buildNumber) {
        this.protoMessage = protoMessage;
        this.fieldData = new FieldData[protoMessage.getFieldsCount()];
        this.checkedNames = new IntOpenHashSet();
        this.patchFuncs = new ArrayList<>();
        for (Map.Entry<BuildNumberRange, PatchFunc> patchEntry : PATCHES.entrySet()) {
            if (patchEntry.getKey().appliesTo(buildNumber)) {
                this.patchFuncs.add(patchEntry.getValue());
            }
        }
    }

    public void createFields() {
        for (int i = 0; i < fieldData.length; i++) {
            fieldData[i] = generateFieldData(protoMessage.getFields(i));
        }
        for (int i = 0; i < protoMessage.getSerializersCount(); i++) {
            Serializer serializer = generateSerializer(protoMessage.getSerializers(i));
            serializers.put(serializer.getId(), serializer);
        }
    }

    public S2DTClass createDTClass(String name) {
        SerializerField field = new SerializerField(
                FieldType.forString(name),
                serializers.get(new SerializerId(name, 0))
        );
        return new S2DTClass(field);
    }

    private FieldData generateFieldData(S2NetMessages.ProtoFlattenedSerializerField_t proto) {
        return new FieldData(
                FieldType.forString(sym(proto.getVarTypeSym())),
                fieldNameFunction(proto),
                new ProtoDecoderProperties(
                        proto.hasEncodeFlags() ? proto.getEncodeFlags() : null,
                        proto.hasBitCount() ? proto.getBitCount() : null,
                        proto.hasLowValue() ? proto.getLowValue() : null,
                        proto.hasHighValue() ? proto.getHighValue() : null,
                        proto.hasVarEncoderSym() ? sym(proto.getVarEncoderSym()) : null
                ),
                proto.hasFieldSerializerNameSym() ?
                        new SerializerId(
                                sym(proto.getFieldSerializerNameSym()),
                                proto.getFieldSerializerVersion()
                        ) : null
        );
    }

    private Serializer generateSerializer(S2NetMessages.ProtoFlattenedSerializer_t proto) {
        SerializerId sid = new SerializerId(
                sym(proto.getSerializerNameSym()),
                proto.getSerializerVersion()
        );
        Field[] fields = new Field[proto.getFieldsIndexCount()];
        String[] fieldNames = new String[proto.getFieldsIndexCount()];
        for (int i = 0; i < fields.length; i++) {
            int fi = proto.getFieldsIndex(i);
            if (fieldData[fi].field == null) {
                fieldData[fi].field = createField(sid, fieldData[fi]);
            }
            fields[i] = fieldData[fi].field;
            fieldNames[i] = fieldData[fi].name;
        }
        return new Serializer(sid, fields, fieldNames);
    }

    private Field createField(SerializerId sId, FieldData fd) {
        for (PatchFunc patchFunc : patchFuncs) {
            patchFunc.execute(sId, fd);
        }

        FieldType elementType;
        switch (fd.category) {
            case ARRAY:
                elementType = fd.fieldType.getElementType();
                break;
            case VECTOR:
                elementType = fd.fieldType.getGenericType();
                break;
            default:
                elementType = fd.fieldType;
        }

        Field elementField;
        if (fd.serializerId != null) {
            if (fd.category == FieldCategory.POINTER) {
                elementField = new PointerField(
                        elementType,
                        serializers.get(fd.serializerId)
                );
            } else {
                elementField = new SerializerField(
                        elementType,
                        serializers.get(fd.serializerId)
                );
            }
        } else {
            elementField = new ValueField(
                    elementType,
                    S2DecoderFactory.createDecoder(fd.decoderProperties, elementType.getBaseType())
            );
        }

        switch (fd.category) {
            case ARRAY:
                return new ArrayField(
                        fd.fieldType,
                        elementField,
                        fd.getArrayElementCount()
                );
            case VECTOR:
                return new VectorField(
                        fd.fieldType,
                        elementField
                );
            default:
                return elementField;
        }
    }

    private String sym(int i) {
        return protoMessage.getSymbols(i);
    }

    private String fieldNameFunction(S2NetMessages.ProtoFlattenedSerializerField_t field) {
        int nameSym = field.getVarNameSym();
        String name = sym(nameSym);
        if (!checkedNames.contains(nameSym)) {
            if (name.indexOf('.') != -1) {
                log.warn("replay contains field with invalid name '%s'. Please open a github issue!", name);
            }
            checkedNames.add(nameSym);
        }
        return name;
    }



    private enum FieldCategory {
        POINTER,
        VECTOR,
        ARRAY,
        VALUE
    }

    private static class FieldData {
        private final FieldType fieldType;
        private final String name;
        private final ProtoDecoderProperties decoderProperties;
        private final SerializerId serializerId;

        private final FieldCategory category;
        private Field field;

        public FieldData(FieldType fieldType, String name, ProtoDecoderProperties decoderProperties, SerializerId serializerId) {
            this.fieldType = fieldType;
            this.name = name;
            this.decoderProperties = decoderProperties;
            this.serializerId = serializerId;

            if (determineIsPointer()) {
                category = FieldCategory.POINTER;
            } else if (determineIsVector()) {
                category = FieldCategory.VECTOR;
            } else if (determineIsArray()) {
                category = FieldCategory.ARRAY;
            } else {
                category = FieldCategory.VALUE;
            }
        }

        private boolean determineIsPointer() {
            if (fieldType.isPointer()) return true;
            switch (fieldType.getBaseType()) {
                case "CBodyComponent":
                case "CLightComponent":
                case "CPhysicsComponent":
                case "CRenderComponent":
                case "CPlayerLocalData":
                    return true;
            }
            return false;
        }

        private boolean determineIsVector() {
            if (serializerId != null) return true;
            switch(fieldType.getBaseType()) {
                case "CUtlVector":
                case "CNetworkUtlVectorBase":
                    return true;
                default:
                    return false;
            }
        }

        private boolean determineIsArray() {
            return fieldType.getElementCount() != null && !"char".equals(fieldType.getBaseType());
        }

        private int getArrayElementCount() {
            String elementCount = fieldType.getElementCount();
            switch (elementCount) {
                case "MAX_ITEM_STOCKS":
                    return 8;
                case "MAX_ABILITY_DRAFT_ABILITIES":
                    return 48;
                default:
                    return Integer.parseInt(elementCount);
            }
        }

    }



    private static final SerializerId SID_PITCH_YAW = new SerializerId("CBodyComponentBaseAnimatingOverlay", 3);

    private interface PatchFunc {
        void execute(SerializerId serializerId, FieldData field);
    }

    private static final Map<BuildNumberRange, PatchFunc> PATCHES = new LinkedHashMap<>();
    static {

        PATCHES.put(new BuildNumberRange(null, 954), (serializerId, field) -> {
            switch (field.name) {
                case "m_flMana":
                case "m_flMaxMana":
                    ProtoDecoderProperties up = field.decoderProperties;
                    if (up.highValue == 3.4028235E38f) {
                        up.lowValue = null;
                        up.highValue = 8192.0f;
                    }
            }
        });

        PATCHES.put(new BuildNumberRange(null, 990), (serializerId, field) -> {
            switch (field.name) {
                case "dirPrimary":
                case "localSound":
                case "m_attachmentPointBoneSpace":
                case "m_attachmentPointRagdollSpace":
                case "m_flElasticity":
                case "m_location":
                case "m_poolOrigin":
                case "m_ragPos":
                case "m_vecEndPos":
                case "m_vecEyeExitEndpoint":
                case "m_vecGunCrosshair":
                case "m_vecLadderDir":
                case "m_vecPlayerMountPositionBottom":
                case "m_vecPlayerMountPositionTop":
                case "m_viewtarget":
                case "m_WorldMaxs":
                case "m_WorldMins":
                case "origin":
                case "vecExtraLocalOrigin":
                case "vecLocalOrigin":
                    field.decoderProperties.encoderType = "coord";
                    break;

                case "angExtraLocalAngles":
                case "angLocalAngles":
                case "m_angInitialAngles":
                case "m_ragAngles":
                case "m_vLightDirection":
                    field.decoderProperties.encoderType = "QAngle";
                    break;

                case "m_vecLadderNormal":
                    field.decoderProperties.encoderType = "normal";
                    break;

                case "m_angRotation":
                    field.decoderProperties.encoderType = SID_PITCH_YAW.equals(serializerId) ? "qangle_pitch_yaw" : "QAngle";
                    break;
            }
        });

        PATCHES.put(new BuildNumberRange(1016, 1026), (serializerId, field) -> {
            switch (field.name) {
                case "m_bWorldTreeState":
                case "m_ulTeamLogo":
                case "m_ulTeamBaseLogo":
                case "m_ulTeamBannerLogo":
                case "m_iPlayerIDsInControl":
                case "m_bItemWhiteList":
                case "m_iPlayerSteamID":
                    field.decoderProperties.encoderType = "fixed64";
            }
        });

        PATCHES.put(new BuildNumberRange(null, null), (serializerId, field) -> {
            switch (field.name) {
                case "m_flSimulationTime":
                case "m_flAnimTime":
                    field.decoderProperties.encoderType = "simulationtime";
            }
        });

        PATCHES.put(new BuildNumberRange(null, null), (serializerId, field) -> {
            switch (field.name) {
                case "m_flRuneTime":
                    ProtoDecoderProperties up = field.decoderProperties;
                    if (up.highValue == Float.MAX_VALUE && up.lowValue == -Float.MAX_VALUE) {
                        up.lowValue = null;
                        up.highValue = null;
                    }
            }
        });

    }

}
