package com.parallel.wordcount.ui;

import com.parallel.wordcount.ChartGenerator;
import com.parallel.wordcount.CsvExporter;
import com.parallel.wordcount.WordCountResult;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WordCountFrame extends JFrame {

    private final UiBenchmarkExecutor executor;
    private final PerformanceChartPanel chartPanel = new PerformanceChartPanel();
    private final ResultTableModel tableModel = new ResultTableModel();
    private final DefaultListModel<Path> datasetModel = new DefaultListModel<>();

    private JTextField wordField;
    private JSpinner runsSpinner;
    private JTextField threadsField;
    private JCheckBox serialBox;
    private JCheckBox cpuBox;
    private JCheckBox gpuBox;
    private JCheckBox gpuOptBox;
    private JCheckBox gpuOptCachedBox;
    private JButton runButton;
    private JButton exportButton;
    private JButton clearButton;
    private JLabel statusLabel;

    public WordCountFrame(UiBenchmarkExecutor executor) {
        super("WordCount Paralelo - Dashboard");
        this.executor = executor;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        add(buildTopPanel(), BorderLayout.NORTH);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        centerSplit.setTopComponent(buildChartPanel());
        centerSplit.setBottomComponent(buildTablePanel());
        centerSplit.setResizeWeight(0.55);
        centerSplit.setContinuousLayout(true);
        centerSplit.setBorder(BorderFactory.createEmptyBorder());
        add(centerSplit, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1080, 760));
    }

    private JPanel buildTopPanel() {
        JPanel top = new JPanel(new BorderLayout(6, 6));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildControlPanel(), buildDatasetsPanel());
        split.setResizeWeight(0.7);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        top.add(split, BorderLayout.CENTER);

        statusLabel = new JLabel("Configure e clique em Executar.", SwingConstants.LEFT);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(0xECEFF1));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        top.add(statusLabel, BorderLayout.SOUTH);
        return top;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Configuracao de execucao"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        wordField = new JTextField("paralelismo", 16);
        runsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
        threadsField = new JTextField("2,4", 10);

        serialBox = new JCheckBox("Serial", true);
        cpuBox = new JCheckBox("Parallel CPU", true);
        gpuBox = new JCheckBox("Parallel GPU", true);
        gpuOptBox = new JCheckBox("Parallel GPU Optimized", true);
        gpuOptCachedBox = new JCheckBox("Parallel GPU Optimized (cached text)", true);

        runButton = new JButton("Executar");
        exportButton = new JButton("Exportar CSV + Grafico");
        clearButton = new JButton("Limpar");

        runButton.addActionListener(e -> triggerRun());
        exportButton.addActionListener(e -> exportResults());
        clearButton.addActionListener(e -> clearView());

        int row = 0;
        addRow(panel, gbc, row++, "Palavra alvo:", wordField);
        addRow(panel, gbc, row++, "Amostras:", runsSpinner);
        addRow(panel, gbc, row++, "Threads CPU (lista):", threadsField);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        panel.add(serialBox, gbc);
        gbc.gridy = row + 1;
        panel.add(cpuBox, gbc);
        gbc.gridy = row + 2;
        panel.add(gpuBox, gbc);
        gbc.gridy = row + 3;
        panel.add(gpuOptBox, gbc);
        gbc.gridy = row + 4;
        panel.add(gpuOptCachedBox, gbc);

        gbc.gridy = row + 5;
        gbc.gridwidth = 1;
        panel.add(runButton, gbc);
        gbc.gridx = 1;
        panel.add(exportButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = row + 6;
        gbc.gridwidth = 2;
        panel.add(clearButton, gbc);

        return panel;
    }

    private JPanel buildDatasetsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Datasets"));

        JList<Path> list = new JList<>(datasetModel);
        list.setVisibleRowCount(8);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(280, 160));

        JButton addButton = new JButton("Adicionar...");
        JButton removeButton = new JButton("Remover");

        addButton.addActionListener(e -> chooseDatasets());
        removeButton.addActionListener(e -> {
            List<Path> selected = list.getSelectedValuesList();
            selected.forEach(datasetModel::removeElement);
        });

        JPanel buttons = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        buttons.add(addButton, gbc);
        gbc.gridy = 1;
        buttons.add(removeButton, gbc);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        preloadDatasets();
        return panel;
    }

    private JScrollPane buildChartPanel() {
        JScrollPane scroll = new JScrollPane(chartPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Tempo medio por dataset/metodo"));
        scroll.setPreferredSize(new Dimension(880, 360));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        return scroll;
    }

    private JScrollPane buildTablePanel() {
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(880, 200));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Ultimos resultados"));
        return scroll;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, java.awt.Component component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, gbc);
    }

    private void triggerRun() {
        UiConfig config = buildConfig();
        if (config == null) {
            return;
        }
        setControlsEnabled(false);
        executor.execute(
                config,
                this::updateStatus,
                this::handleResult,
                () -> {},
                () -> SwingUtilities.invokeLater(() -> setControlsEnabled(true))
        );
    }

    private UiConfig buildConfig() {
        String word = wordField.getText().trim();
        if (word.isEmpty()) {
            updateStatus("Informe uma palavra alvo.");
            return null;
        }

        List<Path> datasets = new ArrayList<>();
        for (int i = 0; i < datasetModel.size(); i++) {
            datasets.add(datasetModel.get(i));
        }
        if (datasets.isEmpty()) {
            updateStatus("Adicione pelo menos um dataset.");
            return null;
        }

        int runs = ((Number) runsSpinner.getValue()).intValue();
        List<Integer> threads = parseThreads(threadsField.getText());
        if (cpuBox.isSelected() && threads.isEmpty()) {
            updateStatus("Informe ao menos um valor de threads para Parallel CPU.");
            return null;
        }

        return new UiConfig(
                datasets,
                word,
                runs,
                threads,
                serialBox.isSelected(),
                cpuBox.isSelected(),
                gpuBox.isSelected(),
                gpuOptBox.isSelected(),
                gpuOptCachedBox.isSelected()
        );
    }

    private List<Integer> parseThreads(String text) {
        List<Integer> values = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        String[] parts = text.split(",");
        Set<Integer> seen = new HashSet<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            try {
                int v = Integer.parseInt(trimmed);
                if (v > 0 && seen.add(v)) {
                    values.add(v);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        values.sort(Integer::compareTo);
        return values;
    }

    private void handleResult(WordCountResult result) {
        SwingUtilities.invokeLater(() -> {
            chartPanel.registerResult(result);
            tableModel.addResult(result);
            statusLabel.setText(describeResult(result));
        });
    }

    private String describeResult(WordCountResult result) {
        String threads = result.threads() != null ? result.threads() + "t" : "";
        String device = result.deviceType() != null ? result.deviceType() : "";
        String suffix = (!threads.isEmpty() ? " | " + threads : "") + (!device.isEmpty() ? " | " + device : "");
        return String.format("%s | %s%s | %d ocorrencias | %d ms",
                result.dataset(), result.method(), suffix, result.occurrences(), result.durationMillis());
    }

    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    private void clearView() {
        chartPanel.clearChart();
        tableModel.clear();
        executor.clearHistory();
        updateStatus("Visualizacao limpa.");
    }

    private void exportResults() {
        List<WordCountResult> snapshot = executor.historySnapshot();
        if (snapshot.isEmpty()) {
            updateStatus("Nenhum resultado para exportar.");
            return;
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        Path csv = Paths.get("results", "wordcount_ui_" + timestamp + ".csv");
        Path chart = Paths.get("results", "wordcount_ui_" + timestamp + ".png");
        try {
            CsvExporter.write(csv, snapshot);
            ChartGenerator.exportAverageDurationChart(snapshot, chart);
            updateStatus("Exportado: " + csv + " e " + chart);
        } catch (Exception e) {
            updateStatus("Erro ao exportar: " + e.getMessage());
        }
    }

    private void setControlsEnabled(boolean enabled) {
        runButton.setEnabled(enabled);
        exportButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
    }

    private void chooseDatasets() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (var file : chooser.getSelectedFiles()) {
                Path p = file.toPath();
                if (!datasetModel.contains(p)) {
                    datasetModel.addElement(p);
                }
            }
        }
    }

    private void preloadDatasets() {
        addDatasetIfExists(Paths.get("data", "sample_small.txt"));
        addDatasetIfExists(Paths.get("data", "sample_medium.txt"));
        addDatasetIfExists(Paths.get("data", "sample_large.txt"));
        addDatasetIfExists(Paths.get("data", "sample_huge.txt"));
        addDatasetIfExists(Paths.get("data", "sample_mega.txt"));
    }

    private void addDatasetIfExists(Path path) {
        if (Files.exists(path)) {
            datasetModel.addElement(path);
        }
    }
}
