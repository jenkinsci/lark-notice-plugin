package io.jenkins.plugins.lark.notice.enums;

import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Pins the full protocol × message-type support matrix. Adding a value to {@link MsgTypeEnum}
 * without deciding which platforms accept it fails here, which is the point: the dispatcher rejects
 * unsupported types, so an omission would silently make a new type unusable everywhere.
 */
public class SupportMatrixTest {

    private static final Map<RobotProtocolType, Set<MsgTypeEnum>> EXPECTED =
            new EnumMap<>(RobotProtocolType.class);

    static {
        EXPECTED.put(RobotProtocolType.LARK_COMPATIBLE, EnumSet.of(
                MsgTypeEnum.TEXT, MsgTypeEnum.IMAGE, MsgTypeEnum.SHARE_CHAT,
                MsgTypeEnum.POST, MsgTypeEnum.MARKDOWN, MsgTypeEnum.CARD));
        EXPECTED.put(RobotProtocolType.DING_TALK, EnumSet.of(
                MsgTypeEnum.TEXT, MsgTypeEnum.MARKDOWN, MsgTypeEnum.LINK,
                MsgTypeEnum.CARD, MsgTypeEnum.FEED_CARD));
        EXPECTED.put(RobotProtocolType.WECHAT_WORK, EnumSet.of(
                MsgTypeEnum.TEXT, MsgTypeEnum.MARKDOWN, MsgTypeEnum.LINK,
                MsgTypeEnum.POST, MsgTypeEnum.CARD));
    }

    @Test
    public void everyProtocolShouldDeclareItsExactSupportedSet() {
        for (RobotProtocolType protocol : RobotProtocolType.values()) {
            assertEquals("supported types for " + protocol,
                    EXPECTED.get(protocol), protocol.supportedTypes());
        }
    }

    @Test
    public void everyMessageTypeShouldBeCoveredByTheMatrix() {
        for (RobotProtocolType protocol : RobotProtocolType.values()) {
            for (MsgTypeEnum type : MsgTypeEnum.values()) {
                assertEquals(protocol + " / " + type,
                        EXPECTED.get(protocol).contains(type), protocol.supports(type));
            }
        }
    }

    /**
     * A new message type must be accepted somewhere, otherwise it is dead on arrival.
     */
    @Test
    public void everyMessageTypeShouldBeSupportedByAtLeastOneProtocol() {
        for (MsgTypeEnum type : MsgTypeEnum.values()) {
            boolean anywhere = false;
            for (RobotProtocolType protocol : RobotProtocolType.values()) {
                anywhere |= protocol.supports(type);
            }
            assertTrue(type + " is not supported by any protocol", anywhere);
        }
    }

    @Test
    public void supportsShouldRejectNull() {
        for (RobotProtocolType protocol : RobotProtocolType.values()) {
            assertFalse(protocol.supports(null));
        }
    }

    /**
     * The accessor documents an unmodifiable set, so callers must not be able to corrupt it.
     */
    @Test
    public void supportedTypesShouldBeUnmodifiable() {
        for (RobotProtocolType protocol : RobotProtocolType.values()) {
            assertThrows(UnsupportedOperationException.class, () -> protocol.supportedTypes().clear());
        }
    }
}
