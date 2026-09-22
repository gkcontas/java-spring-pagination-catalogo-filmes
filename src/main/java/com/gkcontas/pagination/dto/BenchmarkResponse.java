package com.gkcontas.pagination.dto;

import java.util.List;

public record BenchmarkResponse(
        int pageSize,
        String methodology,
        List<BenchmarkRow> results) {

    public record BenchmarkRow(
            int page,
            long rowsSkippedByOffset,
            double offsetQueryMillis,
            double countQueryMillis,
            double offsetTotalMillis,
            double keysetQueryMillis,
            String verdict) {
    }
}
