package com.centraldungeon.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The role matrix covers the application's endpoints, all of them and nothing else, and two barriers
 * that every cell of it relies on.
 *
 * <p>The endpoints come from the real handler mapping, limited to this application's own controllers.
 * It compares sets of endpoints and never authorization: what each actor should get is written in the
 * rows from the documents, and is never read from the {@code @PreAuthorize} it is meant to check.
 */
class RoleMatrixCompletenessIT extends AbstractRoleMatrixIT {

    /** The three endpoints that serve callers without a session, and so declare no authorization. */
    private static final Set<String> PUBLIC_ENDPOINTS =
            Set.of("POST /api/v1/auth/refresh", "POST /api/v1/auth/logout", "GET /api/v1/health");

    /** Where every controller method is registered. */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /** To look for beans that must not exist. */
    @Autowired
    private ApplicationContext applicationContext;

    @Override
    List<MatrixRow> rows() {
        return List.of();
    }

    /** Every endpoint has rows, and every row names an endpoint that exists. */
    @Test
    void theRowsCoverExactlyTheEndpoints() {
        Set<String> endpoints = endpoints().keySet();
        Set<String> covered = allRows().stream().map(MatrixRow::endpoint).collect(Collectors.toCollection(TreeSet::new));

        Set<String> missing = new TreeSet<>(endpoints);
        missing.removeAll(covered);
        Set<String> unknown = new TreeSet<>(covered);
        unknown.removeAll(endpoints);

        assertThat(missing)
                .as("%d of %d endpoints have no row:%n  %s", missing.size(), endpoints.size(), String.join("\n  ", missing))
                .isEmpty();
        assertThat(unknown)
                .as("%d rows name an endpoint the application does not have:%n  %s", unknown.size(), String.join("\n  ", unknown))
                .isEmpty();
    }

    /** Each endpoint has one main row, the one that fills the base columns; the rest are variants. */
    @Test
    void everyEndpointHasExactlyOneMainRow() {
        Map<String, Long> mainRows = allRows().stream()
                .filter(row -> !row.isVariant())
                .collect(Collectors.groupingBy(MatrixRow::endpoint, TreeMap::new, Collectors.counting()));
        Set<String> variantsOnly = allRows().stream()
                .filter(MatrixRow::isVariant)
                .map(MatrixRow::endpoint)
                .filter(endpoint -> !mainRows.containsKey(endpoint))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(mainRows).as("Endpoints with more than one main row").allSatisfy((endpoint, count) -> assertThat(count)
                .as(endpoint)
                .isEqualTo(1L));
        assertThat(variantsOnly).as("Endpoints with variants and no main row").isEmpty();
    }

    /** A disagreement that points at no cell is stale, and would hide nothing but itself. */
    @Test
    void everyKnownDisagreementPointsAtACell() {
        List<KnownDisagreements.Entry> stale = KnownDisagreements.ENTRIES.stream()
                .filter(entry -> allRows().stream()
                        .noneMatch(row -> row.name().equals(entry.row()) && row.cells().containsKey(entry.actor())))
                .toList();

        assertThat(stale).as("Entries of KnownDisagreements with no such row and actor").isEmpty();
    }

    /**
     * The integration tests activate no profile, so the controllers that only exist under the {@code test}
     * profile - the test login, the stubbed Discord and the e2e cleanup - are not registered and the
     * matrix does not have to cover them.
     */
    @Test
    void theTestProfileHandlersAreNotRegistered() {
        assertThat(applicationContext.getEnvironment().getActiveProfiles()).doesNotContain("test");
        assertThat(endpoints().keySet())
                .noneMatch(endpoint -> endpoint.contains("test-login") || endpoint.contains("test-data")
                        || endpoint.contains("test-discord"));
        assertThat(endpoints().values())
                .extracting(handler -> handler.getBeanType().getSimpleName())
                .noneMatch(name -> name.startsWith("Test"));
    }

    /**
     * Every endpoint but the public ones declares its authorization on the method itself. That is what
     * the matrix leans on: a 403 does not say who refused, and only an annotation on each method makes
     * sure a refusal came from a decision taken for that endpoint.
     */
    @Test
    void everyNonPublicHandlerDeclaresPreAuthorizeOnTheMethod() {
        Set<String> undeclared = endpoints().entrySet().stream()
                .filter(entry -> !PUBLIC_ENDPOINTS.contains(entry.getKey()))
                .filter(entry -> !entry.getValue().getMethod().isAnnotationPresent(PreAuthorize.class))
                .map(entry -> entry.getKey() + "  (" + entry.getValue().getShortLogMessage() + ")")
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(undeclared)
                .as("Handlers without @PreAuthorize on the method:%n  %s", String.join("\n  ", undeclared))
                .isEmpty();
        assertThat(endpoints().keySet()).as("The public endpoints exist").containsAll(PUBLIC_ENDPOINTS);
    }

    /** Owner gets what Admin gets by being listed on each endpoint, never by a role hierarchy. */
    @Test
    void noRoleHierarchyIsRegistered() {
        assertThat(applicationContext.getBeanNamesForType(RoleHierarchy.class, true, false)).isEmpty();
    }

    /** @return every row of the four matrix classes */
    static List<MatrixRow> allRows() {
        return Stream.of(TablesRoleMatrixIT.ROWS, RegistrationsTasksRoleMatrixIT.ROWS, FilesCatalogsRoleMatrixIT.ROWS,
                        PlatformRoleMatrixIT.ROWS)
                .flatMap(List::stream)
                .toList();
    }

    /**
     * @return this application's endpoints as {@code METHOD /route}, with their handler. A mapping that
     *         accepts any method is listed as {@code ANY}, which no row can name, so it gets reported
     */
    private Map<String, HandlerMethod> endpoints() {
        Map<String, HandlerMethod> endpoints = new TreeMap<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.centraldungeon")) {
                return;
            }
            Set<String> methods = info.getMethodsCondition().getMethods().isEmpty()
                    ? Set.of("ANY")
                    : info.getMethodsCondition().getMethods().stream().map(RequestMethod::name).collect(Collectors.toSet());
            for (String method : methods) {
                for (String pattern : info.getPatternValues()) {
                    endpoints.put(method + " " + pattern, handler);
                }
            }
        });
        return endpoints;
    }
}
