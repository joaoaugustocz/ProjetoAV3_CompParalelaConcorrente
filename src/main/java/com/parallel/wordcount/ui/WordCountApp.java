package com.parallel.wordcount.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Inicializa a interface Swing do projeto de contagem de palavras.
 */
public class WordCountApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            configurarLookAndFeel();
            UiBenchmarkExecutor executor = new UiBenchmarkExecutor();
            WordCountFrame frame = new WordCountFrame(executor);
            frame.setVisible(true);
        });
    }

    private static void configurarLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }
}
