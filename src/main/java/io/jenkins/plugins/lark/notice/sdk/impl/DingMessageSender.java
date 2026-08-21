package io.jenkins.plugins.lark.notice.sdk.impl;

import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.RobotConfigModel;
import io.jenkins.plugins.lark.notice.model.payload.DingPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.ding.DingCardMessage;
import io.jenkins.plugins.lark.notice.sdk.model.ding.DingLinkMessage;
import io.jenkins.plugins.lark.notice.sdk.model.ding.DingMdMessage;
import io.jenkins.plugins.lark.notice.sdk.model.ding.DingTextMessage;
import io.jenkins.plugins.lark.notice.tools.JsonUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

import static io.jenkins.plugins.lark.notice.sdk.constant.Constants.LF;

/**
 * DingTalk implementation for sending group robot messages.
 *
 * @author xm.z
 */
public class DingMessageSender extends AbstractMessageSender<DingPayload> {

    public DingMessageSender(RobotConfigModel robotConfig) {
        super(robotConfig);
    }

    /**
     * Prepends the user-defined card rows to the body. DingTalk action cards carry a Markdown body,
     * so custom rows render as body lines rather than a structured list.
     */
    private static String withCardFields(MessageIntent intent) {
        String fieldLines = intent.renderCardFieldLines();
        String text = StringUtils.defaultString(intent.getText());
        if (fieldLines == null) {
            return text;
        }
        return StringUtils.isBlank(text) ? fieldLines : fieldLines + LF + LF + text;
    }

    @Override
    public Class<DingPayload> payloadType() {
        return DingPayload.class;
    }

    protected String[] signHeaders() {
        String[] headers = new String[]{};
        if (StringUtils.isNotBlank(robotConfig.getSign())) {
            long timestamp = System.currentTimeMillis();
            headers = new String[]{
                    "timestamp", String.valueOf(timestamp),
                    "sign", robotConfig.createSign(timestamp)
            };
        }
        return headers;
    }

    @Override
    public SendResult sendText(BuildContext ctx, MessageIntent intent, DingPayload payload) {
        String text = addKeyWord(intent.getText(), robotConfig.getKeys());
        DingTextMessage message = DingTextMessage.build(intent.getAt(), text);
        return sendMessage(JsonUtils.toJson(message), signHeaders());
    }

    @Override
    public SendResult sendMarkdown(BuildContext ctx, MessageIntent intent, DingPayload payload) {
        String text = addKeyWord(intent.getText(), robotConfig.getKeys());
        DingMdMessage message = DingMdMessage.build(intent.getAt(), intent.getTitle(), text);
        return sendMessage(JsonUtils.toJson(message), signHeaders());
    }

    @Override
    public SendResult sendLink(BuildContext ctx, MessageIntent intent, DingPayload payload) {
        String text = addKeyWord(intent.getText(), robotConfig.getKeys());
        DingLinkMessage message = DingLinkMessage.build(intent.getAt(), intent.getTitle(), text,
                intent.getPicUrl(), intent.getMessageUrl());
        return sendMessage(JsonUtils.toJson(message), signHeaders());
    }

    @Override
    public SendResult sendCard(BuildContext ctx, MessageIntent intent, DingPayload payload) {
        DingCardMessage message;
        String text = addKeyWord(withCardFields(intent), robotConfig.getKeys());
        String singleTitle = payload == null ? null : payload.getSingleTitle();
        if (StringUtils.isNotBlank(singleTitle)) {
            message = DingCardMessage.build(intent.getAt(), intent.getTitle(), text, singleTitle, payload.getSingleUrl());
        } else {
            List<DingCardMessage.Button> buttons = CollectionUtils.isEmpty(intent.getButtons()) ? null : intent.getButtons().stream()
                    .map(button -> new DingCardMessage.Button(button.getText(), button.getUrl()))
                    .collect(Collectors.toList());
            String btnOrientation = payload == null ? null : payload.getBtnOrientation();
            message = DingCardMessage.build(intent.getAt(), intent.getTitle(), text, btnOrientation, buttons);
        }
        return sendMessage(JsonUtils.toJson(message), signHeaders());
    }
}
