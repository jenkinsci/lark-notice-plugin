package io.jenkins.plugins.lark.notice.model.payload;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * WeCom-only payload consumed solely by {@code WechatWorkMessageSender} for the
 * {@code news_notice} template card.
 * <p>The card's horizontal rows are expressed as a mutable {@link #cardFields} list so callers
 * can add/remove/relabel rows (issue #288 point 3). {@link #sourceDesc} lets callers customize
 * the source line text; the source icon is fixed by WeCom and {@code main_title} is a required
 * API field, so neither is removable.
 *
 * @author xm.z
 */
@Getter
@Builder
public class WeComPayload implements PlatformPayload {

    /** Extra free-form text appended as a {@code vertical_content_list} entry, overriding structured rows. */
    private final String additionalContent;

    /** Customizable source description shown in the card header source line. */
    private final String sourceDesc;

    /** Customizable card rows. When empty the sender builds default build-info rows from the context. */
    private final List<CardField> cardFields;
}
