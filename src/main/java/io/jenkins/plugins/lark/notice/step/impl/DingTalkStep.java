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
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.DingPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import io.jenkins.plugins.lark.notice.step.AbstractStep;
import io.jenkins.plugins.lark.notice.tools.Utils;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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
     * Creates a DingTalk pipeline step with the target robot and message type.
     *
     * @param robot robot identifier or expression
     * @param type  message type
     */
    @DataBoundConstructor
    public DingTalkStep(String robot, MsgTypeEnum type) {
        super(robot, type);
    }

    /**
     * Sets the message title.
     *
     * @param title message title
     */
    @DataBoundSetter
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Sets the message text lines.
     *
     * @param text message text lines
     */
    @DataBoundSetter
    public void setText(List<String> text) {
        this.text = text;
    }

    /**
     * Sets the list of users to @mention.
     *
     * @param ats user identifiers to mention
     */
    @DataBoundSetter
    public void setAts(List<String> ats) {
        this.ats = ats == null ? new HashSet<>() : new HashSet<>(ats);
    }

    /**
     * Sets whether to @mention all users.
     *
     * @param atAll true to mention all users
     */
    @DataBoundSetter
    public void setAtAll(boolean atAll) {
        this.atAll = atAll;
    }

    /**
     * Sets the URL for link-style messages.
     *
     * @param messageUrl message URL
     */
    @DataBoundSetter
    public void setMessageUrl(String messageUrl) {
        this.messageUrl = messageUrl;
    }

    /**
     * Sets the picture URL for link-style messages.
     *
     * @param picUrl picture URL
     */
    @DataBoundSetter
    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    /**
     * Sets the single action button title for DingTalk action cards.
     *
     * @param singleTitle single button title
     */
    @DataBoundSetter
    public void setSingleTitle(String singleTitle) {
        this.singleTitle = singleTitle;
    }

    /**
     * Sets the single action button URL for DingTalk action cards.
     *
     * @param singleUrl single button URL
     */
    @DataBoundSetter
    public void setSingleUrl(String singleUrl) {
        this.singleUrl = singleUrl;
    }

    /**
     * Sets whether action-card buttons should be arranged vertically.
     *
     * @param verticalButton true for vertical layout
     */
    @DataBoundSetter
    public void setVerticalButton(boolean verticalButton) {
        this.verticalButton = verticalButton;
    }

    /**
     * Sets the action buttons for card messages.
     *
     * @param buttons list of button models
     */
    @DataBoundSetter
    public void setButtons(List<ButtonModel> buttons) {
        this.buttons = buttons;
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
            String jobUrl = rootPath + run.getUrl();
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
                .build();
        BuildContext ctx = buildContext(run, listener, noticeOccasion.buildStatus(), locale);

        return service.send(listener, robotId, ctx, intent, payload);
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
