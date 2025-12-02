package com.parallel.wordcount;

/**
 * Aggregated information about a single execution of a counting method.
 */
public record WordCountResult(
        String method,
        String dataset,
        int occurrences,
        long durationMillis,
        Integer threads,
        String deviceType) {
}
