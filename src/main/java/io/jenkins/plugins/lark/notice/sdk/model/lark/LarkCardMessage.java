package io.jenkins.plugins.lark.notice.sdk.model.lark;

import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.LarkPayload;
import io.jenkins.plugins.lark.notice.sdk.model.lark.builder.LarkCardBuilder;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Card;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 卡片消息 类型
 *
 * @author xm.z
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LarkCardMessage extends BaseLarkMessage {

    private Card card;

    public LarkCardMessage(Card card) {
        this.card = card;
        setMsgType("interactive");
    }

    /**
     * Builds a default Lark interactive card from the layered intent + payload.
     *
     * @param intent  cross-platform rendering intent
     * @param payload Lark-specific payload (bottom image)
     * @return assembled interactive card message
     */
    public static LarkCardMessage build(MessageIntent intent, LarkPayload payload) {
        String markdownContent = addAtInfo(intent.getText(), intent.getAt());
        LarkCardBuilder builder = new LarkCardBuilder()
                .withHeader(intent.obtainHeaderTemplate(), intent.getTitle())
                .withImage(intent.getTopImg())
                .withMarkdown(markdownContent)
                .withPersonList(intent.getAt())
                .withButtons(intent.getButtons());
        if (payload != null && payload.getBottomImg() != null) {
            builder.withImage(payload.getBottomImg());
        }
        return new LarkCardMessage(builder.build());
    }
}
