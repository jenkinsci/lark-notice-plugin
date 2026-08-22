package io.jenkins.plugins.lark.notice.sdk.model.ding;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.at.At;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;

/**
 * @author xm.z
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DingCardMessage extends BaseDingMessage {

    private At at;

    private ActionCardContent actionCard;

    public DingCardMessage(At at, ActionCardContent actionCard) {
        this.at = at;
        this.actionCard = actionCard;
        setMsgType("actionCard");
    }

    /**
     * Builds an action card showing a list of buttons.
     *
     * @param at             mention settings
     * @param title          title shown in the conversation list
     * @param text           card body, Markdown
     * @param btnOrientation {@code 0} vertical, {@code 1} horizontal; defaults to horizontal
     * @param buttons        buttons to render
     * @param hideAvatar     {@code 1} to hide the sender avatar
     * @return action card message
     */
    public static DingCardMessage build(At at, String title, String text, String btnOrientation,
                                        List<Button> buttons, String hideAvatar) {
        ActionCardContent content = new ActionCardContent(title, addAtInfo(text, at, true),
                null, null, StringUtils.defaultIfBlank(btnOrientation, "1"), hideAvatar, buttons);
        return new DingCardMessage(at, content);
    }

    /**
     * Builds an action card whose whole body is one jump target. DingTalk ignores {@code btns} once
     * {@code singleTitle} and {@code singleURL} are set, so this variant carries no button list.
     *
     * @param at          mention settings
     * @param title       title shown in the conversation list
     * @param text        card body, Markdown
     * @param singleTitle label of the single action
     * @param singleUrl   target of the single action
     * @param hideAvatar  {@code 1} to hide the sender avatar
     * @return action card message
     */
    public static DingCardMessage build(At at, String title, String text, String singleTitle,
                                        String singleUrl, String hideAvatar) {
        ActionCardContent content = new ActionCardContent(title,
                addAtInfo(text, at, true), singleTitle, singleUrl, null, hideAvatar, null);
        return new DingCardMessage(at, content);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionCardContent implements Serializable {

        private String title;

        private String text;

        /**
         * 单个按钮的标题, 设置此项和singleURL后，buttons 无效
         */
        private String singleTitle;

        /**
         * 点击消息跳转的URL
         * <p>官方字段名为 {@code singleURL}（大写 URL），与 {@code btns[].actionURL} 一致。
         */
        @JsonProperty("singleURL")
        private String singleUrl;

        /**
         * 0：按钮竖直排列 1：按钮横向排列
         */
        private String btnOrientation;

        /**
         * 0：正常显示发消息者头像 1：隐藏发消息者头像
         */
        private String hideAvatar;

        @JsonProperty("btns")
        private List<Button> buttons;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Button implements Serializable {

        private String title;

        @JsonProperty("actionURL")
        private String url;

    }

}
