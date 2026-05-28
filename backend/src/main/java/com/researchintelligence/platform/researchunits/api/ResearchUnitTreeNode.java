package com.researchintelligence.platform.researchunits.api;

import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import java.util.List;

public record ResearchUnitTreeNode(
    Long id,
    String name,
    String shortName,
    ResearchUnitType type,
    Long parentId,
    boolean active,
    List<ResearchUnitTreeNode> children
) {
}
