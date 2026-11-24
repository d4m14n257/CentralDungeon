package com.centraldungeon.features.auth;

import com.centraldungeon.features.catalogs.CatalogStatus;
import com.centraldungeon.features.catalogs.CatalogValue;
import com.centraldungeon.features.catalogs.CatalogValueRepository;
import com.centraldungeon.features.catalogs.GameSystem;
import com.centraldungeon.features.catalogs.Platform;
import com.centraldungeon.features.catalogs.PlatformRepository;
import com.centraldungeon.features.catalogs.SystemRepository;
import java.util.List;
import java.util.function.Function;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fills the system and platform catalogs with a realistic set, so a development database has
 * something to pick from when creating a table.
 *
 * <p>Test data only: it lives under the "test" profile, so a production start never runs it, and it
 * is not a migration, because production starts with empty catalogs that are filled by hand. Every
 * value is added only if its name is free (compared ignoring case) and an existing value is never
 * touched, so restarting the backend adds nothing, and the e2e cleanup in {@link TestDataService}
 * cannot reach these rows: it only deletes tags that start with "E2E ".
 */
@Component
@Profile("test")
public class TestCatalogSeeder implements ApplicationRunner {

    static final List<String> SYSTEMS = List.of(
            "Dungeons & Dragons 5e (2014)", "Dungeons & Dragons 5e (2024)", "Pathfinder 2e", "Pathfinder 1e",
            "Starfinder", "Call of Cthulhu 7e", "Vampire: The Masquerade V5", "Blades in the Dark", "Mothership",
            "Delta Green", "Shadowdark", "Daggerheart", "Cyberpunk RED", "Alien RPG", "Mörk Borg", "Dungeon World",
            "Savage Worlds", "Warhammer Fantasy Roleplay 4e", "Lancer", "Kids on Bikes");

    static final List<String> PLATFORMS = List.of(
            "Discord", "Roll20", "Foundry VTT", "D&D Beyond", "Owlbear Rodeo", "Fantasy Grounds", "Alchemy RPG",
            "Por texto", "Obsidian");

    /** Where the systems go. */
    private final SystemRepository systemRepository;

    /** Where the platforms go. */
    private final PlatformRepository platformRepository;

    /**
     * @param systemRepository   the systems catalog
     * @param platformRepository the platforms catalog
     */
    public TestCatalogSeeder(SystemRepository systemRepository, PlatformRepository platformRepository) {
        this.systemRepository = systemRepository;
        this.platformRepository = platformRepository;
    }

    /**
     * Adds whatever is missing, on every start.
     *
     * @param args unused
     */
    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Inserts the systems and platforms whose name is not in the catalog yet, already accepted. */
    void seed() {
        seed(systemRepository, SYSTEMS, GameSystem::new);
        seed(platformRepository, PLATFORMS, Platform::new);
    }

    private static <E extends CatalogValue> void seed(
            CatalogValueRepository<E> repository, List<String> names, Function<String, E> factory) {
        for (String name : names) {
            if (repository.findByNameIgnoreCase(name).isEmpty()) {
                E value = factory.apply(name);
                value.setStatus(CatalogStatus.Accepted);
                repository.save(value);
            }
        }
    }
}
