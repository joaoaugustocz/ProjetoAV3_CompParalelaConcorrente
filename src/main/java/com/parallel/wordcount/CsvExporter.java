package com.parallel.wordcount;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvExporter {

    private CsvExporter() {
    }

    public static void write(Path path, List<WordCountResult> results) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        List<String> lines = new java.util.ArrayList<>();
        lines.add("method,dataset,occurrences,duration_ms,threads,device");
        for (WordCountResult r : results) {
            lines.add(String.join(",",
                    r.method(),
                    r.dataset(),
                    String.valueOf(r.occurrences()),
                    String.valueOf(r.durationMillis()),
                    r.threads() == null ? "" : String.valueOf(r.threads()),
                    sanitize(r.deviceType())));
        }
        Files.write(path, lines);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", " ");
    }
}
