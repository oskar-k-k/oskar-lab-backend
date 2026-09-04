package dev.oskar_lab.backend.core

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

class ListRequest {
    @NotNull
    @Min(0L)
    Integer page = 0

    @NotNull
    @Min(1L)
    @Max(100L)
    Integer pageSize = 50
}
