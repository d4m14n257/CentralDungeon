package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.UpdateGameTableRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The catalog lists a table is written with are bounded.
 *
 * <p>Each id costs a lookup and a row when the links are replaced, so a list nobody bounded was a way to
 * make one request run thousands of queries in a single transaction. The bound is far above anything a
 * real table carries and the same one the admin catalog operations use.
 */
class GameTableRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void createRefusesMoreThanSixtyFourValuesInEachCatalog() {
        List<String> tooMany = ids(65);
        CreateGameTableRequest request = new CreateGameTableRequest(
                "Mesa", null, null, null, null, tooMany, tooMany, tooMany, null, null, null, null);

        assertThat(invalidFields(validator.validate(request))).containsExactlyInAnyOrder("systemIds", "tagIds", "platformIds");
    }

    @Test
    void updateRefusesMoreThanSixtyFourValuesInEachCatalog() {
        List<String> tooMany = ids(65);
        UpdateGameTableRequest request = new UpdateGameTableRequest(
                "Mesa", null, null, null, null, tooMany, tooMany, tooMany, null, null, null, null);

        assertThat(invalidFields(validator.validate(request))).containsExactlyInAnyOrder("systemIds", "tagIds", "platformIds");
    }

    @Test
    void sixtyFourValuesAreStillAccepted() {
        List<String> enough = ids(64);
        CreateGameTableRequest request = new CreateGameTableRequest(
                "Mesa", null, null, null, null, enough, enough, enough, null, null, null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    private static <T> List<String> invalidFields(java.util.Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).toList();
    }

    private static List<String> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> "value-" + i).toList();
    }
}
