package com.businessbuddy.util;

import java.util.List;

/**
 * Builds CSV text safely (4.6.11).
 *
 * <p>Every value is quoted, quotes are doubled, and a value that starts with =, +, - or @
 * is prefixed with an apostrophe so a spreadsheet does not run it as a formula
 * (CSV formula injection, Table 4.51). Numbers are written without the prefix because
 * they are passed as numbers, not text.</p>
 */
public final class CsvWriter {

    private final StringBuilder out = new StringBuilder();

    public CsvWriter row(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(values.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    public CsvWriter row(Object... values) {
        return row(List.of(values));
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
