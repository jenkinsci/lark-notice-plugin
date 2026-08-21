package io.jenkins.plugins.lark.notice.sdk.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.RobotConfigModel;
import io.jenkins.plugins.lark.notice.model.payload.LarkPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.lark.*;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Card;
import io.jenkins.plugins.lark.notice.tools.JsonUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Lark implementation for sending Lark messages.
 *
 * @author xm.z
 */
public class LarkMessageSender extends AbstractMessageSender<LarkPayload> {

    public LarkMessageSender(RobotConfigModel robotConfig) {
        super(robotConfig);
    }

    @Override
    public Class<LarkPayload> payloadType() {
        return LarkPayload.class;
    }

    /**
     * Wraps a message body with robot signing fields.
     *
     * @param message message body
     * @return signed JSON request body
     */
    protected String signToJson(Object message) {
        ObjectNode objectNode = JsonUtils.valueToTree(message);
        if (StringUtils.isNotBlank(robotConfig.getSign())) {
            long timestamp = System.currentTimeMillis() / 1000L;
            objectNode.put("timestamp", String.valueOf(timestamp));
            objectNode.put("sign", robotConfig.createSign(timestamp));
            return JsonUtils.toJson(objectNode);
        }
        return JsonUtils.toJson(objectNode);
    }

    @Override
    public SendResult sendText(BuildContext ctx, MessageIntent intent, LarkPayload payload) {
        String text = addKeyWord(intent.getText(), robotConfig.getKeys());
        LarkTextMessage message = LarkTextMessage.build(intent.getAt(), text);
        return sendMessage(signToJson(message));
    }

    @Override
    public SendResult sendImage(BuildContext ctx, MessageIntent intent, LarkPayload payload) {
        LarkImageMessage message = LarkImageMessage.build(intent.getText());
        return sendMessage(signToJson(message));
    }

    @Override
    public SendResult sendShareChat(BuildContext ctx, MessageIntent intent, LarkPayload payload) {
        LarkShareChatMessage message = LarkShareChatMessage.build(intent.getText());
        return sendMessage(signToJson(message));
    }

    @Override
    public SendResult sendMarkdown(BuildContext ctx, MessageIntent intent, LarkPayload payload) {
        MessageIntent signed = intent.toBuilder()
                .title(addKeyWord(intent.getTitle(), robotConfig.getKeys()))
                .build();
        LarkCardMessage message = LarkCardMessage.build(signed, payload);
        return sendMessage(signToJson(message));
    }

    @Override
    public SendResult sendPost(BuildContext ctx, MessageIntent intent, LarkPayload payload) {
        String title = addKeyWord(intent.getTitle(), robotConfig.getKeys());
        LarkPostMessage message = LarkPostMessage.build(title, intent.getText());
        return sendMessage(signToJson(message));
    }

    @Override
    public SendResult sendCard(BuildContext ctx, MessageIntent intent, LarkPayload payload) {
        String text = intent.getText();
        if (JsonUtils.isValidJson(text)) {
            Card card = JsonUtils.readValue(text, Card.class);
            return sendMessage(signToJson(new LarkCardMessage(card)));
        }
        MessageIntent signed = intent.toBuilder()
                .title(addKeyWord(intent.getTitle(), robotConfig.getKeys()))
                .build();
        LarkCardMessage message = LarkCardMessage.build(signed, payload);
        return sendMessage(signToJson(message));
    }
}
