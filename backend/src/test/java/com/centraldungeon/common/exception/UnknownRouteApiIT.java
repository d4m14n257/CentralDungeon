package com.centraldungeon.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * What an anonymous caller gets when the route is not what the application serves: an unknown path
 * under a prefix that needs no session, and a method the route does not take.
 *
 * <p>Both used to come back as a 500 that told the caller the server was broken when the truth is that
 * the request was wrong. Anybody can send them without a token, so they also have to say nothing about
 * the server's insides: no path, no framework message.
 */
@SpringBootTest
@Testcontainers
class UnknownRouteApiIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    /** Built by hand, with the real security filter chain, as the other integration tests do. */
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    /** {@code /test-discord} is permitted without a session but is only mapped under the test profile. */
    @Test
    void anUnknownRouteUnderAPublicPrefixIsNotFound() throws Exception {
        mockMvc.perform(get("/test-discord/whatever"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Not Found"));
    }

    /** The routes that only exist under the test profile are permitted without a session and answer 404 without it. */
    @Test
    void theTestProfileRoutesAreNotFoundWithoutTheTestProfile() throws Exception {
        mockMvc.perform(post("/api/v1/auth/test-login")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/test-data/e2e")).andExpect(status().isNotFound());
        mockMvc.perform(get("/test-discord/users/@me")).andExpect(status().isNotFound());
    }

    /** The health route is public and takes only GET. */
    @Test
    void aMethodTheRouteDoesNotTakeIsNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail").value("Method Not Allowed"));
    }
}
