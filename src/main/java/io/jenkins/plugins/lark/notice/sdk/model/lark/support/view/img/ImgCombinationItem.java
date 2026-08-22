package io.jenkins.plugins.lark.notice.sdk.model.lark.support.view.img;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 多图混排中的单个图片项
 *
 * @author xm.z
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
// imgKey / token below are Lark resource identifiers (image keys, built-in icon names),
// not credentials, so the plaintext-storage warning does not apply.
@SuppressWarnings("lgtm[jenkins/plaintext-storage]")
public class ImgCombinationItem {

    /**
     * 图片的 Key。可通过上传图片接口或在搭建工具中上传图片后获得。
     */
    @JsonProperty("img_key")
    private String imgKey;

}