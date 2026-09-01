package com.centraldungeon.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.health.dto.HealthResponse;
import org.junit.jupiter.api.Test;

class HealthServiceTest {

    @Test
    void reportsUpWheneverTheProcessCanAnswer() {
        HealthResponse response = new HealthService().checkStatus();

        assertThat(response.status()).isEqualTo("UP");
    }
}
