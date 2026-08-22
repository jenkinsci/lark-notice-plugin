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
import io.jenkins.plugins.lark.notice.model.payload.DingPayload;
import io.jenkins.plugins.lark.notice.model.payload.LarkPayload;
import io.jenkins.plugins.lark.notice.model.payload.PlatformPayload;
import io.jenkins.plugins.lark.notice.model.payload.WeComPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Dispatches a layered {@link MessageIntent} + {@link PlatformPayload} to the platform sender
 * resolved for a robot, routing the chosen {@link MsgTypeEnum} to the matching {@code sendX}
 * method. Platforms only implement the types they support; unsupported types yield a failure.
 *
 * @author xm.z
 */
public class MessageDispatcher {

    private static final Map<Class<? extends PlatformPayload>, RobotProtocolType> PAYLOAD_PROTOCOLS = Map.of(
            LarkPayload.class, RobotProtocolType.LARK_COMPATIBLE,
            DingPayload.class, RobotProtocolType.DING_TALK,
            WeComPayload.class, RobotProtocolType.WECHAT_WORK);

    private static final Map<RobotProtocolType, String> STEP_NAMES = Map.of(
            RobotProtocolType.LARK_COMPATIBLE, "lark",
            RobotProtocolType.DING_TALK, "dingTalk",
            RobotProtocolType.WECHAT_WORK, "wechatWork");

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
     * Resolves the target for the robot from saved configuration and dispatches the message.
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
        return send(listener, ctx, intent, payload, resolveTarget(robotId));
    }

    /**
     * Dispatches to an explicitly resolved target. Callers working from unsaved configuration —
     * the robot config test button — build the target themselves so the protocol and retry policy
     * match the sender rather than whatever is currently persisted.
     *
     * @param listener task listener
     * @param ctx      shared build context
     * @param intent   cross-platform rendering intent
     * @param payload  platform-specific payload
     * @param target   resolved destination
     * @return send result
     */
    public SendResult send(TaskListener listener, BuildContext ctx, MessageIntent intent,
                           PlatformPayload payload, DispatchTarget target) {
        String robotId = target == null ? null : target.robotId();
        MessageSender<? extends PlatformPayload> sender = target == null ? null : target.sender();
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

        SendResult unsupported = validateSupport(listener, robotId, type, target.protocol());
        if (unsupported != null) {
            return unsupported;
        }
        SendResult mismatched = validatePayload(listener, robotId, type, sender, payload);
        if (mismatched != null) {
            return mismatched;
        }

        if (StringUtils.isNotBlank(target.robotName())) {
            NoticeLog.verbose(listener, Messages.dispatcher_log_current_robot(), target.robotName());
        }

        RetryPolicy retryPolicy = target.retryPolicy() == null
                ? RetryPolicy.from(LarkRetryConfig.defaultConfig())
                : target.retryPolicy();
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
                return fail(listener, robotId, type, Messages.dispatcher_error_retry_interrupted());
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
    private <T extends PlatformPayload> SendResult dispatch(MessageSender<T> sender, MsgTypeEnum type,
                                                            BuildContext ctx, MessageIntent intent,
                                                            PlatformPayload payload) {
        T typed = payload == null ? null : sender.payloadType().cast(payload);
        return switch (type) {
            case TEXT -> sender.sendText(ctx, intent, typed);
            case MARKDOWN -> sender.sendMarkdown(ctx, intent, typed);
            case IMAGE -> sender.sendImage(ctx, intent, typed);
            case SHARE_CHAT -> sender.sendShareChat(ctx, intent, typed);
            case POST -> sender.sendPost(ctx, intent, typed);
            case LINK -> sender.sendLink(ctx, intent, typed);
            case CARD -> sender.sendCard(ctx, intent, typed);
            case FEED_CARD -> sender.sendFeedCard(ctx, intent, typed);
        };
    }

    /**
     * Returns a failure result when the payload does not match what the resolved sender consumes,
     * otherwise null. This happens when a platform-specific step is pointed at a robot of another
     * protocol, e.g. {@code wechatWork robot: '<a Lark robot>'}.
     */
    private SendResult validatePayload(TaskListener listener, String robotId, MsgTypeEnum type,
                                       MessageSender<?> sender, PlatformPayload payload) {
        if (payload == null || sender.payloadType().isInstance(payload)) {
            return null;
        }
        RobotProtocolType senderProtocol = PAYLOAD_PROTOCOLS.get(sender.payloadType());
        String hint = senderProtocol == null ? "" : String.format(
                Messages.dispatcher_error_payload_mismatch_hint(),
                robotId, senderProtocol, STEP_NAMES.get(senderProtocol));
        return fail(listener, robotId, type, String.format(Messages.dispatcher_error_payload_mismatch(),
                payload.getClass().getSimpleName(), robotId, sender.payloadType().getSimpleName()) + hint);
    }

    /**
     * Returns a failure result when the type is not supported by the resolved protocol, otherwise null.
     */
    private SendResult validateSupport(TaskListener listener, String robotId, MsgTypeEnum type, RobotProtocolType protocol) {
        if (protocol != null && !protocol.supports(type)) {
            return fail(listener, robotId, type,
                    String.format(Messages.dispatcher_error_type_unsupported(), type, protocol));
        }
        return null;
    }

    /**
     * Builds a dispatch target from the saved global configuration for a robot id.
     *
     * @param robotId robot id
     * @return resolved target; its sender is {@code null} when no such robot exists
     */
    DispatchTarget resolveTarget(String robotId) {
        MessageSender<? extends PlatformPayload> sender = senderRegistry.resolve(robotId);
        return new DispatchTarget(robotId,
                senderRegistry.findRobotName(robotId).orElse(null),
                resolveProtocol(robotId),
                resolveRetryPolicy(robotId),
                sender);
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
