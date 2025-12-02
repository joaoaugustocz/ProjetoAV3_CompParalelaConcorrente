package com.parallel.wordcount.ui;

import com.parallel.wordcount.ParallelCpuCounter;
import com.parallel.wordcount.ParallelGpuCounter;
import com.parallel.wordcount.ParallelGpuOptimizedCounter;
import com.parallel.wordcount.ParallelGpuOptimizedCachedCounter;
import com.parallel.wordcount.SerialCpuCounter;
import com.parallel.wordcount.TextLoader;
import com.parallel.wordcount.WordCountResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Executor assincrono usado pela interface Swing para rodar os cenarios.
 */
public class UiBenchmarkExecutor {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ui-benchmark-runner");
        t.setDaemon(true);
        return t;
    });

    private final List<WordCountResult> history = Collections.synchronizedList(new ArrayList<>());
    private final SerialCpuCounter serial = new SerialCpuCounter();
    private final ParallelGpuCounter gpu = new ParallelGpuCounter();
    private final ParallelGpuOptimizedCounter gpuOpt = new ParallelGpuOptimizedCounter();
    private final ParallelGpuOptimizedCachedCounter gpuOptCached = new ParallelGpuOptimizedCachedCounter();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    public void execute(UiConfig config,
                        Consumer<String> statusConsumer,
                        Consumer<WordCountResult> resultConsumer,
                        Runnable onStart,
                        Runnable onFinish) {
        UiConfig normalized = config.normalized();
        if (running.compareAndSet(false, true)) {
            executor.submit(() -> {
                onStart.run();
                try {
                    runBenchmarks(normalized, statusConsumer, resultConsumer);
                    statusConsumer.accept("Execucao concluida.");
                } catch (Exception e) {
                    statusConsumer.accept("Falha na execucao: " + e.getMessage());
                } finally {
                    running.set(false);
                    onFinish.run();
                }
            });
        } else {
            statusConsumer.accept("Ja existe uma execucao em andamento.");
        }
    }

    private void runBenchmarks(UiConfig config,
                               Consumer<String> statusConsumer,
                               Consumer<WordCountResult> resultConsumer) {
        if (config.word().isBlank()) {
            throw new IllegalArgumentException("Informe uma palavra alvo.");
        }
        for (Path dataset : config.datasets()) {
            String datasetName = dataset.getFileName().toString();
            String text;
            try {
                text = TextLoader.load(dataset);
            } catch (Exception e) {
                statusConsumer.accept("Erro ao ler " + datasetName + ": " + e.getMessage());
                continue;
            }

            statusConsumer.accept(String.format("Dataset %s (%d chars) | %d amostras",
                    datasetName, text.length(), config.runs()));

            for (int run = 1; run <= config.runs(); run++) {
                statusConsumer.accept(String.format("Amostra %d/%d - %s", run, config.runs(), datasetName));
                if (config.includeSerial()) {
                    WordCountResult res = serial.count(datasetName, text, config.word());
                    register(res, resultConsumer);
                }
                if (config.includeCpu()) {
                    for (Integer threads : config.threads()) {
                        if (threads == null || threads < 1) {
                            continue;
                        }
                        ParallelCpuCounter counter = new ParallelCpuCounter(threads);
                        try {
                            WordCountResult res = counter.count(datasetName, text, config.word());
                            register(res, resultConsumer);
                        } catch (Exception e) {
                            statusConsumer.accept("CPU (" + threads + "): erro " + e.getMessage());
                        }
                    }
                }
                if (config.includeGpu()) {
                    try {
                        WordCountResult res = gpu.count(datasetName, text, config.word());
                        register(res, resultConsumer);
                    } catch (Exception e) {
                        statusConsumer.accept("GPU indisponivel: " + e.getMessage());
                    }
                }
                if (config.includeGpuOpt()) {
                    try {
                        WordCountResult res = gpuOpt.count(datasetName, text, config.word());
                        register(res, resultConsumer);
                    } catch (Exception e) {
                        statusConsumer.accept("GPU Opt indisponivel: " + e.getMessage());
                    }
                }
                if (config.includeGpuOptCached()) {
                    try {
                        WordCountResult res = gpuOptCached.count(datasetName, text, config.word());
                        register(res, resultConsumer);
                    } catch (Exception e) {
                        statusConsumer.accept("GPU Opt Cached indisponivel: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void register(WordCountResult res, Consumer<WordCountResult> consumer) {
        history.add(res);
        consumer.accept(res);
    }

    public List<WordCountResult> historySnapshot() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
