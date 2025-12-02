package com.parallel.wordcount;

import java.util.Locale;

/**
 * Serial counting on CPU using a simple index scan.
 */
public class SerialCpuCounter implements WordCounter {

    @Override
    public String name() {
        return "SerialCPU";
    }

    @Override
    public WordCountResult count(String datasetName, String text, String targetWord) {
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String normalizedTarget = targetWord.toLowerCase(Locale.ROOT);
        long start = System.nanoTime();
        int occurrences = countOccurrences(normalizedText, normalizedTarget);
        long elapsed = System.nanoTime() - start;
        return new WordCountResult(name(), datasetName, occurrences, elapsed / 1_000_000, 1, "CPU");
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
