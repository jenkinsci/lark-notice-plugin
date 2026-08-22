package io.jenkins.plugins.lark.notice.sdk.model.lark;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author xm.z
 */
@Data
@EqualsAndHashCode(callSuper = true)
// imgKey / token below are Lark resource identifiers (image keys, built-in icon names),
// not credentials, so the plaintext-storage warning does not apply.
@SuppressWarnings("lgtm[jenkins/plaintext-storage]")
public class LarkImageMessage extends BaseLarkMessage {

    private ImageContent content;

    public LarkImageMessage(ImageContent content) {
        this.content = content;
        setMsgType("image");
    }

    public static LarkImageMessage build(String text) {
        ImageContent content = new ImageContent(text);
        return new LarkImageMessage(content);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageContent implements Serializable {

        @JsonProperty("image_key")
        private String imageKey;

    }

}
