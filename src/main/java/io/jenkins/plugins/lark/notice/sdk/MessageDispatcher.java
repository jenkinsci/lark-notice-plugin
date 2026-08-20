package io.jenkins.plugins.lark.notice.sdk;

import hudson.model.TaskListener;
import io.jenkins.plugins.lark.notice.Messages;
import io.jenkins.plugins.lark.notice.config.LarkGlobalConfig;
import io.jenkins.plugins.lark.notice.config.LarkRetryConfig;
import io.jenkins.plugins.lark.notice.config.LarkRobotConfig;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.logging.NoticeLog;
import io.jenkins.plugins.lark.notice.logging.NoticeLogKey;
import io.jenkins.plugins.lark.notice.logging.NoticeTrace;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.PlatformPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;

/**
 * Dispatches a layered {@link MessageIntent} + {@link PlatformPayload} to the platform sender
 * resolved for a robot, routing the chosen {@link MsgTypeEnum} to the matching {@code sendX}
 * method. Platforms only implement the types they support; unsupported types yield a failure.
 *
 * @author xm.z
 */
public class MessageDispatcher {

    private static final MessageDispatcher INSTANCE = new MessageDispatcher();

    private final MessageSenderRegistry senderRegistry;

    private MessageDispatcher() {
        this(MessageSenderRegistry.getInstance());
    }

    MessageDispatcher(MessageSenderRegistry senderRegistry) {
        this.senderRegistry = senderRegistry;
    }

    public static MessageDispatcher getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves a sender for the robot and dispatches the message.
     *
     * @param listener task listener
     * @param robotId  target robot id
     * @param ctx      shared build context
     * @param intent   cross-platform rendering intent
     * @param payload  platform-specific payload (must match the robot's protocol)
     * @return send result
     */
    public SendResult send(TaskListener listener, String robotId, BuildContext ctx,
                           MessageIntent intent, PlatformPayload payload) {
        MessageSender<?> sender = senderRegistry.resolve(robotId);
        return send(listener, robotId, ctx, intent, payload, sender);
    }

    /**
     * Dispatches using a pre-resolved sender, bypassing registry resolution.
     *
     * @param listener task listener
     * @param robotId  robot id for logging
     * @param ctx      shared build context
     * @param intent   cross-platform rendering intent
     * @param payload  platform-specific payload
     * @param sender   prepared sender
     * @return send result
     */
    public SendResult send(TaskListener listener, String robotId, BuildContext ctx,
                           MessageIntent intent, PlatformPayload payload, MessageSender<?> sender) {
        if (sender == null) {
            return fail(listener, robotId, null, String.format(Messages.dispatcher_error_robot_not_exist(), robotId));
        }
        if (intent == null) {
            return fail(listener, robotId, null, Messages.dispatcher_error_message_missing());
        }
        MsgTypeEnum type = intent.getType();
        if (type == null) {
            return fail(listener, robotId, null, Messages.dispatcher_error_message_type_missing());
        }

        RobotProtocolType protocol = resolveProtocol(robotId);
        SendResult unsupported = validateSupport(listener, robotId, type, protocol);
        if (unsupported != null) {
            return unsupported;
        }

        if (robotId != null) {
            NoticeLog.verbose(listener, Messages.dispatcher_log_current_robot(), senderRegistry.findRobotName(robotId));
        }

        RetryPolicy retryPolicy = resolveRetryPolicy(robotId);
        int maxAttempts = retryPolicy.getMaxAttempts();
        int attempt = 1;
        SendResult sendResult = null;
        while (true) {
            sendResult = dispatch(sender, type, ctx, intent, payload);
            if (sendResult != null && sendResult.isOk()) {
                break;
            }
            if (!retryPolicy.isEnabled() || attempt >= maxAttempts) {
                break;
            }
            long delayMs = retryPolicy.nextDelayMs(attempt);
            NoticeLog.trace(listener, NoticeTrace.DISPATCHER_SEND_RETRY,
                    NoticeLog.field(NoticeLogKey.ROBOT_ID, robotId),
                    NoticeLog.field(NoticeLogKey.MESSAGE_TYPE, type.name()),
                    NoticeLog.field(NoticeLogKey.ATTEMPT, attempt),
                    NoticeLog.field(NoticeLogKey.MAX_ATTEMPTS, maxAttempts),
                    NoticeLog.field(NoticeLogKey.DELAY_MS, delayMs));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fail(listener, robotId, type, "Retry interrupted");
            }
            attempt++;
        }

        if (sendResult == null) {
            return fail(listener, robotId, type, Messages.dispatcher_error_send_result_missing());
        }

        NoticeLog.verbose(listener, Messages.dispatcher_log_send_details(), sendResult.getRequestBody());
        NoticeLog.trace(listener, NoticeTrace.DISPATCHER_SEND_FINISH,
                NoticeLog.field(NoticeLogKey.ROBOT_ID, robotId),
                NoticeLog.field(NoticeLogKey.MESSAGE_TYPE, type.name()),
                NoticeLog.field(NoticeLogKey.SUCCESS, sendResult.isOk()),
                NoticeLog.field(NoticeLogKey.RESULT_CODE, sendResult.getCode()),
                NoticeLog.field(NoticeLogKey.MESSAGE, NoticeLog.abbreviate(sendResult.getMsg(), 200)),
                NoticeLog.field(NoticeLogKey.REQUEST_SIZE, sendResult.getRequestBody() == null ? 0 : sendResult.getRequestBody().length()),
                NoticeLog.field(NoticeLogKey.ATTEMPT, attempt),
                NoticeLog.field(NoticeLogKey.MAX_ATTEMPTS, maxAttempts));

        return sendResult;
    }

    /**
     * Routes a message type to the matching {@code sendX} method on the concrete sender.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private SendResult dispatch(MessageSender sender, MsgTypeEnum type, BuildContext ctx,
                               MessageIntent intent, PlatformPayload payload) {
        return switch (type) {
            case TEXT -> sender.sendText(ctx, intent, payload);
            case MARKDOWN -> sender.sendMarkdown(ctx, intent, payload);
            case IMAGE -> sender.sendImage(ctx, intent, payload);
            case SHARE_CHAT -> sender.sendShareChat(ctx, intent, payload);
            case POST -> sender.sendPost(ctx, intent, payload);
            case LINK -> sender.sendLink(ctx, intent, payload);
            case CARD -> sender.sendCard(ctx, intent, payload);
        };
    }

    /**
     * Returns a failure result when the type is not supported by the resolved protocol, otherwise null.
     */
    private SendResult validateSupport(TaskListener listener, String robotId, MsgTypeEnum type, RobotProtocolType protocol) {
        if (protocol != null && !protocol.supports(type)) {
            return fail(listener, robotId, type,
                    String.format("Message type %s is not supported by %s robots.", type, protocol));
        }
        return null;
    }

    private RobotProtocolType resolveProtocol(String robotId) {
        if (robotId == null) {
            return null;
        }
        return LarkGlobalConfig.getRobot(robotId)
                .map(LarkRobotConfig::getProtocolType)
                .orElse(null);
    }

    RetryPolicy resolveRetryPolicy(String robotId) {
        if (robotId == null) {
            return RetryPolicy.from(LarkRetryConfig.defaultConfig());
        }
        return LarkGlobalConfig.getRobot(robotId)
                .map(LarkRobotConfig::getRetryConfig)
                .map(RetryPolicy::from)
                .orElseGet(() -> RetryPolicy.from(LarkRetryConfig.defaultConfig()));
    }

    private SendResult fail(TaskListener listener, String robotId, MsgTypeEnum msgType, String message) {
        SendResult failed = SendResult.fail(message);
        NoticeLog.trace(listener, NoticeTrace.DISPATCHER_SEND_FINISH,
                NoticeLog.field(NoticeLogKey.ROBOT_ID, robotId),
                NoticeLog.field(NoticeLogKey.MESSAGE_TYPE, msgType == null ? "<null>" : msgType.name()),
                NoticeLog.field(NoticeLogKey.SUCCESS, false),
                NoticeLog.field(NoticeLogKey.RESULT_CODE, failed.getCode()),
                NoticeLog.field(NoticeLogKey.MESSAGE, NoticeLog.abbreviate(failed.getMsg(), 200)));
        return failed;
    }
}
