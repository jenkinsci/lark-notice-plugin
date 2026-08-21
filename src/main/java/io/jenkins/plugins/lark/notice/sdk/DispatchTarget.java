package io.jenkins.plugins.lark.notice.sdk;

import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.model.payload.PlatformPayload;

/**
 * Everything {@link MessageDispatcher} needs to know about where a message is going, resolved by
 * the caller rather than looked up mid-dispatch.
 * <p>This matters for the robot configuration test button: that send uses webhook, signing and
 * proxy values straight from the form, which may differ from — or not yet exist in — the saved
 * global configuration. Passing a target keeps the protocol used for the support check and the
 * retry policy consistent with the sender, instead of mixing form values with saved ones.
 *
 * @param robotId     robot identifier, used for logging and diagnostics
 * @param robotName   display name for logging, may be {@code null}
 * @param protocol    protocol family used to validate message-type support, may be {@code null} to skip the check
 * @param retryPolicy retry behaviour for this send
 * @param sender      prepared platform sender
 * @author xm.z
 */
public record DispatchTarget(String robotId,
                             String robotName,
                             RobotProtocolType protocol,
                             RetryPolicy retryPolicy,
                             MessageSender<? extends PlatformPayload> sender) {
}
