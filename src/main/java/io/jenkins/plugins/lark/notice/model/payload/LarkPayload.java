package io.jenkins.plugins.lark.notice.model.payload;

import io.jenkins.plugins.lark.notice.sdk.model.lark.support.view.img.ImgElement;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Lark-only payload fields consumed solely by {@code LarkMessageSender}.
 *
 * @author xm.z
 */
@Getter
@Builder
public class LarkPayload implements PlatformPayload {

    /**
     * Image key for a pure image message (Lark {@code image} msg type).
     */
    private final String imageKey;

    /**
     * Chat id for forwarding a shared chat (Lark {@code share_chat} msg type).
     */
    private final String shareChatId;

    /**
     * Rich-text post body (Lark {@code post} msg type), as nested region/segment maps.
     */
    private final List<List<Map<String, String>>> post;

    /**
     * Image element appended at the bottom of a Lark interactive card body.
     */
    private final ImgElement bottomImg;
}
