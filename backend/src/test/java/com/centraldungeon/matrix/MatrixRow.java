package com.centraldungeon.matrix;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;

/**
 * One endpoint of the role matrix: how to build its request and what each actor gets.
 *
 * <p>The request function runs once per cell, against that cell's freshly emptied database, so a cell
 * that mutates something never leaves anything behind for the next one and order does not matter.
 *
 * <p>A variant is a second row for an endpoint whose expected result depends on the request and not
 * only on the actor, such as the role named in the body of {@code grant-role}. Only the main row has to
 * fill the base columns and carry a positive control.
 */
final class MatrixRow {

    /** The endpoint exactly as the handler mapping publishes it, such as {@code POST /api/v1/tags}. */
    private final String endpoint;

    /** What distinguishes this variant from the endpoint's main row, or null for the main row. */
    private final @Nullable String variant;

    /** Builds the request, creating whatever it needs in the fixtures of the cell. */
    private final Function<MatrixFixtures, MatrixRequest> request;

    /** What each actor is expected to get. */
    private final Map<MatrixActor, MatrixCell> cells = new EnumMap<>(MatrixActor.class);

    /** Why nobody can reach this endpoint with a 2xx, when that is really the case. */
    private @Nullable String noPositiveControlReason;

    private MatrixRow(String endpoint, @Nullable String variant, Function<MatrixFixtures, MatrixRequest> request) {
        if (!endpoint.matches("(GET|POST|PUT|PATCH|DELETE) /\\S+")) {
            throw new IllegalArgumentException("Expected 'METHOD /route', got: " + endpoint);
        }
        this.endpoint = endpoint;
        this.variant = variant;
        this.request = request;
    }

    /**
     * @param endpoint the method and the route, copied from the matrix, such as {@code POST /api/v1/tags}
     * @param request  builds the call, given the fixtures of the cell
     * @return the endpoint's main row, still without cells
     */
    static MatrixRow of(String endpoint, Function<MatrixFixtures, MatrixRequest> request) {
        return new MatrixRow(endpoint, null, request);
    }

    /**
     * @param label    what this variant changes, such as {@code role=Admin}; it shows up in the test's name
     * @param endpoint the method and the route, copied from the matrix
     * @param request  builds the call, given the fixtures of the cell
     * @return a further row for an endpoint that already has its main row
     */
    static MatrixRow variant(String label, String endpoint, Function<MatrixFixtures, MatrixRequest> request) {
        return new MatrixRow(endpoint, label, request);
    }

    /**
     * @param cell   what these actors are expected to get
     * @param actors who they are; an actor can only appear once in a row
     * @return this row
     */
    MatrixRow cell(MatrixCell cell, MatrixActor... actors) {
        for (MatrixActor actor : actors) {
            if (cells.putIfAbsent(actor, cell) != null) {
                throw new IllegalArgumentException(name() + " declares " + actor + " twice");
            }
        }
        return this;
    }

    /**
     * The scarce exception to the positive control, for an endpoint nobody can really reach.
     *
     * @param reason why, in a sentence
     * @return this row
     */
    MatrixRow withoutPositiveControl(String reason) {
        noPositiveControlReason = reason;
        return this;
    }

    /** @return the endpoint this row exercises */
    String endpoint() {
        return endpoint;
    }

    /** @return the endpoint, plus the variant's label when this is one */
    String name() {
        return variant == null ? endpoint : endpoint + " [" + variant + "]";
    }

    /** @return whether this is a variant rather than the endpoint's main row */
    boolean isVariant() {
        return variant != null;
    }

    /** @return the method the request is sent with */
    HttpMethod method() {
        return HttpMethod.valueOf(endpoint.substring(0, endpoint.indexOf(' ')));
    }

    /** @return the route, in the form the handler mapping publishes it */
    String pattern() {
        return endpoint.substring(endpoint.indexOf(' ') + 1);
    }

    /** @return the function that builds the request */
    Function<MatrixFixtures, MatrixRequest> request() {
        return request;
    }

    /** @return the expected result per actor, in the order of {@link MatrixActor} */
    Map<MatrixActor, MatrixCell> cells() {
        return Collections.unmodifiableMap(cells);
    }

    /** @return why this row has no positive control, or null when it has to have one */
    @Nullable String noPositiveControlReason() {
        return noPositiveControlReason;
    }
}
