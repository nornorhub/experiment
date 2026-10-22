package com.nornorhub.ghostbus.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Streaming reader for GTFS CSV files (RFC 4180 quoting, optional UTF-8 BOM). */
public final class Csv {

    private final BufferedReader in;
    private final Map<String, Integer> columns = new HashMap<>();

    public Csv(InputStream stream) throws IOException {
        in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        List<String> header = readRecord();
        if (header == null) {
            return;
        }
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).trim();
            if (i == 0 && name.startsWith("﻿")) {
                name = name.substring(1);
            }
            columns.put(name, i);
        }
    }

    /** Index of the named column, or -1 if the file doesn't have it. */
    public int column(String name) {
        Integer i = columns.get(name);
        return i == null ? -1 : i;
    }

    /** Next row, or null at end of file. */
    public List<String> next() throws IOException {
        List<String> row;
        do {
            row = readRecord();
        } while (row != null && row.size() == 1 && row.get(0).isEmpty());
        return row;
    }

    public static String get(List<String> row, int column) {
        return column >= 0 && column < row.size() ? row.get(column).trim() : "";
    }

    private List<String> readRecord() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        while (true) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (quoted) {
                    if (c == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            field.append('"');
                            i++;
                        } else {
                            quoted = false;
                        }
                    } else {
                        field.append(c);
                    }
                } else if (c == '"') {
                    quoted = true;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                } else {
                    field.append(c);
                }
            }
            if (!quoted) {
                break;
            }
            // A quoted field contains a line break: keep reading.
            String more = in.readLine();
            if (more == null) {
                break;
            }
            field.append('\n');
            line = more;
        }
        fields.add(field.toString());
        return fields;
    }
}
