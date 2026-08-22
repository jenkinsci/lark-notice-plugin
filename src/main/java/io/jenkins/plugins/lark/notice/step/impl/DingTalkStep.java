package io.jenkins.plugins.lark.notice.step.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.enums.NoticeOccasionEnum;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.ButtonModel;
import io.jenkins.plugins.lark.notice.model.FeedCardLinkModel;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.DingPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.ding.DingFeedCardMessage;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import io.jenkins.plugins.lark.notice.step.AbstractStep;
import io.jenkins.plugins.lark.notice.tools.Utils;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static io.jenkins.plugins.lark.notice.sdk.constant.Constants.defaultTitle;

/**
 * This class represents a step for sending notifications to DingTalk in Jenkins.
 *
 * @author xm.z
 */
@Getter
@SuppressWarnings("unused")
public class DingTalkStep extends AbstractStep {

    /**
     * The title of the message, with different meanings in different message types.
     */
    private String title;

    /**
     * The content of the text message, represented as a list of strings.
     */
    private List<String> text;

    /**
     * URL to redirect to when clicking on a single message.
     */
    private String messageUrl;

    /**
     * URL of the image to be displayed after a single message.
     */
    private String picUrl;

    /**
     * Title of a single button. If set along with singleURL, buttons will be invalid.
     */
    private String singleTitle;

    /**
     * URL to redirect when clicking on the message.
     */
    private String singleUrl;

    /**
     * List of users to tag using '@'.
     */
    private Set<String> ats;

    /**
     * Whether to tag everyone.
     */
    private boolean atAll;

    /**
     * Whether to display buttons vertically. Default is horizontally.
     */
    private boolean verticalButton;

    /**
     * The list of buttons to be included in the message.
     */
    private List<ButtonModel> buttons;

    /**
     * Whether to hide the sender avatar on action cards.
     */
    private boolean hideAvatar;

    /**
     * Entries of a FEED_CARD message.
     */
    private List<FeedCardLinkModel> feedCardLinks;

    /**
     * Creates a DingTalk pipeline step with the target robot and message type.
     *
     * @param robot robot identifier or expression
     * @param type  message type
     */
    @DataBoundConstructor
    public DingTalkStep(String robot, MsgTypeEnum type) {
        super(robot, type);
    }

    @DataBoundSetter
    public void setTitle(String title) {
        this.title = title;
    }

    @DataBoundSetter
    public void setText(List<String> text) {
        this.text = text;
    }

    /**
     * Accepts the raw list from the pipeline script; duplicates collapse and {@code null} becomes an
     * empty set, so callers never have to guard.
     */
    @DataBoundSetter
    public void setAts(List<String> ats) {
        this.ats = ats == null ? new HashSet<>() : new HashSet<>(ats);
    }

    @DataBoundSetter
    public void setAtAll(boolean atAll) {
        this.atAll = atAll;
    }

    @DataBoundSetter
    public void setMessageUrl(String messageUrl) {
        this.messageUrl = messageUrl;
    }

    @DataBoundSetter
    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    @DataBoundSetter
    public void setSingleTitle(String singleTitle) {
        this.singleTitle = singleTitle;
    }

    @DataBoundSetter
    public void setSingleUrl(String singleUrl) {
        this.singleUrl = singleUrl;
    }

    @DataBoundSetter
    public void setVerticalButton(boolean verticalButton) {
        this.verticalButton = verticalButton;
    }

    @DataBoundSetter
    public void setButtons(List<ButtonModel> buttons) {
        this.buttons = buttons;
    }

    @DataBoundSetter
    public void setHideAvatar(boolean hideAvatar) {
        this.hideAvatar = hideAvatar;
    }

    @DataBoundSetter
    public void setFeedCardLinks(List<FeedCardLinkModel> feedCardLinks) {
        this.feedCardLinks = feedCardLinks;
    }

    /**
     * Sends the message to the specified run, environment variables, and task listener.
     *
     * @param run      The run to send the message to.
     * @param envVars  The environment variables.
     * @param listener The task listener.
     * @return The SendResult indicating the success or failure of the message sending.
     */
    @Override
    protected SendResult send(Run<?, ?> run, EnvVars envVars, TaskListener listener) {
        NoticeOccasionEnum noticeOccasion = NoticeOccasionEnum.getNoticeOccasion(run.getResult());
        String robotId = envVars.expand(robot);
        Locale locale = resolveLocale(robotId);

        List<Button> resolvedButtons = expandButtons(envVars, buttons);
        // Default buttons only make sense on the btns branch; when singleTitle is set the sender
        // renders a single jump action and ignores the button list entirely.
        if (resolvedButtons == null && MsgTypeEnum.CARD.equals(type) && StringUtils.isBlank(singleTitle)) {
            String jobUrl = jobUrl(run);
            resolvedButtons = Utils.createDefaultButtons(jobUrl, locale);
        }

        MessageIntent intent = MessageIntent.builder().type(type)
                .statusType(noticeOccasion.buildStatus())
                .title(envVars.expand(StringUtils.defaultIfBlank(title, defaultTitle())))
                .text(envVars.expand(Utils.join(text))).buttons(resolvedButtons)
                .messageUrl(expandNullable(envVars, messageUrl))
                .picUrl(expandNullable(envVars, picUrl))
                .atAll(atAll).atUserIds(expandAts(envVars, ats))
                .cardFields(resolveCardFields(envVars))
                .build();
        DingPayload payload = DingPayload.builder()
                .singleTitle(expandNullable(envVars, singleTitle))
                .singleUrl(expandNullable(envVars, singleUrl))
                .btnOrientation(isVerticalButton() ? "0" : "1")
                .hideAvatar(hideAvatar ? "1" : "0")
                .feedCardLinks(resolveFeedCardLinks(envVars))
                .build();
        BuildContext ctx = buildContext(run, listener, noticeOccasion.buildStatus(), locale);

        return dispatcher().send(listener, robotId, ctx, intent, payload);
    }

    /**
     * Expands and converts the configured feed entries.
     *
     * @param envVars environment variables
     * @return expanded entries, or {@code null} when none are configured
     */
    private List<DingFeedCardMessage.FeedCardLink> resolveFeedCardLinks(EnvVars envVars) {
        if (CollectionUtils.isEmpty(feedCardLinks)) {
            return null;
        }
        return feedCardLinks.stream()
                .map(model -> model.toFeedCardLink(value -> expandNullable(envVars, value)))
                .toList();
    }

    @Extension
    public static class DingTalkStepDescriptor extends AbstractStepDescriptor {

        @Override
        public String getFunctionName() {
            return "dingTalk";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "DingTalk Notice";
        }
    }

}
