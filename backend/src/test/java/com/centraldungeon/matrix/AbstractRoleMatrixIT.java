package com.centraldungeon.matrix;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * What every role matrix class shares: one MySQL for all of them, so they also share one Spring
 * context, and storage in a temporary directory so uploads never land in the working tree.
 *
 * <p>The container is started once per JVM and stopped when the JVM exits, rather than per class:
 * the matrix classes would otherwise pay a container and a context each. Its durability settings are
 * relaxed because every cell empties and refills the database, and nothing here needs to survive a
 * crash. No profile is activated, so the handlers that only exist under the {@code test} profile are
 * absent, as they are in production.
 */
@SpringBootTest
abstract class AbstractRoleMatrixIT {

    /** The database of every matrix class. */
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withCommand("--innodb-flush-log-at-trx-commit=0", "--sync-binlog=0", "--skip-log-bin");

    /** Where uploaded content goes during the run. */
    private static final Path STORAGE_ROOT;

    static {
        MYSQL.start();
        Runtime.getRuntime().addShutdownHook(new Thread(MYSQL::stop));
        try {
            STORAGE_ROOT = Files.createTempDirectory("role-matrix-storage");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The application, for the harness. */
    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    /** @return the rows this class covers */
    abstract List<MatrixRow> rows();

    /** @return a harness over this class's application context */
    RoleMatrixHarness harness() {
        return new RoleMatrixHarness(context);
    }

    @TestFactory
    Stream<DynamicTest> matrix() {
        return harness().tests(rows());
    }

    @AfterAll
    static void reportPendingTriage() {
        RoleMatrixHarness.printPendingTriage();
    }
}
