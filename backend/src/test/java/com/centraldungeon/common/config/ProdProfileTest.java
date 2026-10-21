package com.centraldungeon.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards the production profile against falling back to a development default for anything that is
 * a secret or depends on where the system runs: application.yml carries defaults so a local machine
 * boots, and in production every one of them must come from the environment or the start-up must
 * fail (ADR-02 §5, ADR-05 §2).
 */
class ProdProfileTest {

    private static final Pattern REQUIRED_PLACEHOLDER = Pattern.compile("\\$\\{[A-Z_]+}");

    @ParameterizedTest
    @ValueSource(strings = {
        "spring.datasource.url",
        "spring.datasource.username",
        "spring.datasource.password",
        "spring.security.oauth2.client.registration.discord.client-id",
        "spring.security.oauth2.client.registration.discord.client-secret",
        "discord.guild-id",
        "app.cors.allowed-origin",
        "app.frontend.url",
        "app.jwt.secret",
        "app.storage.root"
    })
    void everyEnvironmentSpecificValueIsRequiredWithoutDefault(String key) throws IOException {
        assertThat(prodProperties()).containsKey(key);
        assertThat(prodProperties().get(key)).matches(REQUIRED_PLACEHOLDER);
    }

    @Test
    void noValueOfTheProdProfileHasAPlaceholderDefault() throws IOException {
        assertThat(prodProperties().values()).noneMatch(value -> value.matches(".*\\$\\{[A-Z_]+:.*"));
    }

    private static Map<String, String> prodProperties() throws IOException {
        try (InputStream yaml = ProdProfileTest.class.getResourceAsStream("/application-prod.yml")) {
            Map<String, String> flat = new LinkedHashMap<>();
            flatten("", new Yaml().load(yaml), flat);
            return flat;
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Object node, Map<String, String> into) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> flatten(prefix.isEmpty() ? key.toString() : prefix + "." + key, value, into));
        } else {
            into.put(prefix, String.valueOf(node));
        }
    }
}
