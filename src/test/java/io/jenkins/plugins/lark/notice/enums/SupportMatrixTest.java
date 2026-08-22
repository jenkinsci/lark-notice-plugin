package io.jenkins.plugins.lark.notice.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the full protocol × message-type support matrix. Adding a value to {@link MsgTypeEnum}
 * without deciding which platforms accept it fails here, which is the point: the dispatcher rejects
 * unsupported types, so an omission would silently make a new type unusable everywhere.
 *
 * <p>Every combination is its own test case, so a failure names the exact protocol and type instead
 * of stopping at the first mismatch inside a loop.
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

    /**
     * Supplies every protocol × message-type pairing.
     *
     * @return one argument pair per combination
     */
    static Stream<Arguments> combinations() {
        return Arrays.stream(RobotProtocolType.values())
                .flatMap(protocol -> Arrays.stream(MsgTypeEnum.values())
                        .map(type -> Arguments.of(protocol, type)));
    }

    @ParameterizedTest
    @EnumSource(RobotProtocolType.class)
    public void protocolShouldDeclareItsExactSupportedSet(RobotProtocolType protocol) {
        assertEquals(EXPECTED.get(protocol), protocol.supportedTypes());
    }

    @ParameterizedTest(name = "does {0} accept {1}")
    @MethodSource("combinations")
    public void supportShouldMatchTheMatrix(RobotProtocolType protocol, MsgTypeEnum type) {
        assertEquals(EXPECTED.get(protocol).contains(type), protocol.supports(type));
    }

    /**
     * A new message type must be accepted somewhere, otherwise it is dead on arrival.
     */
    @ParameterizedTest
    @EnumSource(MsgTypeEnum.class)
    public void messageTypeShouldBeSupportedByAtLeastOneProtocol(MsgTypeEnum type) {
        assertTrue(Arrays.stream(RobotProtocolType.values()).anyMatch(protocol -> protocol.supports(type)),
                type + " is not supported by any protocol");
    }

    @ParameterizedTest
    @EnumSource(RobotProtocolType.class)
    public void supportsShouldRejectNull(RobotProtocolType protocol) {
        assertFalse(protocol.supports(null));
    }

    /**
     * The accessor documents an unmodifiable set, so callers must not be able to corrupt it.
     */
    @ParameterizedTest
    @EnumSource(RobotProtocolType.class)
    public void supportedTypesShouldBeUnmodifiable(RobotProtocolType protocol) {
        assertThrows(UnsupportedOperationException.class, () -> protocol.supportedTypes().clear());
    }
}
