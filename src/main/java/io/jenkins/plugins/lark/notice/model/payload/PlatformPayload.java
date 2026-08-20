package io.jenkins.plugins.lark.notice.model.payload;

/**
 * Marker supertype for platform-specific message payload. Each robot family carries its own
 * payload subtype ({@link LarkPayload}, {@link DingPayload}, {@link WeComPayload}) holding fields
 * only that platform consumes. The generic {@code <T extends PlatformPayload>} on message senders
 * pins the payload type at compile time so a sender cannot read a foreign platform's fields.
 *
 * @author xm.z
 */
public interface PlatformPayload {
}
