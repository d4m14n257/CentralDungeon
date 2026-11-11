package com.centraldungeon.matrix;

import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.matrix.KnownDisagreements.Entry;
import com.centraldungeon.users.User;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Runs the rows of the role matrix over the real filter chain, the real tokens and a real database.
 *
 * <p>Each row gives one dynamic test that checks its shape, named {@code METHOD route × ROW SHAPE}, and
 * one per cell, named {@code METHOD route × ACTOR}, so a red result reads without opening anything. A
 * cell:
 * <ol>
 *   <li>empties the database and builds the row's request in a fresh {@link MatrixFixtures};</li>
 *   <li>gives the actor its account and relation, and evicts every cache;</li>
 *   <li>runs the preflight: the actor's token on {@code /users/me} has to answer 200 with exactly the
 *       actor's roles. For {@code BLOCKED} that is the positive control of the column - the same token
 *       worked a moment before the account was blocked;</li>
 *   <li>sends the request and demands that, whenever a handler was reached, it is the row's own route,
 *       so a wrong path cannot pass as a business 404;</li>
 *   <li>compares the exact status, the code and the body against the cell, or against the entry of
 *       {@link KnownDisagreements} when there is one.</li>
 * </ol>
 */
final class RoleMatrixHarness {

    /** The route the preflight asks. */
    private static final String ME = "/api/v1/users/me";

    /** How much of a body a failure message quotes. */
    private static final int QUOTED_BODY = 600;

    /** The undocumented cells that passed in this class, printed once it is done. */
    private static final List<String> PENDING_TRIAGE = new ArrayList<>();

    /** Where the fixtures get their beans. */
    private final ApplicationContext context;

    /** The application, behind the real security filter chain. */
    private final MockMvc mockMvc;

    /** Mints the actors' tokens the same way the login does. */
    private final JwtService jwtService;

    /** Holds the cached roles and status, which fixtures written behind the application's back make stale. */
    private final CacheManager cacheManager;

    /**
     * @param context the web application context of the test
     */
    RoleMatrixHarness(ApplicationContext context) {
        this.context = context;
        this.mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
        this.jwtService = context.getBean(JwtService.class);
        this.cacheManager = context.getBean(CacheManager.class);
    }

    /**
     * @param rows the rows of one test class
     * @return a shape test per row and a test per cell
     */
    Stream<DynamicTest> tests(List<MatrixRow> rows) {
        return rows.stream().flatMap(row -> Stream.concat(
                Stream.of(DynamicTest.dynamicTest(row.name() + " × ROW SHAPE", () -> checkShape(row))),
                row.cells().entrySet().stream().map(cell -> cellTest(row, cell.getKey(), cell.getValue()))));
    }

    /**
     * A main row has to fill the six base columns and hold a positive control: a cell that is expected
     * to pass, which is what proves that its path and its body reach the handler at all.
     *
     * @param row the row
     */
    void checkShape(MatrixRow row) {
        if (row.isVariant()) {
            return;
        }
        List<MatrixActor> missing = MatrixActor.BASE.stream().filter(actor -> !row.cells().containsKey(actor)).toList();
        if (!missing.isEmpty()) {
            throw new AssertionError(row.name() + " leaves base columns without a cell: " + missing);
        }
        if (row.noPositiveControlReason() != null) {
            return;
        }
        boolean controlled = row.cells().entrySet().stream().anyMatch(cell -> {
            Entry disagreement = KnownDisagreements.find(row.name(), cell.getKey());
            return disagreement == null ? cell.getValue().isPositiveControl() : MatrixCell.isSuccess(disagreement.observed());
        });
        if (!controlled) {
            throw new AssertionError(row.name() + " has no cell expected to pass, so nothing proves its path and body are"
                    + " right. Add the actor who can do it, or withoutPositiveControl(reason) if really nobody can");
        }
    }

    /**
     * Runs one cell.
     *
     * @param row          the row
     * @param actor        the column
     * @param cell         what the column expects
     * @param disagreement the entry of {@link KnownDisagreements} for this cell, or null
     * @throws Exception when the call cannot be made or the body check fails to run
     */
    void runCell(MatrixRow row, MatrixActor actor, MatrixCell cell, @Nullable Entry disagreement) throws Exception {
        String label = row.name() + " × " + actor;
        MatrixFixtures w = MatrixFixtures.forCell(context, actor);
        MatrixRequest request = row.request().apply(w);
        w.settle();
        requirePathVariables(row, request, label);
        evictCaches();

        AbstractMockHttpServletRequestBuilder<?> call = request.toBuilder(row.method(), row.pattern());
        if (actor != MatrixActor.ANONYMOUS) {
            User account = w.me();
            String token = jwtService.issueAccessToken(account.getId());
            preflight(label, actor, token);
            if (actor == MatrixActor.BLOCKED) {
                w.block(account);
                evictCaches();
            }
            call.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        MvcResult result = mockMvc.perform(call).andReturn();
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String seen = label + " answered " + status + " " + quote(body);
        requireRouted(row, result, seen);

        if (disagreement != null) {
            if (status != disagreement.observed()) {
                throw new AssertionError(seen + " - KnownDisagreements lists it as answering " + disagreement.observed()
                        + " (" + disagreement.reason() + "). It changed: remove the entry and give the cell what was decided");
            }
            return;
        }
        compare(cell, w, status, body, seen);
        if (cell.kind() == MatrixCell.Kind.UNDOCUMENTED) {
            PENDING_TRIAGE.add(label + " answers " + status + propertyNote(cell.property()));
        }
    }

    /** Prints the undocumented cells that passed, and forgets them for the next class. */
    static void printPendingTriage() {
        if (PENDING_TRIAGE.isEmpty()) {
            return;
        }
        System.out.println("Role matrix - cells no document fixes, pending triage (" + PENDING_TRIAGE.size() + "):");
        PENDING_TRIAGE.forEach(line -> System.out.println("  [pending triage] " + line));
        PENDING_TRIAGE.clear();
    }

    private DynamicTest cellTest(MatrixRow row, MatrixActor actor, MatrixCell cell) {
        Entry disagreement = KnownDisagreements.find(row.name(), actor);
        String suffix = disagreement != null
                ? " [pending decision]"
                : cell.kind() == MatrixCell.Kind.UNDOCUMENTED ? " [pending triage]" : "";
        return DynamicTest.dynamicTest(row.name() + " × " + actor + suffix, () -> runCell(row, actor, cell, disagreement));
    }

    private static void compare(MatrixCell cell, MatrixFixtures w, int status, String body, String seen) throws Exception {
        if (cell.property() == MatrixCell.Property.MUST_NOT_BE_2XX && MatrixCell.isSuccess(status)) {
            throw new AssertionError(seen + " - the documents say this must not pass. That is a finding: it goes to"
                    + " KnownDisagreements, not into the cell");
        }
        if (cell.property() == MatrixCell.Property.MUST_BE_2XX && !MatrixCell.isSuccess(status)) {
            throw new AssertionError(seen + " - the documents say this has to pass");
        }
        if (status != cell.status()) {
            throw new AssertionError(seen + " - expected exactly " + cell.status());
        }
        if (cell.errorCode() != null && !cell.errorCode().equals(errorCode(body))) {
            throw new AssertionError(seen + " - expected errorCode " + cell.errorCode());
        }
        if (cell.bodyCheck() != null) {
            try {
                cell.bodyCheck().check(w, body);
            } catch (AssertionError failed) {
                // The bare AssertJ message does not say which cell it was, and the dynamic test name is an index.
                throw new AssertionError(seen + " - the body check failed: " + failed.getMessage(), failed);
            }
        }
    }

    private void preflight(String label, MatrixActor actor, String token) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("Preflight of " + label + ": " + ME + " answered "
                    + result.getResponse().getStatus() + " " + quote(body) + " before the call");
        }
        List<String> roles = JsonPath.read(body, "$.roles");
        if (!new HashSet<>(roles).equals(new HashSet<>(actor.roleNames()))) {
            throw new AssertionError("Preflight of " + label + ": the account holds " + roles + ", not " + actor.roleNames());
        }
    }

    private static void requireRouted(MatrixRow row, MvcResult result, String seen) {
        Object handler = result.getHandler();
        if (handler == null) {
            return;
        }
        if (!(handler instanceof HandlerMethod)) {
            throw new AssertionError(seen + " - no controller matched: the row's path is wrong");
        }
        Object pattern = result.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!row.pattern().equals(pattern)) {
            throw new AssertionError(seen + " - it reached " + pattern + ", not " + row.pattern());
        }
    }

    private static void requirePathVariables(MatrixRow row, MatrixRequest request, String label) {
        long expected = row.pattern().chars().filter(c -> c == '{').count();
        if (request.pathVariableCount() != expected) {
            throw new AssertionError(label + ": the route has " + expected + " path variables and the request gives "
                    + request.pathVariableCount());
        }
    }

    private void evictCaches() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    private static @Nullable String errorCode(String body) {
        if (body.isBlank()) {
            return null;
        }
        try {
            return JsonPath.read(body, "$.errorCode");
        } catch (JsonPathException e) {
            return null;
        }
    }

    private static String propertyNote(MatrixCell.Property property) {
        return switch (property) {
            case NONE -> " (no document says whether it passes)";
            case MUST_BE_2XX -> " (it has to pass; no document says with which status)";
            case MUST_NOT_BE_2XX -> " (it must not pass; no document says how it is refused)";
        };
    }

    private static String quote(String body) {
        return body.length() <= QUOTED_BODY ? body : body.substring(0, QUOTED_BODY) + "...";
    }
}
