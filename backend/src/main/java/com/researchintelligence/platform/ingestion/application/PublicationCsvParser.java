package com.researchintelligence.platform.ingestion.application;

import com.researchintelligence.platform.shared.application.BusinessRuleException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PublicationCsvParser {

    private PublicationCsvParser() {
    }

    static List<CsvRecord> parse(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new BusinessRuleException("CSV file must include a header row.");
            }
            List<String> headers = parseLine(stripBom(headerLine)).stream()
                .map(header -> header.trim().toLowerCase(Locale.ROOT))
                .toList();
            validateHeaders(headers);

            List<CsvRecord> records = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseLine(line);
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String value = i < fields.size() ? fields.get(i) : "";
                    values.put(headers.get(i), value);
                }
                records.add(new CsvRecord(rowNumber, values));
            }
            return records;
        }
    }

    private static void validateHeaders(List<String> headers) {
        List<String> required = List.of("title", "abstracttext", "year", "type", "status", "doi", "source", "url", "authors", "topics");
        List<String> missing = required.stream()
            .filter(requiredHeader -> !headers.contains(requiredHeader))
            .toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("CSV file is missing required columns: " + String.join(", ", missing));
        }
    }

    private static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
