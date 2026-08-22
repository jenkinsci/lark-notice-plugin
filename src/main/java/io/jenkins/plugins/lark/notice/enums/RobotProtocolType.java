package io.jenkins.plugins.lark.notice.enums;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * High-level robot protocol families supported by the plugin.
 *
 * @author xm.z
 */
public enum RobotProtocolType {

    LARK_COMPATIBLE,
    DING_TALK,
    WECHAT_WORK;

    /**
     * Resolves one protocol from a persisted or submitted string value.
     *
     * @param value raw enum value
     * @return matching protocol, or {@code null} when the value is blank or unknown
     */
    public static RobotProtocolType fromValue(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return RobotProtocolType.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Infers one protocol from a full webhook URL.
     *
     * @param webhook webhook URL
     * @return inferred protocol, or {@code null} when the webhook is unsupported
     */
    public static RobotProtocolType inferFromWebhook(String webhook) {
        RobotType robotType = RobotType.fromUrl(webhook);
        if (robotType == null) {
            return null;
        }
        if (RobotType.DING_TALK.equals(robotType)) {
            return DING_TALK;
        }
        if (RobotType.WECHAT_WORK.equals(robotType)) {
            return WECHAT_WORK;
        }
        return LARK_COMPATIBLE;
    }

    /**
     * Maps a runtime robot type back to its protocol family.
     *
     * @param robotType runtime robot type
     * @return matching protocol family
     */
    public static RobotProtocolType fromRobotType(RobotType robotType) {
        if (robotType == null) {
            return null;
        }
        return switch (robotType) {
            case LARK -> LARK_COMPATIBLE;
            case DING_TALK -> DING_TALK;
            case WECHAT_WORK -> WECHAT_WORK;
        };
    }

    /**
     * Returns the message types supported by this protocol family.
     *
     * @return unmodifiable set of supported message types
     */
    public Set<MsgTypeEnum> supportedTypes() {
        return Collections.unmodifiableSet(switch (this) {
            case LARK_COMPATIBLE -> EnumSet.of(MsgTypeEnum.TEXT, MsgTypeEnum.IMAGE, MsgTypeEnum.SHARE_CHAT,
                    MsgTypeEnum.POST, MsgTypeEnum.MARKDOWN, MsgTypeEnum.CARD);
            case DING_TALK -> EnumSet.of(MsgTypeEnum.TEXT, MsgTypeEnum.MARKDOWN, MsgTypeEnum.LINK,
                    MsgTypeEnum.CARD, MsgTypeEnum.FEED_CARD);
            case WECHAT_WORK -> EnumSet.of(MsgTypeEnum.TEXT, MsgTypeEnum.MARKDOWN, MsgTypeEnum.LINK,
                    MsgTypeEnum.POST, MsgTypeEnum.CARD);
        });
    }

    /**
     * Reports whether a message type is supported by this protocol family.
     *
     * @param type message type to check
     * @return {@code true} when supported
     */
    public boolean supports(MsgTypeEnum type) {
        return type != null && supportedTypes().contains(type);
    }

    /**
     * Maps the protocol family to the runtime sender type.
     *
     * @return runtime sender type
     */
    public RobotType toRobotType() {
        return switch (this) {
            case DING_TALK -> RobotType.DING_TALK;
            case WECHAT_WORK -> RobotType.WECHAT_WORK;
            case LARK_COMPATIBLE -> RobotType.LARK;
        };
    }
}
