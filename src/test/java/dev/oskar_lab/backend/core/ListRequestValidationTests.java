package dev.oskar_lab.backend.core;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListRequestValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsDefaultPagination() {
        assertTrue(validator.validate(new ListRequest()).isEmpty());
    }

    @Test
    void rejectsNegativePagesAndOversizedRequests() {
        var request = new ListRequest();
        request.setPage(-1);
        request.setPageSize(101);

        assertEquals(2, validator.validate(request).size());
    }
}
