package com.researchintelligence.platform.ingestion.application;

record PublicationAuthorDraft(
    Long researcherId,
    String externalAuthorName,
    String externalAffiliation,
    int authorOrder
) {
    String identityKey() {
        if (researcherId != null) {
            return "researcher:" + researcherId;
        }
        return "external:" + IngestionNormalizer.normalizeText(externalAuthorName);
    }
}
