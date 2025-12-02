package com.parallel.wordcount;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Parallel counting on CPU by dividing the text into chunks processed by a fixed-size pool.
 */
public class ParallelCpuCounter implements WordCounter {

    private final int threadCount;

    public ParallelCpuCounter(int threadCount) {
        this.threadCount = Math.max(1, threadCount);
    }

    @Override
    public String name() {
        return "ParallelCPU";
    }

    @Override
    public WordCountResult count(String datasetName, String text, String targetWord) throws ExecutionException, InterruptedException {
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String normalizedTarget = targetWord.toLowerCase(Locale.ROOT);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long start = System.nanoTime();
        List<Future<Integer>> futures = new ArrayList<>();
        int length = normalizedText.length();
        int chunkSize = Math.max(1, (int) Math.ceil((double) length / threadCount));
        for (int i = 0; i < threadCount; i++) {
            int startIdx = i * chunkSize;
            if (startIdx >= length) {
                break;
            }
            int boundary = Math.min(length, startIdx + chunkSize);
            futures.add(executor.submit(chunkTask(normalizedText, normalizedTarget, startIdx, boundary)));
        }

        int occurrences = 0;
        for (Future<Integer> future : futures) {
            occurrences += future.get();
        }
        executor.shutdown();
        long elapsed = System.nanoTime() - start;
        return new WordCountResult(name(), datasetName, occurrences, elapsed / 1_000_000, threadCount, "CPU");
    }

    private Callable<Integer> chunkTask(String text, String target, int startIdx, int boundary) {
        int maxSearchEnd = Math.min(text.length(), boundary + target.length() - 1);
        return () -> countOccurrences(text, target, startIdx, boundary, maxSearchEnd);
    }

    /**
     * Counts occurrences starting at or after startIdx and strictly before boundary.
     * The search window may extend beyond the boundary to capture words that overlap,
     * but only occurrences that start before the boundary are tallied.
     */
    private int countOccurrences(String text, String target, int startIdx, int boundary, int maxSearchEnd) {
        int count = 0;
        int idx = text.indexOf(target, startIdx);
        while (idx != -1 && idx < maxSearchEnd) {
            if (idx < boundary) {
                count++;
                idx = text.indexOf(target, idx + target.length());
            } else {
                break;
            }
        }
        return count;
    }
}
