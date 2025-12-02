package com.parallel.wordcount.ui;

import com.parallel.wordcount.WordCountResult;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Tabela de resultados recentes (buffer limitado).
 */
public class ResultTableModel extends AbstractTableModel {

    private static final int LIMIT = 200;

    private final String[] columns = {
            "#",
            "Dataset",
            "Metodo",
            "Threads",
            "Device",
            "Tempo (ms)",
            "Ocorrencias"
    };

    private final Deque<Row> rows = new ArrayDeque<>();
    private int sequence = 1;

    public void addResult(WordCountResult result) {
        if (rows.size() == LIMIT) {
            rows.removeFirst();
        }
        rows.addLast(new Row(sequence++, result));
        fireTableDataChanged();
    }

    public void clear() {
        if (!rows.isEmpty()) {
            rows.clear();
            fireTableDataChanged();
        }
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row row = rowAt(rowIndex);
        if (row == null) {
            return "";
        }
        WordCountResult r = row.result();
        return switch (columnIndex) {
            case 0 -> row.index();
            case 1 -> r.dataset();
            case 2 -> r.method();
            case 3 -> r.threads() != null ? r.threads() : "";
            case 4 -> r.deviceType() != null ? r.deviceType() : "";
            case 5 -> r.durationMillis();
            case 6 -> r.occurrences();
            default -> "";
        };
    }

    private Row rowAt(int idx) {
        if (idx < 0 || idx >= rows.size()) {
            return null;
        }
        Iterator<Row> it = rows.iterator();
        for (int i = 0; it.hasNext(); i++) {
            Row r = it.next();
            if (i == idx) {
                return r;
            }
        }
        return null;
    }

    private record Row(int index, WordCountResult result) {
    }
}
