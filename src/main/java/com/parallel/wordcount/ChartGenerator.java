package com.parallel.wordcount;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.style.Styler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChartGenerator {

    private ChartGenerator() {
    }

    public static void exportAverageDurationChart(List<WordCountResult> results, Path outputFile) throws IOException {
        if (results.isEmpty()) {
            return;
        }
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }

        Map<String, Map<String, Stats>> aggregated = new LinkedHashMap<>();
        Set<String> methods = new LinkedHashSet<>();
        for (WordCountResult r : results) {
            aggregated.computeIfAbsent(r.dataset(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(r.method(), k -> new Stats())
                    .add(r.durationMillis());
            methods.add(r.method());
        }

        List<String> datasets = new ArrayList<>(aggregated.keySet());

        CategoryChart chart = new CategoryChartBuilder()
                .width(1100)
                .height(650)
                .title("Tempo medio por dataset")
                .xAxisTitle("Dataset")
                .yAxisTitle("Tempo (ms)")
                .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setAvailableSpaceFill(0.8);

        for (String method : methods) {
            List<Double> series = new ArrayList<>();
            for (String dataset : datasets) {
                Stats stats = aggregated.get(dataset).get(method);
                series.add(stats == null ? 0.0 : stats.average());
            }
            chart.addSeries(method, datasets, series);
        }

        BitmapEncoder.saveBitmap(chart, outputFile.toString(), BitmapEncoder.BitmapFormat.PNG);
    }

    private static class Stats {
        private long total = 0;
        private int count = 0;

        void add(long value) {
            total += value;
            count++;
        }

        double average() {
            return count == 0 ? 0 : (double) total / count;
        }
    }
}
