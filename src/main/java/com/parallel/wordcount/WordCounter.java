package com.parallel.wordcount;

/**
 * Base contract for word counting strategies.
 */
public interface WordCounter {
    String name();

    WordCountResult count(String datasetName, String text, String targetWord) throws Exception;
}
