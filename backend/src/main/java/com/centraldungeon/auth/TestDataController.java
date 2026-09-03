package com.centraldungeon.auth;

import com.centraldungeon.auth.dto.TestCleanupResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deletes what an e2e run left behind, so the development database stops growing one run at a time
 * (decisiones.md #172). Playwright calls it once when the suite finishes.
 *
 * <p><b>It takes no parameters on purpose.</b> The patterns it deletes are fixed in
 * {@link TestDataService} - tables whose name contains "E2E" and users whose discordId starts with
 * "e2e-" - so there is no input that could widen the blast radius. Only registered under the "test"
 * profile, like TestLoginController.
 */
@RestController
@RequestMapping("/api/v1/test-data")
@Profile("test")
public class TestDataController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final TestDataService testDataService;

    /**
     * @param testDataService performs the cleanup
     */
    public TestDataController(TestDataService testDataService) {
        this.testDataService = testDataService;
    }

    /**
     * Wipes what the Playwright suite left behind, so a run starts from a known state.
     *
     * <p>Reachable only under the {@code test} profile: without that profile there is no bean, so the
     * path 404s in dev and in prod no matter what {@code SecurityConfig} says about it.
     *
     * @return 200 with how many rows were removed per table, which is what makes a failed cleanup
     *         visible instead of silent
     */
    @DeleteMapping("/e2e")
    public TestCleanupResponse deleteE2eData() {
        return testDataService.deleteE2eData();
    }
}
