package com.centraldungeon.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The XSS gate of #62. Every case here is something a table's description could actually carry, and
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
    @Test
    void isIdempotent() {
        String once = sanitizer.sanitize("<p>Hola <strong>vos</strong></p><script>x</script>");

        assertThat(sanitizer.sanitize(once)).isEqualTo(once);
    }
}
