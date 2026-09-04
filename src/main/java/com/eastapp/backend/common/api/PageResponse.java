package com.eastapp.backend.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return from(source, source.getContent().stream().map(mapper).toList());
    }

    public static <T> PageResponse<T> from(Page<?> source, List<T> content) {
        return new PageResponse<>(
                List.copyOf(content),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast()
        );
    }
}
