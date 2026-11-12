package com.marketpos.app;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes a report to a CSV file in the app cache, ready to upload to Google Drive. */
final class ReportExport {

    private ReportExport() {
    }

    static File writeCsv(Context context, String title, String from, String to,
                         String[] header, List<String[]> rows) throws IOException {
        File dir = new File(context.getCacheDir(), "reports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }

        String name = safe(title) + "_" + from.replace('/', '-') + "_" + to.replace('/', '-') + ".csv";
        File out = new File(dir, name);

        try (Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM so Excel shows Arabic correctly
            writeLine(w, header);
            for (String[] row : rows) {
                writeLine(w, row);
            }
        }
        return out;
    }

    private static void writeLine(Writer w, String[] cells) throws IOException {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write(quote(cells[i]));
        }
        w.write("\r\n");
    }

    private static String quote(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }

    private static String safe(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
