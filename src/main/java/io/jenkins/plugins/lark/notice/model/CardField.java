package io.jenkins.plugins.lark.notice.model;

import lombok.*;
import org.apache.commons.lang3.StringUtils;

/**
 * One user-defined row of a card's information block, replacing the built-in build rows.
 * <p>Rows default to plain text ({@link #TEXT_TYPE}); set {@link #url} to make the row link
 * somewhere ({@link #LINK_TYPE}). WeCom renders these as {@code horizontal_content_list} entries;
 * Lark and DingTalk cards are Markdown based, so there they render as body lines via
 * {@link #toMarkdownLine()}.
 *
 * @author xm.z
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardField {

    /**
     * Horizontal content type: plain text row.
     */
    public static final int TEXT_TYPE = 0;

    /**
     * Horizontal content type: jump row that opens {@link #url}.
     */
    public static final int LINK_TYPE = 1;

    /**
     * Row label, e.g. "任务名称".
     */
    private String keyname;

    /**
     * Row value.
     */
    private String value;

    /**
     * Jump URL. When blank the row is rendered as a plain text row.
     */
    private String url;

    /**
     * Resolves the content type from whether {@link #url} is set.
     *
     * @return {@link #TEXT_TYPE} or {@link #LINK_TYPE}
     */
    public int resolveType() {
        return (url == null || url.isBlank()) ? TEXT_TYPE : LINK_TYPE;
    }

    /**
     * Renders this row as one Markdown line, for platforms whose cards carry a Markdown body
     * instead of a structured row list.
     *
     * @return {@code **label**: value} with the value linked when a url is set
     */
    public String toMarkdownLine() {
        String body = resolveType() == LINK_TYPE
                ? String.format("[%s](%s)", StringUtils.defaultString(value), url)
                : StringUtils.defaultString(value);
        return StringUtils.isBlank(keyname) ? body : String.format("**%s**: %s", keyname, body);
    }
}
