package io.jenkins.plugins.lark.notice.model.payload;

import lombok.Builder;
import lombok.Getter;

/**
 * WeCom-only payload consumed solely by {@code WechatWorkMessageSender} for the
 * {@code news_notice} template card.
 * <p>{@link #sourceDesc} lets callers customize the source line text; the source icon is fixed by
 * WeCom and {@code main_title} is a required API field, so neither is removable. Custom card rows
 * live on {@code MessageIntent} because every platform can render them.
 *
 * @author xm.z
 */
@Getter
@Builder
public class WeComPayload implements PlatformPayload {

    /**
     * Extra free-form text appended as a {@code vertical_content_list} entry, overriding structured rows.
     */
    private final String additionalContent;

    /**
     * Customizable source description shown in the card header source line.
     */
    private final String sourceDesc;

    /**
     * Title of the {@code quote_area} block.
     */
    private final String quoteTitle;

    /**
     * Body text of the {@code quote_area} block.
     */
    private final String quoteText;

    /**
     * Jump target of the {@code quote_area} block. When set the block becomes clickable.
     */
    private final String quoteUrl;
}
