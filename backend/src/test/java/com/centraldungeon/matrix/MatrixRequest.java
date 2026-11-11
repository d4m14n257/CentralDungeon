package com.centraldungeon.matrix;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The call a row makes, the same one for every actor of the row: path variables, query, and a valid
 * JSON or multipart body. The body has to be valid because an invalid one can answer before
 * authorization gets a say.
 */
final class MatrixRequest {

    /** The values of the route's {@code {variables}}, in the order they appear. */
    private final Object[] pathVariables;

    /** Query parameters, as name and value pairs. */
    private final List<String[]> query = new ArrayList<>();

    /** Extra headers, as name and value pairs. */
    private final List<String[]> headers = new ArrayList<>();

    /** Cookies to send. */
    private final List<Cookie> cookies = new ArrayList<>();

    /** The parts of a multipart body; empty for anything else. */
    private final List<MockMultipartFile> parts = new ArrayList<>();

    /** The JSON body, or null when the call has none. */
    private @Nullable String json;

    private MatrixRequest(Object[] pathVariables) {
        this.pathVariables = pathVariables;
    }

    /**
     * @param variables the values of the route's {@code {variables}}, in order; none for a fixed route
     * @return a request with no query and no body
     */
    static MatrixRequest path(Object... variables) {
        return new MatrixRequest(variables);
    }

    /**
     * @param name  the parameter's name
     * @param value its value
     * @return this request
     */
    MatrixRequest query(String name, String value) {
        query.add(new String[] {name, value});
        return this;
    }

    /**
     * @param body the JSON body
     * @return this request
     */
    MatrixRequest json(String body) {
        json = body;
        return this;
    }

    /**
     * Adds an uploaded PDF, which makes this a multipart call.
     *
     * @param name    the part's name
     * @param content the bytes; {@link MatrixFixtures#uniqueBytes()} unless the row means to deduplicate
     * @return this request
     */
    MatrixRequest filePart(String name, byte[] content) {
        parts.add(new MockMultipartFile(name, "sheet.pdf", MediaType.APPLICATION_PDF_VALUE, content));
        return this;
    }

    /**
     * Adds a JSON part, which makes this a multipart call.
     *
     * @param name the part's name
     * @param body the JSON
     * @return this request
     */
    MatrixRequest jsonPart(String name, String body) {
        parts.add(new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE, body.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    /**
     * @param name  the cookie's name
     * @param value its value
     * @return this request
     */
    MatrixRequest cookie(String name, String value) {
        cookies.add(new Cookie(name, value));
        return this;
    }

    /**
     * @param name  the header's name
     * @param value its value
     * @return this request
     */
    MatrixRequest header(String name, String value) {
        headers.add(new String[] {name, value});
        return this;
    }

    /** @return how many path variables this request carries, to compare against the route's */
    int pathVariableCount() {
        return pathVariables.length;
    }

    /**
     * @param method  the row's method
     * @param pattern the row's route
     * @return the MockMvc request, still without the actor's token
     */
    AbstractMockHttpServletRequestBuilder<?> toBuilder(HttpMethod method, String pattern) {
        AbstractMockHttpServletRequestBuilder<?> builder = parts.isEmpty()
                ? MockMvcRequestBuilders.request(method, pattern, pathVariables)
                : multipart(method, pattern);
        query.forEach(pair -> builder.queryParam(pair[0], pair[1]));
        headers.forEach(pair -> builder.header(pair[0], pair[1]));
        if (!cookies.isEmpty()) {
            builder.cookie(cookies.toArray(Cookie[]::new));
        }
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return builder;
    }

    private MockMultipartHttpServletRequestBuilder multipart(HttpMethod method, String pattern) {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(method, pattern, pathVariables);
        parts.forEach(builder::file);
        return builder;
    }
}
