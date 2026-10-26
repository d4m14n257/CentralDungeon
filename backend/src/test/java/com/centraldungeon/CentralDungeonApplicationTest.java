package com.centraldungeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class CentralDungeonApplicationTest {

    @Test
    void loadingTheApplicationPinsTheJvmClockToUtc() throws ClassNotFoundException {
        Class.forName(CentralDungeonApplication.class.getName(), true, getClass().getClassLoader());

        assertThat(ZoneId.systemDefault().normalized()).isEqualTo(ZoneOffset.UTC);
        assertThat(LocalDateTime.now()).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
    }
}
