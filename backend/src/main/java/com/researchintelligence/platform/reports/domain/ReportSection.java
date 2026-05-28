package com.researchintelligence.platform.reports.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum ReportSection {
    EXECUTIVE_SUMMARY("Resumen ejecutivo", "resumen ejecutivo", "resumen_ejecutivo", "summary"),
    PUBLICATION_OVERVIEW("Panorama de publicaciones", "produccion visible", "output"),
    YEARLY_EVOLUTION("Evolucion anual", "evolucion temporal", "trend", "tendencias"),
    TOP_TOPICS("Temas principales", "temas destacados", "topics"),
    LINKED_RESEARCHERS("Investigadores vinculados", "investigadores activos", "researchers"),
    LINKED_UNITS("Unidades vinculadas", "unidades activas", "units"),
    REPRESENTATIVE_PUBLICATIONS(
        "Publicaciones representativas",
        "publicaciones representativas",
        "publicaciones_representativas",
        "evidence"
    ),
    COLLABORATIONS("Colaboraciones", "colaboraciones", "collaboration"),
    DATA_QUALITY("Calidad de datos", "calidad de datos", "data quality"),
    VALIDATION_STATUS("Estado de validacion", "estado de validacion", "validation status"),
    OPPORTUNITIES("Oportunidades", "oportunidades"),
    LIMITATIONS("Limitaciones", "limitaciones del contexto", "context limitations"),
    CITED_EVIDENCE("Evidencia citada", "evidencia citada", "cited evidence"),
    MAIN_LINES("Lineas principales", "lineas principales", "lineas_principales"),
    INVOLVED_ACTORS(
        "Investigadores/unidades implicadas",
        "investigadores unidades implicadas",
        "investigadores_unidades_implicadas",
        "involved researchers and units"
    ),
    TRENDS("Tendencias", "tendencias"),
    CONTEXT_LIMITATIONS("Limitaciones del contexto", "limitaciones_del_contexto");

    private final String heading;
    private final Set<String> aliases;

    ReportSection(String heading, String... aliases) {
        this.heading = heading;
        this.aliases = new LinkedHashSet<>();
        this.aliases.add(normalize(name()));
        this.aliases.add(normalize(heading));
        Arrays.stream(aliases).map(ReportSection::normalize).forEach(this.aliases::add);
    }

    public String heading() {
        return heading;
    }

    public static Optional<ReportSection> fromApiValue(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(section -> section.aliases.contains(normalized))
            .findFirst();
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        String withoutDiacritics = decomposed.replaceAll("\\p{M}+", "");
        String underscored = withoutDiacritics.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return underscored.replaceAll("^_+|_+$", "");
    }
}
