package io.jenkins.plugins.lark.notice.model;

import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.at.At;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.view.img.ImgElement;
import io.jenkins.plugins.lark.notice.tools.Utils;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross-platform rendering intent describing <em>what</em> the user wants to send and the shared
 * presentation elements common to Lark, DingTalk and WeCom (title, text, @mentions, buttons,
 * images, click-through URLs). Platform-specific extras live in {@link payload.PlatformPayload}
 * subtypes and are carried alongside an intent by the dispatcher.
 *
 * @author xm.z
 */
@Getter
@Setter
@ToString
@Builder(toBuilder = true)
public class MessageIntent {

    /** The message type, determining the format or channel of the message. */
    private MsgTypeEnum type;

    /** Build status used to derive card theme color when not otherwise provided. */
    private BuildStatusEnum statusType;

    /** Message title, displayed prominently. May be blank to use a default. */
    private String title;

    /** Main text content of the message (Markdown or plain depending on type/platform). */
    private String text;

    /** User identifiers to @mention. Split into mobile vs id groups by {@link #getAt()}. */
    private Set<String> atUserIds;

    /** Whether to @mention everyone in scope. */
    private boolean atAll;

    /** Interactive buttons shared by Lark interactive cards and DingTalk action cards. */
    private List<Button> buttons;

    /** Top image element shared by Lark cards and WeCom template cards. */
    private ImgElement topImg;

    /** Image URL used by DingTalk link messages and as a WeCom card image fallback. */
    private String picUrl;

    /** Click-through URL opened by link-style messages or card body clicks. */
    private String messageUrl;

    /**
     * Derives the card header theme color from {@link #statusType}.
     *
     * @return theme color for the card title
     */
    public String obtainHeaderTemplate() {
        return (Objects.nonNull(statusType) ? statusType : BuildStatusEnum.START).getTemplate();
    }

    /**
     * Builds an {@link At} view-model from {@link #atUserIds} and {@link #atAll}, partitioning
     * identifiers into mobile vs id lists.
     *
     * @return mention settings
     */
    public At getAt() {
        At at = new At();
        at.setIsAtAll(atAll);
        if (atUserIds != null) {
            Map<Boolean, List<String>> partitioned = atUserIds.stream()
                    .map(String::trim).filter(StringUtils::isNotBlank)
                    .collect(Collectors.partitioningBy(Utils::isMobile));
            at.setAtUserIds(partitioned.get(false));
            at.setAtMobiles(partitioned.get(true));
        }
        return at;
    }
}
