package io.jenkins.plugins.lark.notice.model.payload;

import lombok.Builder;
import lombok.Getter;

/**
 * DingTalk-only payload fields consumed solely by {@code DingMessageSender}.
 *
 * @author xm.z
 */
@Getter
@Builder
public class DingPayload implements PlatformPayload {

    /**
     * Title of the single-action button on a DingTalk action card. When set with {@link #singleUrl}, buttons are ignored.
     */
    private final String singleTitle;

    /**
     * Target URL of the single-action button on a DingTalk action card.
     */
    private final String singleUrl;

    /**
     * DingTalk action-card button layout: {@code 0} vertical, {@code 1} horizontal.
     */
    private final String btnOrientation;
}
