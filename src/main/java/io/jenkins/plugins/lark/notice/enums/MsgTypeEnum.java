package io.jenkins.plugins.lark.notice.enums;

/**
 * Message types selectable by users across robot platforms. Each platform supports a subset
 * (see {@link RobotProtocolType#supportedTypes()}); the dispatcher routes a chosen type to the
 * matching {@code sendX} method on the resolved sender.
 *
 * @author xm.z
 */
public enum MsgTypeEnum {

    /**
     * Plain text message.
     */
    TEXT,

    /**
     * Image message.
     */
    IMAGE,

    /**
     * Shared chat forward (Lark).
     */
    SHARE_CHAT,

    /**
     * Rich-text post (Lark).
     */
    POST,

    /**
     * Link message (DingTalk, WeCom fallback).
     */
    LINK,

    /**
     * Markdown message.
     */
    MARKDOWN,

    /**
     * Interactive card message.
     */
    CARD,

    /**
     * Feed card message: a list of image-and-title entries, each with its own jump target (DingTalk).
     */
    FEED_CARD
}

