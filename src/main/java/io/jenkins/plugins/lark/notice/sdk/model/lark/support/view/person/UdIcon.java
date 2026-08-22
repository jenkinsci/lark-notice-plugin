package io.jenkins.plugins.lark.notice.sdk.model.lark.support.view.person;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 图标库中的前缀图标配置（ud_icon）
 *
 * @author xm.z
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
// imgKey / token below are Lark resource identifiers (image keys, built-in icon names),
// not credentials, so the plaintext-storage warning does not apply.
@SuppressWarnings("lgtm[jenkins/plaintext-storage]")
public class UdIcon {

    /**
     * 图标的 token
     */
    private String token;

    /**
     * 图标样式，支持设置颜色
     */
    private Style style;

}

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Style {
    /**
     * 图标颜色
     */
    private String color;
}