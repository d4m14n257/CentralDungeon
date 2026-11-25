package com.centraldungeon.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The XSS gate. Every case here is something a table's description could actually carry, and
 * every one of them is rendered in somebody else's browser.
 */
class RichTextSanitizerTest {

    private final RichTextSanitizer sanitizer = new RichTextSanitizer();

    @Test
    void keepsTheFormattingTheEditorProduces() {
        String html = "<p>Una mesa <strong>larga</strong> con <em>mucho</em> rol.</p><ul><li>Uno</li></ul>";

        assertThat(sanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    void dropsScriptTagsAndTheirContents() {
        assertThat(sanitizer.sanitize("<p>Hola</p><script>alert('xss')</script>")).isEqualTo("<p>Hola</p>");
    }

    @Test
    void dropsEventHandlerAttributes() {
        String sanitized = sanitizer.sanitize("<p onclick=\"steal()\">Hola</p>");

        assertThat(sanitized).isEqualTo("<p>Hola</p>");
    }

    /** A style attribute is a second language with its own escapes, so it is not on the allowlist. */
    @Test
    void dropsStyleAttributes() {
        assertThat(sanitizer.sanitize("<p style=\"position:fixed\">Hola</p>")).isEqualTo("<p>Hola</p>");
    }

    @Test
    void dropsJavascriptUrlsButKeepsRealLinks() {
        assertThat(sanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>")).isEqualTo("click");
        assertThat(sanitizer.sanitize("<a href=\"https://example.org\">docs</a>")).contains("https://example.org");
    }

    /** Only http(s) survives: a mailto link is an address harvested off a public page. */
    @Test
    void dropsMailtoLinks() {
        assertThat(sanitizer.sanitize("<a href=\"mailto:someone@example.org\">write</a>")).isEqualTo("write");
    }

    /** An image tag is a request to an arbitrary host, which is not something a description gets to make. */
    @Test
    void dropsImages() {
        assertThat(sanitizer.sanitize("<p>Hola</p><img src=\"https://tracker.example/x.png\">")).isEqualTo("<p>Hola</p>");
    }

    /** Null in, null out: an absent description is not the same thing as an empty one. */
    @Test
    void leavesNullAlone() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    /** What sanitizes down to nothing is not content, so it comes back absent rather than empty. */
    @Test
    void turnsTextThatIsEntirelyUnsafeIntoNull() {
        assertThat(sanitizer.sanitize("<script>alert(1)</script>")).isNull();
    }

    /** Sanitizing on the way out as well as on the way in only works if doing it twice is harmless. */
    @ParameterizedTest
    @ValueSource(strings = {
        "<p>Hola <strong>vos</strong></p><script>x</script>",
        "<a href=\"https://example.org\" onclick=\"x()\" target=\"_blank\">docs</a>",
        "<a href=\"javascript:alert(1)\">x</a><img src=x onerror=alert(1)>",
        "<scr<script>ipt>alert(1)</scr</script>ipt>",
        "<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp;amp; caf\u00e9</p>",
        "<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\">",
        "<ul><li>uno<ul><li>dos</li></ul></li></ul><pre><code>a &lt; b</code></pre>"
    })
    void isIdempotent(String html) {
        String once = sanitizer.sanitize(html);

        assertThat(sanitizer.sanitize(once)).isEqualTo(once);
    }
}
