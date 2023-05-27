package skadistats.clarity.model.csgo;

import com.google.protobuf.ByteString;
import com.google.protobuf.ZeroCopy;
import skadistats.clarity.io.Util;
import skadistats.clarity.wire.Packet;
import skadistats.clarity.wire.csgo.s2.proto.CSGOS2ClarityMessages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class PlayerInfoType {

    private static final int MAX_PLAYER_NAME_LENGTH = 128;
    private static final int SIGNED_GUID_LEN = 32;
    private static final int MAX_CUSTOM_FILES = 4;

    private final long version;
    private final long xuid;
    private final String name;
    private final int userId;
    private final String guid;
    private final int friendsId;
    private final String friendsName;
    private final boolean fakePlayer;
    private final boolean isHltv;
    private final int[] customFiles;

    public static PlayerInfoType createS1(ByteString byteString) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(ZeroCopy.extract(byteString)).order(ByteOrder.BIG_ENDIAN);
        long version = buf.getLong();
        long xuid = buf.getLong();
        String name = readZeroTerminated(buf, MAX_PLAYER_NAME_LENGTH);
        int userId = buf.getInt();
        String guid = readZeroTerminated(buf, SIGNED_GUID_LEN + 1);
        int friendsId = buf.getInt();
        buf.position(buf.position() + 3);
        String friendsName = readZeroTerminated(buf, MAX_PLAYER_NAME_LENGTH);
        boolean fakePlayer = buf.get() == (byte)1;
        boolean isHltv = buf.get() == (byte)1;
        int[] customFiles = new int[MAX_CUSTOM_FILES];
        for (int i = 0; i < MAX_CUSTOM_FILES; i++) {
            customFiles[i] = buf.getInt();
        }
        // TODO: still 6 bytes remaining, so something is wrong...
        return new PlayerInfoType(version, xuid, name, userId, guid, friendsId, friendsName, fakePlayer, isHltv, customFiles);
    }

    public static PlayerInfoType createS2(ByteString byteString) {
        CSGOS2ClarityMessages.PlayerInfo msg = Packet.parse(CSGOS2ClarityMessages.PlayerInfo.class, byteString);
        return new PlayerInfoType(
                -1L,
                msg.getXuid(),
                msg.getName(),
                -1,
                "",
                -1,
                "",
                msg.getFakePlayer(),
                msg.getIsHlTv(),
                new int[MAX_CUSTOM_FILES]
        );
    }

    private static String readZeroTerminated(ByteBuffer buffer, int size) throws IOException {
        return Util.readFixedZeroTerminated(buffer, size);
    }

    private PlayerInfoType(long version, long xuid, String name, int userId, String guid, int friendsId, String friendsName, boolean fakePlayer, boolean isHltv, int[] customFiles) {
        this.version = version;
        this.xuid = xuid;
        this.name = name;
        this.userId = userId;
        this.guid = guid;
        this.friendsId = friendsId;
        this.friendsName = friendsName;
        this.fakePlayer = fakePlayer;
        this.isHltv = isHltv;
        this.customFiles = customFiles;
    }

    public long getVersion() {
        return version;
    }

    public long getXuid() {
        return xuid;
    }

    public String getName() {
        return name;
    }

    public int getUserId() {
        return userId;
    }

    public String getGuid() {
        return guid;
    }

    public int getFriendsId() {
        return friendsId;
    }

    public String getFriendsName() {
        return friendsName;
    }

    public boolean isFakePlayer() {
        return fakePlayer;
    }

    public boolean isHltv() {
        return isHltv;
    }

    public int[] getCustomFiles() {
        return customFiles;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PlayerInfoType{");
        sb.append("version=").append(version);
        sb.append(", xuid=").append(xuid);
        sb.append(", name='").append(name).append('\'');
        sb.append(", userId=").append(userId);
        sb.append(", guid='").append(guid).append('\'');
        sb.append(", friendsId=").append(friendsId);
        sb.append(", friendsName='").append(friendsName).append('\'');
        sb.append(", fakePlayer=").append(fakePlayer);
        sb.append(", isHltv=").append(isHltv);
        sb.append(", customFiles=");
        if (customFiles == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < customFiles.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(customFiles[i]);
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlayerInfoType that = (PlayerInfoType) o;

        if (version != that.version) return false;
        if (xuid != that.xuid) return false;
        if (userId != that.userId) return false;
        if (friendsId != that.friendsId) return false;
        if (fakePlayer != that.fakePlayer) return false;
        if (isHltv != that.isHltv) return false;
        if (!name.equals(that.name)) return false;
        if (!guid.equals(that.guid)) return false;
        if (!friendsName.equals(that.friendsName)) return false;
        return Arrays.equals(customFiles, that.customFiles);
    }

    @Override
    public int hashCode() {
        int result = (int) (version ^ (version >>> 32));
        result = 31 * result + (int) (xuid ^ (xuid >>> 32));
        result = 31 * result + name.hashCode();
        result = 31 * result + userId;
        result = 31 * result + guid.hashCode();
        result = 31 * result + friendsId;
        result = 31 * result + friendsName.hashCode();
        result = 31 * result + (fakePlayer ? 1 : 0);
        result = 31 * result + (isHltv ? 1 : 0);
        result = 31 * result + Arrays.hashCode(customFiles);
        return result;
    }

}
