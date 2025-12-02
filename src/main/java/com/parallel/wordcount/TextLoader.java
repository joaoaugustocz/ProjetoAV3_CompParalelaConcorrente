package com.parallel.wordcount;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TextLoader {

    private TextLoader() {
    }

    public static String load(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
