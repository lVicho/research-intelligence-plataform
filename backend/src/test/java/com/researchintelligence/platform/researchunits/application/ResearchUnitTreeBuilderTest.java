package com.researchintelligence.platform.researchunits.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchintelligence.platform.researchunits.api.ResearchUnitTreeNode;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchUnitTreeBuilderTest {

    private final ResearchUnitTreeBuilder builder = new ResearchUnitTreeBuilder();

    @Test
    void buildsNestedResearchUnitTree() {
        ResearchUnitEntity university = unit(1L, "Northbridge University", null, ResearchUnitType.UNIVERSITY);
        ResearchUnitEntity faculty = unit(2L, "Faculty of Health Sciences", 1L, ResearchUnitType.FACULTY);
        ResearchUnitEntity department = unit(3L, "Department of Biomedical Informatics", 2L, ResearchUnitType.DEPARTMENT);
        ResearchUnitEntity hospital = unit(4L, "Riverbend Medical Research Hospital", null, ResearchUnitType.HOSPITAL);

        List<ResearchUnitTreeNode> tree = builder.build(List.of(department, hospital, faculty, university));

        assertEquals(2, tree.size());
        assertEquals("Northbridge University", tree.get(0).name());
        assertEquals("Faculty of Health Sciences", tree.get(0).children().getFirst().name());
        assertEquals("Department of Biomedical Informatics", tree.get(0).children().getFirst().children().getFirst().name());
        assertEquals("Riverbend Medical Research Hospital", tree.get(1).name());
    }

    private ResearchUnitEntity unit(Long id, String name, Long parentId, ResearchUnitType type) {
        ResearchUnitEntity entity = new ResearchUnitEntity(name, null, type, parentId, "United States", "Boston", null, true);
        entity.setId(id);
        return entity;
    }
}
