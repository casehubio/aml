package io.casehub.aml.domain;

import java.util.List;

public record PagedResponse<T>(
    List<T> items,
    long total,
    int page,
    int pageSize
) {}
