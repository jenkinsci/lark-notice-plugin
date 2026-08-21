package io.jenkins.plugins.lark.notice.model;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.plugins.lark.notice.model.payload.CardField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.function.UnaryOperator;

/**
 * Configurable card content row for WeCom template cards. Maps to a {@link CardField} entry in
 * the rendered {@code horizontal_content_list}. When {@link #url} is set the row becomes a jump
 * row; otherwise it renders as plain text.
 *
 * @author xm.z
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CardFieldModel implements Describable<CardFieldModel> {

    /** Row label, e.g. "任务名称". */
    private String keyname;

    /** Row value. */
    private String value;

    /** Jump URL. When blank the row is rendered as a plain text row. */
    private String url;

    @DataBoundConstructor
    public CardFieldModel(String keyname, String value, String url) {
        this.keyname = keyname;
        this.value = value;
        this.url = url;
    }

    /**
     * Converts this model to a payload {@link CardField}, running every field through
     * {@code expander} first (used to apply environment variable expansion).
     *
     * @param expander applied to {@link #keyname}, {@link #value} and {@link #url}
     * @return card field
     */
    public CardField toCardField(UnaryOperator<String> expander) {
        return CardField.builder()
                .keyname(expander.apply(keyname))
                .value(expander.apply(value))
                .url(expander.apply(url))
                .build();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CardFieldModel> {

    }
}
