package com.parallel.wordcount.ui;

import com.parallel.wordcount.WordCountResult;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Painel customizado inspirado no projeto AV2 para exibir barras animadas com tempos medios.
 */
public class PerformanceChartPanel extends JPanel {

    private static final int AXIS_HEIGHT = 40;
    private static final int MIN_BAR_WIDTH = 70;
    private static final int BAR_SPACING = 20;
    private static final int BASE_WIDTH = 860;
    private static final int MIN_HEIGHT = 420;
    private static final int BOTTOM_PADDING = 90;
    private static final int TOP_PADDING = 30;

    private final Map<String, GrowingStats> stats = new LinkedHashMap<>();
    private final Map<String, Double> displayed = new LinkedHashMap<>();
    private final Timer animator;

    public PerformanceChartPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(BASE_WIDTH, MIN_HEIGHT));
        animator = new Timer(30, e -> updateAnimation());
        animator.setRepeats(true);
    }

    public void registerResult(WordCountResult result) {
        String key = labelFor(result);
        GrowingStats s = stats.computeIfAbsent(key, k -> new GrowingStats());
        s.add(result.durationMillis());
        displayed.putIfAbsent(key, 0.0);
        updatePreferredWidth();
        if (!animator.isRunning()) {
            animator.start();
        }
        repaint();
    }

    public void clearChart() {
        animator.stop();
        stats.clear();
        displayed.clear();
        setPreferredSize(new Dimension(BASE_WIDTH, MIN_HEIGHT));
        revalidate();
        repaint();
    }

    private void updateAnimation() {
        boolean keepRunning = false;
        for (Map.Entry<String, GrowingStats> entry : stats.entrySet()) {
            String key = entry.getKey();
            double target = entry.getValue().average();
            double current = displayed.getOrDefault(key, 0.0);
            double next = current + (target - current) * 0.15;
            displayed.put(key, next);
            if (Math.abs(next - target) > 0.5) {
                keepRunning = true;
            }
        }
        if (!keepRunning) {
            animator.stop();
        }
        repaint();
    }

    private void updatePreferredWidth() {
        int count = Math.max(displayed.size(), 1);
        int width = 140 + count * (MIN_BAR_WIDTH + BAR_SPACING);
        int finalWidth = Math.max(BASE_WIDTH, width);
        if (getPreferredSize().width != finalWidth) {
            setPreferredSize(new Dimension(finalWidth, MIN_HEIGHT));
            revalidate();
        }
    }

    private String labelFor(WordCountResult r) {
        String suffix = "";
        if ("ParallelCPU".equals(r.method()) && r.threads() != null) {
            suffix = " (" + r.threads() + "t)";
        } else if ("ParallelGPU".equals(r.method()) && r.deviceType() != null) {
            suffix = " (" + r.deviceType() + ")";
        }
        return r.dataset() + " | " + r.method() + suffix;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int baseY = getHeight() - AXIS_HEIGHT - BOTTOM_PADDING;

        g2d.setColor(Color.GRAY);
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawLine(40, baseY, width - 20, baseY);

        if (displayed.isEmpty()) {
            g2d.setColor(Color.DARK_GRAY);
            g2d.setFont(getFont().deriveFont(Font.BOLD, 16f));
            g2d.drawString("Resultados aparecerao aqui.", 60, baseY - 20);
            g2d.dispose();
            return;
        }

        double maxValue = displayed.values().stream().mapToDouble(Double::doubleValue).max().orElse(1d);
        int count = displayed.size();
        int available = width - 60;
        int barWidth = Math.max(MIN_BAR_WIDTH, (available - (count - 1) * BAR_SPACING) / Math.max(1, count));
        int availableHeight = Math.max(60, baseY - TOP_PADDING);

        int x = 50;
        int colorIndex = 0;
        for (Map.Entry<String, Double> entry : displayed.entrySet()) {
            String key = entry.getKey();
            double value = entry.getValue();
            int barHeight = (int) ((value / maxValue) * availableHeight);
            int y = baseY - barHeight;
            if (y < TOP_PADDING) {
                y = TOP_PADDING;
                barHeight = baseY - y;
            }

            g2d.setColor(palette(colorIndex++));
            g2d.fillRoundRect(x, y, barWidth, Math.max(5, barHeight), 12, 12);

            g2d.setColor(Color.DARK_GRAY);
            g2d.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2d.drawString(String.format("%.1f ms", value), x + 4, Math.max(TOP_PADDING + 12, y - 8));

            g2d.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            drawMultiline(g2d, key, x, baseY + 25, barWidth);

            x += barWidth + BAR_SPACING;
        }

        g2d.dispose();
    }

    private void drawMultiline(Graphics2D g2d, String text, int x, int y, int maxWidth) {
        String[] parts = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineHeight = g2d.getFontMetrics().getHeight();
        int yCursor = y;

        for (String part : parts) {
            String next = line.length() == 0 ? part : line + " " + part;
            if (g2d.getFontMetrics().stringWidth(next) > maxWidth) {
                g2d.drawString(line.toString(), x, yCursor);
                yCursor += lineHeight;
                line = new StringBuilder(part);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            g2d.drawString(line.toString(), x, yCursor);
        }
    }

    private Color palette(int idx) {
        Color[] colors = {
                new Color(0x4CAF50),
                new Color(0x2196F3),
                new Color(0xFF9800),
                new Color(0x9C27B0),
                new Color(0xF44336),
                new Color(0x009688),
                new Color(0x3F51B5),
                new Color(0x795548)
        };
        return colors[idx % colors.length];
    }

    private static class GrowingStats {
        private long sum;
        private long count;

        void add(long value) {
            sum += value;
            count++;
        }

        double average() {
            if (count == 0) {
                return 0;
            }
            return (double) sum / count;
        }
    }
}
