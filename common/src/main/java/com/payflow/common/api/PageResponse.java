package com.payflow.common.api;

import java.util.List;

public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, long totalPages) {
  public static void validate(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || page > 1000000)
      throw new IllegalArgumentException("Invalid page");
  }

  public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
    return new PageResponse<>(items, page, size, total, (total + size - 1) / size);
  }
}
