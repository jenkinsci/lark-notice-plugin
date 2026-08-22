package io.jenkins.plugins.lark.notice.model.payload;

import lombok.Builder;
import lombok.Getter;

/**
 * WeCom-only payload consumed solely by {@code WechatWorkMessageSender} for the
 * {@code news_notice} template card.
 * <p>{@link #sourceDesc}, {@link #sourceIconUrl} and {@link #cardImageUrl} customize the card
 * header and banner, each falling back to a built-in default when blank. {@code main_title} is a
 * required API field, so it is never dropped. Custom card rows live on {@code MessageIntent}
 * because every platform can render them.
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
     * Icon shown next to {@link #sourceDesc}. WeCom only renders public HTTP(S) URLs, so any other
     * value keeps the built-in Jenkins icon.
     */
    private final String sourceIconUrl;

    /**
     * Banner image of the card, the {@code card_image.url} field. WeCom only renders public HTTP(S)
     * URLs, so any other value keeps the built-in image.
     */
    private final String cardImageUrl;

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
