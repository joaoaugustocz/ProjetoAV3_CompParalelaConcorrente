package com.parallel.wordcount;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simple CLI runner for benchmarking the counting strategies.
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);
        if (config.help) {
            printUsage();
            return;
        }
        validate(config);

        SerialCpuCounter serial = new SerialCpuCounter();
        ParallelGpuCounter gpu = new ParallelGpuCounter();

        List<WordCountResult> results = new ArrayList<>();

        for (Path input : config.inputs) {
            String datasetName = input.getFileName().toString();
            String text = TextLoader.load(input);
            System.out.println("\nDataset: " + datasetName + " (" + text.length() + " chars)");

            for (int run = 1; run <= config.runs; run++) {
                System.out.println("  Run " + run + "/" + config.runs);
                results.add(serial.count(datasetName, text, config.word));

                for (int threads : config.threadOptions) {
                    ParallelCpuCounter parallelCpuCounter = new ParallelCpuCounter(threads);
                    results.add(parallelCpuCounter.count(datasetName, text, config.word));
                }

                if (!config.skipGpu) {
                    try {
                        results.add(gpu.count(datasetName, text, config.word));
                    } catch (Exception ex) {
                        System.err.println("    GPU run skipped: " + ex.getMessage());
                    }
                }
            }
        }

        Path csvPath = config.csvOutput != null ? config.csvOutput : defaultCsvPath();
        CsvExporter.write(csvPath, results);
        System.out.println("\nCSV salvo em: " + csvPath.toAbsolutePath());

        if (config.chartOutput != null) {
            ChartGenerator.exportAverageDurationChart(results, config.chartOutput);
            System.out.println("Grafico salvo em: " + config.chartOutput.toAbsolutePath());
        }
    }

    private static void validate(Config config) {
        if (config.word == null || config.word.isBlank()) {
            throw new IllegalArgumentException("Informe a palavra alvo com --word <palavra>");
        }
        for (Path input : config.inputs) {
            if (!Files.exists(input)) {
                throw new IllegalArgumentException("Arquivo de entrada nao encontrado: " + input);
            }
        }
    }

    private static Path defaultCsvPath() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        return Paths.get("results", "wordcount_" + timestamp + ".csv");
    }

    private static void printUsage() {
        System.out.println("""
                Uso:
                  java -jar target/wordcount-parallel-1.0.0-jar-with-dependencies.jar --word termo
                      --inputs data/sample_small.txt,data/sample_medium.txt,data/sample_large.txt
                      [--runs 3] [--threads 2,4,8] [--csv results/out.csv] [--chart results/out.png] [--skip-gpu]

                Opcoes:
                  --word <palavra>           Palavra alvo a ser contada (obrigatorio)
                  --inputs <lista>           Lista de arquivos separados por virgula (padrao: amostras em data/)
                  --runs <n>                 Numero de execucoes repetidas por dataset (padrao: 3)
                  --threads <lista>          Quantidade de threads para a versao paralela na CPU (padrao: nucleos disponiveis)
                  --csv <arquivo>            Caminho do CSV de saida (padrao: results/wordcount_TIMESTAMP.csv)
                  --chart <arquivo>          Caminho do grafico PNG com tempos medios (opcional)
                  --skip-gpu                 Nao executar a versao GPU (util se nao houver driver OpenCL)
                  --help                     Exibe esta mensagem
                """);
    }

    private record Config(
            String word,
            List<Path> inputs,
            int runs,
            List<Integer> threadOptions,
            Path csvOutput,
            Path chartOutput,
            boolean skipGpu,
            boolean help) {

        static Config fromArgs(String[] args) {
            List<Path> inputs = new ArrayList<>();
            String word = null;
            int runs = 3;
            List<Integer> threads = null;
            Path csv = null;
            Path chart = null;
            boolean skipGpu = false;
            boolean help = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--word" -> word = valueAt(args, ++i);
                    case "--inputs" -> inputs = parsePaths(valueAt(args, ++i));
                    case "--runs" -> runs = Integer.parseInt(valueAt(args, ++i));
                    case "--threads" -> threads = parseIntegers(valueAt(args, ++i));
                    case "--csv" -> csv = Paths.get(valueAt(args, ++i));
                    case "--chart" -> chart = Paths.get(valueAt(args, ++i));
                    case "--skip-gpu" -> skipGpu = true;
                    case "--help" -> help = true;
                    default -> throw new IllegalArgumentException("Opcao desconhecida: " + args[i]);
                }
            }

            if (threads == null) {
                threads = List.of(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
            }
            if (inputs.isEmpty()) {
                inputs = List.of(
                        Paths.get("data", "sample_small.txt"),
                        Paths.get("data", "sample_medium.txt"),
                        Paths.get("data", "sample_large.txt"));
            }
            if (chart == null) {
                chart = Paths.get("results", "wordcount_chart.png");
            }

            return new Config(word, inputs, runs, threads, csv, chart, skipGpu, help);
        }

        private static String valueAt(String[] args, int idx) {
            if (idx >= args.length) {
                throw new IllegalArgumentException("Valor esperado apos " + args[idx - 1]);
            }
            return args[idx];
        }

        private static List<Path> parsePaths(String raw) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Paths::get)
                    .toList();
        }

        private static List<Integer> parseIntegers(String raw) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
        }
    }
}
