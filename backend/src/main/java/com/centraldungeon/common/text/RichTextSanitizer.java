package com.centraldungeon.common.text;

import org.jspecify.annotations.Nullable;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * The one gate every piece of user-written rich text passes through, on the way in and on the way
 * out (#62).
 *
 * <p>Rich text is the system's most direct XSS surface: a table's description, its house rules and
 * its requirements are written by one person and rendered in everybody else's browser. Sanitizing
 * only on write would leave whatever was stored before this class existed - or by any path that
 * ever bypasses it - to reach the page intact, so #62 asks for both ends and both ends is what this
 * gives.
 *
 * <p>The policy is an <b>allowlist</b>: anything not named below is dropped, which is what makes a
 * tag nobody thought of safe by default instead of dangerous by default. It covers exactly what the
 * editor can produce - headings, emphasis, lists, quotes, links - and nothing else. No {@code img},
 * no {@code style}, no {@code class}: an image tag is a request to an arbitrary host, and a style
 * attribute is a second language with its own escapes.
 */
@Component
public class RichTextSanitizer {

    /**
     * Built once and shared: a {@link PolicyFactory} is immutable and thread-safe, and rebuilding it
     * per call would put a parser construction on every read of every table.
     */
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "em", "u", "s", "code", "pre", "blockquote", "h1", "h2", "h3", "ul", "ol", "li", "a")
            // Only http(s) links, and every one of them leaves the tab it was clicked from with no
            // handle back into it: rel=noopener is what stops the opened page from touching window.opener.
            .allowUrlProtocols("http", "https")
            .allowAttributes("href").onElements("a")
            .requireRelNofollowOnLinks()
            .allowStandardUrlProtocols()
            .toFactory();

    /**
     * Sanitizes one field of user-written rich text.
     *
     * <p>Null in, null out: a table with no description is not the same thing as a table with an
     * empty one, and collapsing the two here would turn every absent field into a stored empty
     * string. Text that sanitizes down to nothing - a lone {@code <script>}, say - also comes back
     * null, because what is left is not content.
     *
     * @param html the rich text as it arrived from the editor, or null when the field is absent
     * @return the same text with everything outside the allowlist removed, or null when there is
     *         nothing left worth storing
     */
    public @Nullable String sanitize(@Nullable String html) {
        if (html == null) {
            return null;
        }
        String sanitized = POLICY.sanitize(html).strip();
        return sanitized.isEmpty() ? null : sanitized;
    }
}
