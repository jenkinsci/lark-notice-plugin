package io.jenkins.plugins.lark.notice.sdk.model.ding;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * DingTalk {@code feedCard} message: a list of image-and-title entries, each with its own jump
 * target. Field names follow the custom-robot API, which spells the URLs {@code messageURL} and
 * {@code picURL} here (unlike the {@code link} message type).
 *
 * @author xm.z
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DingFeedCardMessage extends BaseDingMessage {

    private FeedCardContent feedCard;

    public DingFeedCardMessage(FeedCardContent feedCard) {
        this.feedCard = feedCard;
        setMsgType("feedCard");
    }

    /**
     * Builds a feed-card message from the entry list.
     *
     * @param links feed entries
     * @return feed card message
     */
    public static DingFeedCardMessage build(List<FeedCardLink> links) {
        return new DingFeedCardMessage(new FeedCardContent(links));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedCardContent implements Serializable {

        /**
         * feedCard 消息的内容列表
         */
        private List<FeedCardLink> links;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedCardLink implements Serializable {

        /**
         * 每条内容的标题
         */
        private String title;

        /**
         * 每条内容的跳转链接
         */
        @JsonProperty("messageURL")
        private String messageUrl;

        /**
         * 每条内容的图片 URL
         */
        @JsonProperty("picURL")
        private String picUrl;

    }

}
