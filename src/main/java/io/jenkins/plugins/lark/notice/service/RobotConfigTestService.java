package io.jenkins.plugins.lark.notice.service;

import hudson.model.User;
import io.jenkins.plugins.lark.notice.Messages;
import io.jenkins.plugins.lark.notice.config.*;
import io.jenkins.plugins.lark.notice.enums.*;
import io.jenkins.plugins.lark.notice.i18n.NoticeI18n;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.BuildJobModel;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.RobotConfigModel;
import io.jenkins.plugins.lark.notice.model.payload.PlatformPayload;
import io.jenkins.plugins.lark.notice.sdk.DispatchTarget;
import io.jenkins.plugins.lark.notice.sdk.MessageDispatcher;
import io.jenkins.plugins.lark.notice.sdk.MessageSender;
import io.jenkins.plugins.lark.notice.sdk.RetryPolicy;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.tools.ApiResponse;
import io.jenkins.plugins.lark.notice.tools.JsonUtils;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.net.ProxySelector;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static io.jenkins.plugins.lark.notice.sdk.constant.Constants.NOTICE_ICON;

/**
 * Service for validating robot configurations and sending test messages.
 */
public final class RobotConfigTestService {

    private RobotConfigTestService() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Sends a test message using values taken straight from the robot configuration form, without
     * consulting — or requiring — the saved configuration. This is what lets the test button work
     * while a robot is still being created.
     *
     * @param id                    robot id, used for logging only
     * @param name                  robot display name, used for logging only
     * @param protocolType          {@link io.jenkins.plugins.lark.notice.enums.RobotProtocolType}
     *                              name, or blank to infer it from the webhook
     * @param endpointMode          {@link io.jenkins.plugins.lark.notice.enums.WebhookEndpointMode}
     *                              name, deciding whether the webhook is given whole or assembled
     * @param webhook               full webhook URL, used in full-webhook mode
     * @param baseUrl               webhook base URL, used when the webhook is assembled
     * @param webhookToken          webhook credential, used when the webhook is assembled
     * @param proxy                 serialised {@link io.jenkins.plugins.lark.notice.config.LarkProxyConfig}
     * @param securityConfigs       serialised list of security policies (keyword, signature, no-SSL)
     * @param messageLocaleStrategy {@link io.jenkins.plugins.lark.notice.enums.MessageLocaleStrategy}
     *                              name, deciding the language of the test message
     * @return outcome carrying a localised message for the form to display
     */
    public static ApiResponse testRobotConfig(String id, String name,
                                              String protocolType, String endpointMode,
                                              String webhook, String baseUrl, String webhookToken,
                                              String proxy, String securityConfigs,
                                              String messageLocaleStrategy) {
        try {
            List<LarkSecurityPolicyConfig> securityPolicyConfigs = parseSecurityPolicyConfigs(securityConfigs);
            RobotWebhookResolver.ResolvedWebhook resolvedWebhook = RobotWebhookResolver.resolveSettings(
                    RobotProtocolType.fromValue(protocolType),
                    WebhookEndpointMode.fromValue(endpointMode),
                    webhook,
                    baseUrl,
                    webhookToken);
            RobotProtocolType resolvedProtocolType = resolvedWebhook.protocolType();
            WebhookEndpointMode resolvedEndpointMode = resolvedWebhook.endpointMode();
            String resolvedWebhookUrl = resolvedWebhook.webhook();
            LarkRobotConfig robotConfig = new LarkRobotConfig(id, name, resolvedWebhookUrl, securityPolicyConfigs);
            robotConfig.setProtocolType(resolvedProtocolType);
            robotConfig.setEndpointMode(resolvedEndpointMode);
            robotConfig.setBaseUrl(baseUrl);
            robotConfig.setWebhookToken(webhookToken);
            robotConfig.setMessageLocaleStrategy(MessageLocaleStrategy.parse(messageLocaleStrategy));
            ProxySelector proxySelector = parseProxySelector(proxy);

            Optional<RobotType> robotTypeOpt = robotConfig.obtainRobotType();
            if (robotTypeOpt.isEmpty()) {
                return ApiResponse.fail(Messages.form_validation_webhook_invalid());
            }
            RobotType robotType = robotTypeOpt.get();

            MessageSender<? extends PlatformPayload> sender =
                    robotType.obtainInstance(RobotConfigModel.of(robotConfig, proxySelector));
            Locale testLocale = MessageLocaleResolver.resolve(robotConfig);
            BuildJobModel buildJobModel = buildTestJobModel(testLocale);
            RobotProtocolType protocol = RobotProtocolType.fromRobotType(robotType);
            MessageIntent intent = buildJobModel.buildCardIntent(testLocale).toBuilder()
                    .text(buildJobModel.toMarkdown(robotType, testLocale)).atAll(false).build();
            BuildContext buildCtx = buildJobModel.buildContext(testLocale);
            PlatformPayload payload = buildJobModel.cardPayload(protocol);
            // Everything here comes from the submitted form, which may not match — or may not yet
            // exist in — the saved configuration, so the target is built from the same values as
            // the sender instead of letting the dispatcher look up the persisted robot. The test
            // send also deliberately uses the default (retry-disabled) policy: the form carries no
            // retry settings, and a config check should report the first failure immediately.
            DispatchTarget target = new DispatchTarget(robotConfig.getId(), robotConfig.getName(),
                    protocol, RetryPolicy.from(LarkRetryConfig.defaultConfig()), sender);
            SendResult sendResult = Objects.requireNonNull(MessageDispatcher.getInstance()
                    .send(null, buildCtx, intent, payload, target), "sendResult");
            boolean ok = sendResult.isOk();
            String detail = sendResult.getMsg();
            String message = ok
                    ? Messages.form_validation_test_success()
                    : StringUtils.isNotBlank(detail)
                    ? Messages.form_validation_test_failure_with_detail(detail)
                    : Messages.form_validation_test_failure();
            return ok ? ApiResponse.ok(message) : ApiResponse.fail(message);
        } catch (Exception e) {
            String detail = StringUtils.defaultIfBlank(e.getMessage(), null);
            String message = StringUtils.isNotBlank(detail)
                    ? Messages.form_validation_test_failure_with_detail(detail)
                    : Messages.form_validation_test_failure();
            return ApiResponse.fail(message);
        }
    }

    private static List<LarkSecurityPolicyConfig> parseSecurityPolicyConfigs(String securityConfigs) {
        return JsonUtils.readList(securityConfigs, LarkSecurityPolicyConfig.class)
                .stream()
                .filter(config -> StringUtils.isNotBlank(config.getValue()))
                .toList();
    }

    private static ProxySelector parseProxySelector(String proxy) {
        return Optional.ofNullable(JsonUtils.readValue(proxy, LarkProxyConfig.class))
                .orElseGet(LarkProxyConfig::new)
                .obtainProxySelector();
    }

    private static BuildJobModel buildTestJobModel(Locale locale) {
        String rootUrl = Jenkins.get().getRootUrl();
        String configureUrl = Strings.CS.appendIfMissing(rootUrl, "/") + "configure";
        User user = Optional.ofNullable(User.current()).orElse(User.getUnknown());

        return BuildJobModel.builder()
                .projectName(NoticeI18n.robotTestProjectName(locale))
                .title(NOTICE_ICON + " " + NoticeI18n.robotTestSuccessTitle(locale))
                .projectUrl(rootUrl).jobName(NoticeI18n.robotTestJobName(locale)).jobUrl(configureUrl)
                .statusType(BuildStatusEnum.SUCCESS).duration("-")
                .executorName(user.getDisplayName()).build();
    }
}
