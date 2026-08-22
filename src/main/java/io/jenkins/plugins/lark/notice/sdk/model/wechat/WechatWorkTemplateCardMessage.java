package io.jenkins.plugins.lark.notice.sdk.model.wechat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import io.jenkins.plugins.lark.notice.model.*;
import io.jenkins.plugins.lark.notice.model.payload.WeComPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.Serializable;
import java.util.List;

/**
 * WeCom news-notice template card message.
 *
 * @author xm.z
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WechatWorkTemplateCardMessage extends BaseWechatWorkMessage {

    private static final String DEFAULT_SOURCE_ICON_URL = "https://get.jenkins.io/art/jenkins-logo/favicon.ico";

    private static final String DEFAULT_CARD_IMAGE_URL = "https://www.jenkins.io/images/post-images/2025/07/24/redesigning-jenkins-part-two.png";

    private static final String DEFAULT_SOURCE_DESCRIPTION = "Lark Notice · Jenkins";

    private static final String DEFAULT_TITLE = "Jenkins Build Notice";

    private static final String CARD_TYPE_NEWS_NOTICE = "news_notice";

    private static final int LINK_TYPE = 1;

    private static final int TEXT_TYPE = 0;

    private static final int MAX_JUMP_ITEMS = 3;

    private static final int MAX_HORIZONTAL_CONTENT_ITEMS = 6;

    private static final double CARD_IMAGE_ASPECT_RATIO = 2.25d;

    @JsonProperty("template_card")
    private TemplateCard templateCard;

    public WechatWorkTemplateCardMessage(TemplateCard templateCard) {
        this.templateCard = templateCard;
        setMsgType("template_card");
    }

    /**
     * Builds a news-notice template card from the layered message model.
     *
     * @param ctx     shared build context supplying default card rows when the payload does not override them
     * @param intent  cross-platform rendering intent (title, images, buttons, click-through URL)
     * @param payload WeCom-specific payload (custom card rows, source description, additional content)
     * @return assembled template card message
     */
    public static WechatWorkTemplateCardMessage build(BuildContext ctx, MessageIntent intent,
                                                      WeComPayload payload) {
        String title = StringUtils.defaultIfBlank(intent.getTitle(), DEFAULT_TITLE);
        String actionUrl = StringUtils.defaultIfBlank(resolveActionUrl(intent), ctx.getJobUrl());
        TemplateCard card = new TemplateCard();
        card.setCardType(CARD_TYPE_NEWS_NOTICE);
        card.setSource(buildSource(ctx, payload));
        card.setMainTitle(new MainTitle(title, null));
        card.setCardImage(buildCardImage(intent));
        card.setHorizontalContentList(resolveHorizontalContentList(ctx, intent));
        card.setVerticalContentList(resolveVerticalContentList(ctx, intent, payload));
        card.setQuoteArea(resolveQuoteArea(payload));
        card.setJumpList(resolveJumpList(intent));
        card.setCardAction(new CardAction(LINK_TYPE, actionUrl));
        return new WechatWorkTemplateCardMessage(card);
    }

    private static Source buildSource(BuildContext ctx, WeComPayload payload) {
        String desc = StringUtils.defaultIfBlank(payload.getSourceDesc(), DEFAULT_SOURCE_DESCRIPTION);
        return new Source(DEFAULT_SOURCE_ICON_URL, desc, resolveSourceColor(ctx.getStatusType()));
    }

    private static CardImage buildCardImage(MessageIntent intent) {
        return new CardImage(resolveCardImageUrl(intent), CARD_IMAGE_ASPECT_RATIO);
    }

    private static String resolveActionUrl(MessageIntent intent) {
        if (!CollectionUtils.isEmpty(intent.getButtons())) {
            return StringUtils.defaultIfBlank(intent.getButtons().get(0).getUrl(), intent.getMessageUrl());
        }
        return StringUtils.defaultIfBlank(intent.getMessageUrl(), null);
    }

    /**
     * Resolves the card image URL. WeCom only accepts public HTTP(S) URLs, so image keys (used by
     * Lark) are ignored. Priority: {@link MessageIntent#getPicUrl()} then a resolved HTTP top-image
     * URL, then the built-in Jenkins image. This fixes the "card image cannot be set" issue.
     */
    private static String resolveCardImageUrl(MessageIntent intent) {
        if (isHttpUrl(intent.getPicUrl())) {
            return intent.getPicUrl();
        }
        if (intent.getTopImg() != null && isHttpUrl(intent.getTopImg().getImgKey())) {
            return intent.getTopImg().getImgKey();
        }
        return DEFAULT_CARD_IMAGE_URL;
    }

    private static boolean isHttpUrl(String value) {
        String url = StringUtils.trimToEmpty(value);
        return Strings.CS.startsWithAny(url, "https://", "http://");
    }

    /**
     * Builds the quote block when the payload carries any of its parts. {@code type} is derived:
     * 1 (jump to url) when a url is present, otherwise 0 (no click action).
     */
    private static QuoteArea resolveQuoteArea(WeComPayload payload) {
        String title = payload.getQuoteTitle();
        String text = payload.getQuoteText();
        String url = payload.getQuoteUrl();
        if (StringUtils.isAllBlank(title, text, url)) {
            return null;
        }
        return new QuoteArea(StringUtils.isNotBlank(url) ? LINK_TYPE : TEXT_TYPE, url, title, text);
    }

    private static List<Jump> resolveJumpList(MessageIntent intent) {
        if (CollectionUtils.isEmpty(intent.getButtons())) {
            return null;
        }
        return intent.getButtons().stream()
                .filter(button -> StringUtils.isNotBlank(button.getText()) && StringUtils.isNotBlank(button.getUrl()))
                .limit(MAX_JUMP_ITEMS)
                .map(button -> new Jump(LINK_TYPE, button.getUrl(), button.getText()))
                .toList();
    }

    /**
     * Resolves horizontal content rows. Uses payload-supplied {@link CardField}s when present
     * (customizable rows), otherwise builds the default build-info rows from the context.
     * Custom rows are capped at {@link #MAX_HORIZONTAL_CONTENT_ITEMS} because WeCom rejects
     * longer lists; the default rows never exceed that cap.
     */
    private static List<HorizontalContent> resolveHorizontalContentList(BuildContext ctx, MessageIntent intent) {
        if (CollectionUtils.isNotEmpty(intent.getCardFields())) {
            return intent.getCardFields().stream()
                    .filter(field -> StringUtils.isNotBlank(field.getValue()))
                    .limit(MAX_HORIZONTAL_CONTENT_ITEMS)
                    .map(WechatWorkTemplateCardMessage::toHorizontalContent)
                    .toList();
        }
        List<HorizontalContent> contents = BuildFields.of(ctx).stream()
                .filter(field -> StringUtils.isNotBlank(field.value()))
                .map(WechatWorkTemplateCardMessage::toHorizontalContent)
                .toList();
        return contents.isEmpty() ? null : contents;
    }

    private static HorizontalContent toHorizontalContent(BuildField field) {
        boolean hasUrl = StringUtils.isNotBlank(field.url());
        return new HorizontalContent(hasUrl ? LINK_TYPE : TEXT_TYPE,
                field.label(), field.value(), hasUrl ? field.url() : null);
    }

    private static HorizontalContent toHorizontalContent(CardField field) {
        int type = field.resolveType();
        String url = type == LINK_TYPE ? field.getUrl() : null;
        return new HorizontalContent(type, field.getKeyname(), field.getValue(), url);
    }

    private static List<VerticalContent> resolveVerticalContentList(BuildContext ctx, MessageIntent intent,
                                                                    WeComPayload payload) {
        if (StringUtils.isNotBlank(payload.getAdditionalContent())) {
            return List.of(new VerticalContent(StringUtils.defaultString(intent.getTitle()), payload.getAdditionalContent()));
        }
        // The structured rows already carry the build information, so there is no text block to
        // add. Nothing else can reach here: a card is always built from a populated BuildContext.
        return null;
    }

    private static int resolveSourceColor(BuildStatusEnum statusType) {
        if (BuildStatusEnum.FAILURE.equals(statusType) || BuildStatusEnum.UNSTABLE.equals(statusType)) {
            return 2;
        }
        if (BuildStatusEnum.SUCCESS.equals(statusType)) {
            return 3;
        }
        return 0;
    }

    @Data
    @NoArgsConstructor
    public static class TemplateCard implements Serializable {

        @JsonProperty("card_type")
        private String cardType;

        private Source source;

        @JsonProperty("main_title")
        private MainTitle mainTitle;

        @JsonProperty("card_image")
        private CardImage cardImage;

        @JsonProperty("vertical_content_list")
        private List<VerticalContent> verticalContentList;

        @JsonProperty("horizontal_content_list")
        private List<HorizontalContent> horizontalContentList;

        @JsonProperty("jump_list")
        private List<Jump> jumpList;

        // Omitted entirely when unset: an existing assertion pins quote_area's absence, and WeCom
        // has no use for an explicitly null block.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("quote_area")
        private QuoteArea quoteArea;

        @JsonProperty("card_action")
        private CardAction cardAction;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source implements Serializable {

        @JsonProperty("icon_url")
        private String iconUrl;

        private String desc;

        @JsonProperty("desc_color")
        private Integer descColor;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainTitle implements Serializable {

        private String title;

        private String desc;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardImage implements Serializable {

        private String url;

        @JsonProperty("aspect_ratio")
        private Double aspectRatio;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerticalContent implements Serializable {

        private String title;

        private String desc;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HorizontalContent implements Serializable {

        private Integer type;

        private String keyname;

        private String value;

        private String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Jump implements Serializable {

        private Integer type;

        private String url;

        private String title;
    }

    /**
     * WeCom {@code quote_area}: a quoted-reference block with an optional jump target.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteArea implements Serializable {

        private Integer type;

        private String url;

        private String title;

        @JsonProperty("quote_text")
        private String quoteText;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardAction implements Serializable {

        private Integer type;

        private String url;
    }
}
