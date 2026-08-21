package io.jenkins.plugins.lark.notice.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.plugins.lark.notice.sdk.model.ding.DingFeedCardMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.function.UnaryOperator;

/**
 * One entry of a DingTalk {@code feedCard} message: a title, a jump target and a thumbnail.
 *
 * @author xm.z
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class FeedCardLinkModel implements Describable<FeedCardLinkModel> {

    /**
     * Entry title.
     */
    private String title;

    /**
     * URL opened when the entry is tapped.
     */
    private String messageUrl;

    /**
     * Thumbnail image URL.
     */
    private String picUrl;

    @DataBoundConstructor
    public FeedCardLinkModel(String title, String messageUrl, String picUrl) {
        this.title = title;
        this.messageUrl = messageUrl;
        this.picUrl = picUrl;
    }

    /**
     * Converts this model to a wire-level feed entry, running every field through {@code expander}.
     *
     * @param expander applied to each field, used for environment variable expansion
     * @return feed entry
     */
    public DingFeedCardMessage.FeedCardLink toFeedCardLink(UnaryOperator<String> expander) {
        return new DingFeedCardMessage.FeedCardLink(
                expander.apply(title), expander.apply(messageUrl), expander.apply(picUrl));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<FeedCardLinkModel> {

    }
}
