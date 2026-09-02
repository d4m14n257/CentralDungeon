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

    private final TestDataService testDataService;

    public TestDataController(TestDataService testDataService) {
        this.testDataService = testDataService;
    }

    @DeleteMapping("/e2e")
    public TestCleanupResponse deleteE2eData() {
        return testDataService.deleteE2eData();
    }
}
