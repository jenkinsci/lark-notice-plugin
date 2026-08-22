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
import io.jenkins.plugins.lark.notice.model.ImgModel;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.WeComPayload;
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
 * Pipeline step for sending WeCom group robot messages.
 *
 * @author xm.z
 */
@Getter
@SuppressWarnings("unused")
public class WechatWorkStep extends AbstractStep {

    /**
     * Message title.
     */
    private String title;

    /**
     * Message content lines.
     */
    private List<String> text;

    /**
     * URL opened from link-style messages or card body clicks.
     */
    private String messageUrl;

    /**
     * Image URL used by WeCom template cards.
     */
    private String picUrl;

    /**
     * Image model used by WeCom template cards.
     */
    private ImgModel topImg;

    /**
     * Action buttons used by WeCom template cards.
     */
    private List<ButtonModel> buttons;

    /**
     * Card header source description, the WeCom {@code source.desc} field. Defaults to a built-in
     * label when blank.
     */
    private String sourceDesc;

    /**
     * Title of the card's quote block, the WeCom {@code quote_area.title} field.
     */
    private String quoteTitle;

    /**
     * Body of the card's quote block, the WeCom {@code quote_area.quote_text} field.
     */
    private String quoteText;

    /**
     * Jump target of the card's quote block, the WeCom {@code quote_area.url} field.
     */
    private String quoteUrl;

    /**
     * Users to mention in text and markdown messages.
     */
    private Set<String> ats;

    /**
     * Whether to mention everyone.
     */
    private boolean atAll;

    /**
     * Creates a WeCom pipeline step with the target robot and message type.
     *
     * @param robot robot identifier or expression
     * @param type  message type
     */
    @DataBoundConstructor
    public WechatWorkStep(String robot, MsgTypeEnum type) {
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

    @DataBoundSetter
    public void setMessageUrl(String messageUrl) {
        this.messageUrl = messageUrl;
    }

    @DataBoundSetter
    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    @DataBoundSetter
    public void setTopImg(ImgModel topImg) {
        this.topImg = topImg;
    }

    @DataBoundSetter
    public void setButtons(List<ButtonModel> buttons) {
        this.buttons = buttons;
    }

    @DataBoundSetter
    public void setSourceDesc(String sourceDesc) {
        this.sourceDesc = sourceDesc;
    }

    @DataBoundSetter
    public void setQuoteTitle(String quoteTitle) {
        this.quoteTitle = quoteTitle;
    }

    @DataBoundSetter
    public void setQuoteText(String quoteText) {
        this.quoteText = quoteText;
    }

    @DataBoundSetter
    public void setQuoteUrl(String quoteUrl) {
        this.quoteUrl = quoteUrl;
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
        String robotId = envVars.expand(robot);
        MessageBundle bundle = buildMessage(run, envVars, listener, robotId);
        return dispatcher().send(listener, robotId, bundle.ctx, bundle.intent, bundle.payload);
    }

    /**
     * Assembles the shared context, cross-platform intent and WeCom payload for a run.
     *
     * @param run      build run
     * @param envVars  environment variables
     * @param listener task listener
     * @param robotId  resolved robot id
     * @return assembled message bundle
     */
    MessageBundle buildMessage(Run<?, ?> run, EnvVars envVars, TaskListener listener, String robotId) {
        NoticeOccasionEnum noticeOccasion = NoticeOccasionEnum.getNoticeOccasion(run.getResult());
        Locale locale = resolveLocale(robotId);
        BuildContext ctx = buildContext(run, listener, noticeOccasion.buildStatus(), locale);
        String expandedText = envVars.expand(Utils.join(text));
        String jobUrl = jobUrl(run);
        List<Button> resolvedButtons = expandButtons(envVars, buttons);
        if (resolvedButtons == null && MsgTypeEnum.CARD.equals(type)) {
            resolvedButtons = Utils.createDefaultButtons(jobUrl, locale);
        }
        MessageIntent intent = MessageIntent.builder()
                .type(type)
                .statusType(noticeOccasion.buildStatus())
                .title(envVars.expand(StringUtils.defaultIfBlank(title, defaultTitle())))
                .text(expandedText)
                .messageUrl(expandNullable(envVars, messageUrl))
                .picUrl(expandNullable(envVars, picUrl))
                .topImg(buildImg(envVars, topImg))
                .buttons(resolvedButtons)
                .atAll(atAll)
                .atUserIds(expandAts(envVars, ats))
                .cardFields(resolveCardFields(envVars))
                .build();
        WeComPayload payload = WeComPayload.builder()
                .additionalContent(expandedText)
                .sourceDesc(expandNullable(envVars, sourceDesc))
                .quoteTitle(expandNullable(envVars, quoteTitle))
                .quoteText(expandNullable(envVars, quoteText))
                .quoteUrl(expandNullable(envVars, quoteUrl))
                .build();
        return new MessageBundle(ctx, intent, payload);
    }

    /**
     * Assembled message parts produced by {@link #buildMessage}.
     */
    record MessageBundle(BuildContext ctx, MessageIntent intent, WeComPayload payload) {
    }

    @Extension
    public static class WechatWorkStepDescriptor extends AbstractStepDescriptor {

        @Override
        public String getFunctionName() {
            return "wechatWork";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "WeCom Notice";
        }
    }
}
