package io.jenkins.plugins.lark.notice.sdk.impl;

import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.RobotConfigModel;
import io.jenkins.plugins.lark.notice.model.payload.WeComPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.wechat.WechatWorkMarkdownMessage;
import io.jenkins.plugins.lark.notice.sdk.model.wechat.WechatWorkTemplateCardMessage;
import io.jenkins.plugins.lark.notice.sdk.model.wechat.WechatWorkTextMessage;
import io.jenkins.plugins.lark.notice.tools.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static io.jenkins.plugins.lark.notice.sdk.constant.Constants.LF;

/**
 * WeCom implementation for sending group robot messages.
 *
 * @author xm.z
 */
@Slf4j
public class WechatWorkMessageSender extends AbstractMessageSender<WeComPayload> {

    public WechatWorkMessageSender(RobotConfigModel robotConfig) {
        super(robotConfig);
    }

    @Override
    public Class<WeComPayload> payloadType() {
        return WeComPayload.class;
    }

    private static String withTitle(String title, String text) {
        if (StringUtils.isBlank(title)) {
            return text;
        }
        return "## " + title + LF + LF + StringUtils.defaultString(text);
    }

    @Override
    public SendResult sendText(BuildContext ctx, MessageIntent intent, WeComPayload payload) {
        String text = addKeyWord(intent.getText(), robotConfig.getKeys());
        WechatWorkTextMessage message = WechatWorkTextMessage.build(intent.getAt(), text);
        return sendMessage(JsonUtils.toJson(message));
    }

    @Override
    public SendResult sendMarkdown(BuildContext ctx, MessageIntent intent, WeComPayload payload) {
        String text = addKeyWord(intent.getText(), robotConfig.getKeys());
        WechatWorkMarkdownMessage message = WechatWorkMarkdownMessage.build(intent.getAt(), withTitle(intent.getTitle(), text));
        return sendMessage(JsonUtils.toJson(message));
    }

    @Override
    public SendResult sendCard(BuildContext ctx, MessageIntent intent, WeComPayload payload) {
        MessageIntent signed = intent.toBuilder()
                .text(addKeyWord(intent.getText(), robotConfig.getKeys()))
                .build();
        WechatWorkTemplateCardMessage message = WechatWorkTemplateCardMessage.build(ctx, signed, payload);
        return sendMessage(JsonUtils.toJson(message));
    }

    @Override
    public SendResult sendLink(BuildContext ctx, MessageIntent intent, WeComPayload payload) {
        log.debug("WeCom does not support link messages; falling back to markdown");
        return sendMarkdown(ctx, intent, payload);
    }

    @Override
    public SendResult sendPost(BuildContext ctx, MessageIntent intent, WeComPayload payload) {
        log.debug("WeCom does not support post messages; falling back to markdown");
        return sendMarkdown(ctx, intent, payload);
    }
}
