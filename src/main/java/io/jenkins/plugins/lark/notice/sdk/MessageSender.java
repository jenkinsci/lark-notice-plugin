package io.jenkins.plugins.lark.notice.sdk;

import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.PlatformPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import org.apache.commons.lang3.StringUtils;

/**
 * Sends a typed message payload to a robot platform. The generic {@code <T>} pins the
 * platform-specific payload type a sender may consume, so a sender cannot read fields belonging
 * to another platform. {@link #sendText} and {@link #sendMarkdown} are abstract because every
 * supported platform implements them; the remaining {@code sendX} methods default to an
 * "unsupported" failure and platforms override only the ones they support.
 *
 * @param <T> platform-specific payload type
 * @author xm.z
 */
public interface MessageSender<T extends PlatformPayload> {

    /**
     * Sends a text message.
     *
     * @param ctx    shared build context
     * @param intent cross-platform rendering intent
     * @param payload platform-specific payload
     * @return result of the send operation
     */
    SendResult sendText(BuildContext ctx, MessageIntent intent, T payload);

    /**
     * Sends a markdown message.
     *
     * @param ctx    shared build context
     * @param intent cross-platform rendering intent
     * @param payload platform-specific payload
     * @return result of the send operation
     */
    SendResult sendMarkdown(BuildContext ctx, MessageIntent intent, T payload);

    /**
     * Sends an image message. Unsupported unless overridden.
     *
     * @param ctx    shared build context
     * @param intent cross-platform rendering intent
     * @param payload platform-specific payload
     * @return failure result by default
     */
    default SendResult sendImage(BuildContext ctx, MessageIntent intent, T payload) {
        return SendResult.fail("This type of message is not supported.");
    }

    /**
     * Sends a shared-chat message. Unsupported unless overridden.
     *
     * @param ctx    shared build context
     * @param intent cross-platform rendering intent
     * @param payload platform-specific payload
     * @return failure result by default
     */
    default SendResult sendShareChat(BuildContext ctx, MessageIntent intent, T payload) {
        return SendResult.fail("This type of message is not supported.");
    }

    /**
     * Sends a link message. Unsupported unless overridden.
     *
     * @param ctx    shared build context
     * @param intent cross-platform rendering intent
     * @param payload platform-specific payload
     * @return failure result by default
     */
    default SendResult sendLink(BuildContext ctx, MessageIntent intent, T payload) {
        return SendResult.fail("This type of message is not supported.");
    }

    /**
     * Sends a rich-text post message. Unsupported unless overridden.
     *
     * @param ctx    shared build context
     * @param intent cross-platform rendering intent
     * @param payload platform-specific payload
     * @return failure result by default
     */
    default SendResult sendPost(BuildContext ctx, MessageIntent intent, T payload) {
        return SendResult.fail("This type of message is not supported.");
    }

    /**
     * Sends an interactive card message. Unsupported unless overridden.
     *
     * @param ctx    shared build context
     * @param intent cross-platform rendering intent
     * @param payload platform-specific payload
     * @return failure result by default
     */
    default SendResult sendCard(BuildContext ctx, MessageIntent intent, T payload) {
        return SendResult.fail("This type of message is not supported.");
    }

    /**
     * Appends robot keywords to message content.
     *
     * @param str  original content
     * @param keys keywords to append
     * @return content with appended keywords
     */
    default String addKeyWord(String str, String keys) {
        if (StringUtils.isEmpty(keys)) {
            return str;
        }
        return str + " " + keys;
    }
}
