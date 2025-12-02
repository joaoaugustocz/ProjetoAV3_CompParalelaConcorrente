package com.parallel.wordcount.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parametros escolhidos na interface para uma bateria de testes.
 */
public record UiConfig(
        List<Path> datasets,
        String word,
        int runs,
        List<Integer> threads,
        boolean includeSerial,
        boolean includeCpu,
        boolean includeGpu,
        boolean includeGpuOpt,
        boolean includeGpuOptCached) {

    public UiConfig {
        Objects.requireNonNull(datasets, "datasets");
        Objects.requireNonNull(word, "word");
        Objects.requireNonNull(threads, "threads");
    }

    /**
     * Cria uma copia imutavel garantindo listas ordenadas e sem duplicados.
     */
    public UiConfig normalized() {
        List<Path> ds = List.copyOf(datasets);
        List<Integer> th = new ArrayList<>(threads);
        Collections.sort(th);
        List<Integer> deduped = new ArrayList<>();
        Integer last = null;
        for (Integer v : th) {
            if (v != null && v > 0 && !v.equals(last)) {
                deduped.add(v);
                last = v;
            }
        }
        return new UiConfig(ds, word.trim(), runs, List.copyOf(deduped),
                includeSerial, includeCpu, includeGpu, includeGpuOpt, includeGpuOptCached);
    }
}
