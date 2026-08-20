package io.jenkins.plugins.lark.notice.sdk.model.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import io.jenkins.plugins.lark.notice.i18n.NoticeI18n;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.CardField;
import io.jenkins.plugins.lark.notice.model.payload.WeComPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * @param content raw markdown/plain text used as vertical content fallback
     * @return assembled template card message
     */
    public static WechatWorkTemplateCardMessage build(BuildContext ctx, MessageIntent intent,
                                                      WeComPayload payload, String content) {
        String title = StringUtils.defaultIfBlank(intent.getTitle(), DEFAULT_TITLE);
        String actionUrl = StringUtils.defaultIfBlank(resolveActionUrl(intent), ctx.getJobUrl());
        TemplateCard card = new TemplateCard();
        card.setCardType(CARD_TYPE_NEWS_NOTICE);
        card.setSource(buildSource(ctx, payload));
        card.setMainTitle(new MainTitle(title, null));
        card.setCardImage(buildCardImage(intent));
        card.setHorizontalContentList(resolveHorizontalContentList(ctx, payload));
        card.setVerticalContentList(resolveVerticalContentList(ctx, intent, payload, content));
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
        return StringUtils.startsWith(url, "https://") || StringUtils.startsWith(url, "http://");
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
     */
    private static List<HorizontalContent> resolveHorizontalContentList(BuildContext ctx, WeComPayload payload) {
        if (CollectionUtils.isNotEmpty(payload.getCardFields())) {
            return payload.getCardFields().stream()
                    .filter(field -> StringUtils.isNotBlank(field.getValue()))
                    .map(WechatWorkTemplateCardMessage::toHorizontalContent)
                    .toList();
        }
        Locale locale = ctx.getLocale();
        List<HorizontalContent> contents = new ArrayList<>();
        addHorizontalContent(contents, NoticeI18n.buildMessageProjectName(locale), ctx.getProjectName(), ctx.getProjectUrl());
        addHorizontalContent(contents, NoticeI18n.buildMessageJobName(locale), ctx.getJobName(), ctx.getJobUrl());
        addHorizontalContent(contents, NoticeI18n.buildMessageStatus(locale), resolveStatusLabel(ctx, locale), null);
        addHorizontalContent(contents, NoticeI18n.buildMessageDuration(locale), ctx.getDuration(), null);
        addHorizontalContent(contents, NoticeI18n.buildMessageExecutor(locale), ctx.getExecutorName(), null);
        return contents.isEmpty() ? null : contents;
    }

    private static HorizontalContent toHorizontalContent(CardField field) {
        int type = field.resolveType();
        String url = type == LINK_TYPE ? field.getUrl() : null;
        return new HorizontalContent(type, field.getKeyname(), field.getValue(), url);
    }

    private static void addHorizontalContent(List<HorizontalContent> contents, String key, String value, String url) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        boolean hasUrl = StringUtils.isNotBlank(url);
        contents.add(new HorizontalContent(hasUrl ? LINK_TYPE : TEXT_TYPE, key, value, hasUrl ? url : null));
    }

    private static List<VerticalContent> resolveVerticalContentList(BuildContext ctx, MessageIntent intent,
                                                                      WeComPayload payload, String content) {
        if (StringUtils.isNotBlank(payload.getAdditionalContent())) {
            return List.of(new VerticalContent(StringUtils.defaultString(intent.getTitle()), payload.getAdditionalContent()));
        }
        // When the card already renders structured build-info rows, skip the vertical text block
        // to avoid duplicating the same content in two places.
        if (hasStructuredBuildFields(ctx)) {
            return null;
        }
        String plainText = toPlainText(content);
        if (StringUtils.isBlank(plainText)) {
            return null;
        }
        return List.of(new VerticalContent(StringUtils.defaultString(intent.getTitle()), plainText));
    }

    private static boolean hasStructuredBuildFields(BuildContext ctx) {
        return StringUtils.isNotBlank(ctx.getProjectName())
                || StringUtils.isNotBlank(ctx.getJobName())
                || StringUtils.isNotBlank(ctx.getDuration())
                || StringUtils.isNotBlank(ctx.getExecutorName());
    }

    private static String toPlainText(String markdown) {
        return StringUtils.defaultString(markdown)
                .replaceAll("(?m)^>\\s*", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "$1")
                .replaceAll("<font\\s+color=['\"][^'\"]+['\"]>(.*?)</font>", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .trim();
    }

    private static String resolveStatusLabel(BuildContext ctx, Locale locale) {
        BuildStatusEnum statusType = ctx.getStatusType();
        return statusType == null ? "" : statusType.getLabel(locale);
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardAction implements Serializable {

        private Integer type;

        private String url;
    }
}
