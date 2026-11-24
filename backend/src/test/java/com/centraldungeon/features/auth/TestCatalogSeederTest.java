package com.centraldungeon.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.features.catalogs.CatalogStatus;
import com.centraldungeon.features.catalogs.GameSystem;
import com.centraldungeon.features.catalogs.Platform;
import com.centraldungeon.features.catalogs.PlatformRepository;
import com.centraldungeon.features.catalogs.SystemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TestCatalogSeederTest {

    private final SystemRepository systems = mock(SystemRepository.class);
    private final PlatformRepository platforms = mock(PlatformRepository.class);
    private final TestCatalogSeeder seeder = new TestCatalogSeeder(systems, platforms);

    @Test
    void emptyCatalogsGetEveryValueAccepted() {
        when(systems.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(platforms.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        seeder.seed();

        ArgumentCaptor<GameSystem> savedSystems = ArgumentCaptor.forClass(GameSystem.class);
        verify(systems, times(TestCatalogSeeder.SYSTEMS.size())).save(savedSystems.capture());
        assertThat(savedSystems.getAllValues()).extracting(GameSystem::getName)
                .containsExactlyElementsOf(TestCatalogSeeder.SYSTEMS);
        assertThat(savedSystems.getAllValues()).allMatch(s -> s.getStatus() == CatalogStatus.Accepted);

        ArgumentCaptor<Platform> savedPlatforms = ArgumentCaptor.forClass(Platform.class);
        verify(platforms, times(TestCatalogSeeder.PLATFORMS.size())).save(savedPlatforms.capture());
        assertThat(savedPlatforms.getAllValues()).extracting(Platform::getName)
                .containsExactlyElementsOf(TestCatalogSeeder.PLATFORMS);
    }

    @Test
    void aSecondRunInsertsNothingAndAnExistingValueIsNotTouched() {
        List<String> taken = new ArrayList<>();
        GameSystem existing = new GameSystem("Pathfinder 2e");
        existing.setStatus(CatalogStatus.Rejected);
        when(systems.findByNameIgnoreCase(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            if (name.equals("Pathfinder 2e")) {
                return Optional.of(existing);
            }
            return taken.contains(name) ? Optional.of(new GameSystem(name)) : Optional.empty();
        });
        when(systems.save(any(GameSystem.class))).thenAnswer(call -> {
            taken.add(call.<GameSystem>getArgument(0).getName());
            return call.getArgument(0);
        });
        when(platforms.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(new Platform("Discord")));

        seeder.seed();
        seeder.seed();

        verify(systems, times(TestCatalogSeeder.SYSTEMS.size() - 1)).save(any(GameSystem.class));
        verify(systems, never()).save(existing);
        assertThat(existing.getStatus()).isEqualTo(CatalogStatus.Rejected);
        verify(platforms, never()).save(any(Platform.class));
    }
}
