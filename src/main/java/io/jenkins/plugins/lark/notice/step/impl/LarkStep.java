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
import io.jenkins.plugins.lark.notice.model.payload.LarkPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import io.jenkins.plugins.lark.notice.step.AbstractStep;
import io.jenkins.plugins.lark.notice.tools.Utils;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.*;

import static io.jenkins.plugins.lark.notice.sdk.constant.Constants.defaultTitle;

/**
 * This class represents a step for sending messages to Lark using a specific robot.
 *
 * @author xm.z
 */
@Getter
// imageKey is a Lark image resource id, not a credential, hence the plaintext-storage suppression.
@SuppressWarnings({"unused", "lgtm[jenkins/plaintext-storage]"})
public class LarkStep extends AbstractStep {

    /**
     * The title of the message, with different meanings in different message types.
     */
    private String title;

    /**
     * The content of the text message, represented as a list of strings.
     */
    private List<String> text;

    /**
     * The chat ID to be shared in a SHARE_CHAT message.
     */
    private String shareChatId;

    /**
     * The image key to be displayed in an IMAGE message.
     */
    private String imageKey;

    /**
     * The data structure to display rich content in a POST message.
     */
    private List<List<Map<String, String>>> post;

    /**
     * The image at the top of the message body - only applicable to card messages.
     */
    private ImgModel topImg;

    /**
     * The image at the bottom of the message body - only applicable to card messages.
     */
    private ImgModel bottomImg;

    /**
     * The list of buttons to be included in the message.
     */
    private List<ButtonModel> buttons;

    /**
     * Users to @mention. Mobile numbers are routed to Lark's at-mobile list, everything else is
     * treated as an open id.
     */
    private Set<String> ats;

    /**
     * Whether to @mention everyone in the chat.
     */
    private boolean atAll;

    /**
     * Creates a Lark pipeline step with the target robot and message type.
     *
     * @param robot robot identifier or expression
     * @param type  message type
     */
    @DataBoundConstructor
    public LarkStep(String robot, MsgTypeEnum type) {
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
     * Sets the shared chat identifier for SHARE_CHAT messages.
     *
     * @param shareChatId share chat identifier
     */
    @DataBoundSetter
    public void setShareChatId(String shareChatId) {
        this.shareChatId = shareChatId;
    }

    /**
     * Sets the image key for IMAGE messages.
     *
     * @param imageKey image key
     */
    @DataBoundSetter
    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    /**
     * Sets the POST message body structure.
     *
     * @param post post body structure
     */
    @DataBoundSetter
    public void setPost(List<List<Map<String, String>>> post) {
        this.post = post;
    }

    /**
     * Sets the top image for card messages.
     *
     * @param topImg top image model
     */
    @DataBoundSetter
    public void setTopImg(ImgModel topImg) {
        this.topImg = topImg;
    }

    /**
     * Sets the bottom image for card messages.
     *
     * @param bottomImg bottom image model
     */
    @DataBoundSetter
    public void setBottomImg(ImgModel bottomImg) {
        this.bottomImg = bottomImg;
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
     * Sets the list of users to @mention.
     *
     * @param ats user identifiers (open id or mobile number)
     */
    @DataBoundSetter
    public void setAts(List<String> ats) {
        this.ats = ats == null ? new HashSet<>() : new HashSet<>(ats);
    }

    /**
     * Sets whether to @mention everyone.
     *
     * @param atAll {@code true} to mention everyone
     */
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
    public SendResult send(Run<?, ?> run, EnvVars envVars, TaskListener listener) {
        NoticeOccasionEnum noticeOccasion = NoticeOccasionEnum.getNoticeOccasion(run.getResult());
        String robotId = envVars.expand(robot);
        Locale locale = resolveLocale(robotId);

        List<Button> resolvedButtons = expandButtons(envVars, buttons);
        if (resolvedButtons == null && MsgTypeEnum.CARD.equals(type)) {
            String jobUrl = jobUrl(run);
            resolvedButtons = Utils.createDefaultButtons(jobUrl, locale);
        }

        MessageIntent intent = MessageIntent.builder().type(type)
                .statusType(noticeOccasion.buildStatus())
                .title(envVars.expand(StringUtils.defaultIfBlank(title, defaultTitle())))
                .text(envVars.expand(Utils.join(text))).buttons(resolvedButtons)
                .topImg(buildImg(envVars, topImg))
                .atAll(atAll).atUserIds(expandAts(envVars, ats))
                .cardFields(resolveCardFields(envVars))
                .build();
        BuildContext ctx = buildContext(run, listener, noticeOccasion.buildStatus(), locale);
        LarkPayload payload = LarkPayload.builder()
                .imageKey(expandNullable(envVars, imageKey))
                .shareChatId(expandNullable(envVars, shareChatId))
                .post(expandPost(envVars))
                .bottomImg(buildImg(envVars, bottomImg))
                .build();

        return dispatcher().send(listener, robotId, ctx, intent, payload);
    }

    /**
     * Expands environment variables inside every segment value of the rich-text body. The whole
     * structure used to be serialised into the shared text field and expanded as one string, so
     * expansion has to happen per value here to keep {@code ${VAR}} working inside post content.
     *
     * @param envVars environment variables
     * @return expanded rich-text structure, or {@code null} when no post body is set
     */
    private List<List<Map<String, String>>> expandPost(EnvVars envVars) {
        if (post == null) {
            return null;
        }
        List<List<Map<String, String>>> expanded = new ArrayList<>(post.size());
        for (List<Map<String, String>> region : post) {
            if (region == null) {
                expanded.add(null);
                continue;
            }
            List<Map<String, String>> segments = new ArrayList<>(region.size());
            for (Map<String, String> segment : region) {
                if (segment == null) {
                    segments.add(null);
                    continue;
                }
                Map<String, String> copy = new LinkedHashMap<>();
                segment.forEach((key, value) -> copy.put(key, expandNullable(envVars, value)));
                segments.add(copy);
            }
            expanded.add(segments);
        }
        return expanded;
    }

    @Extension
    public static class LarkStepDescriptor extends AbstractStepDescriptor {

        @Override
        public String getFunctionName() {
            return "lark";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "lark notice";
        }
    }

}
