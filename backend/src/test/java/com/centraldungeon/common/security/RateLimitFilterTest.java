package com.centraldungeon.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.centraldungeon.common.config.RateLimitProperties;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

/** What the filter writes to the log when it refuses a request. */
class RateLimitFilterTest {

    private static final String CLIENT_IP = "203.0.113.77";

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RateLimitFilter.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        filterLogger.addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        filterLogger.detachAppender(logged);
    }

    @Test
    void aRefusalNamesTheLimitButNeverTheCallerNorAnythingDerivedFromIt() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("test", List.of(new RateLimitFilter.Rule(
                "login",
                AnyRequestMatcher.INSTANCE,
                new FixedWindowLimiter(new RateLimitProperties.Limit(1, Duration.ofMinutes(1)), InstantSource.system()),
                request -> request.getRemoteAddr())), JsonMapper.builder().build());

        MockHttpServletResponse response = null;
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(CLIENT_IP);
            response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(logged.list).singleElement().satisfies(event -> {
            String line = event.getFormattedMessage();
            assertThat(line).contains("login");
            assertThat(line).doesNotContain(CLIENT_IP).doesNotContain(Integer.toHexString(CLIENT_IP.hashCode()));
        });
    }
}
