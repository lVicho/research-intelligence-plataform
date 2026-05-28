package com.researchintelligence.platform.researchunits.application;

import com.researchintelligence.platform.researchunits.api.ResearchUnitTreeNode;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ResearchUnitTreeBuilder {

    public List<ResearchUnitTreeNode> build(List<ResearchUnitEntity> units) {
        Map<Long, MutableNode> nodesById = new HashMap<>();
        for (ResearchUnitEntity unit : units) {
            nodesById.put(unit.getId(), new MutableNode(unit));
        }

        List<MutableNode> roots = new ArrayList<>();
        for (MutableNode node : nodesById.values()) {
            Long parentId = node.unit.getParentId();
            MutableNode parent = parentId == null ? null : nodesById.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }

        roots.sort(Comparator.comparing(node -> node.unit.getName(), String.CASE_INSENSITIVE_ORDER));
        return roots.stream().map(this::toTreeNode).toList();
    }

    private ResearchUnitTreeNode toTreeNode(MutableNode node) {
        node.children.sort(Comparator.comparing(child -> child.unit.getName(), String.CASE_INSENSITIVE_ORDER));
        return new ResearchUnitTreeNode(
            node.unit.getId(),
            node.unit.getName(),
            node.unit.getShortName(),
            node.unit.getType(),
            node.unit.getParentId(),
            node.unit.isActive(),
            node.children.stream().map(this::toTreeNode).toList()
        );
    }

    private static final class MutableNode {
        private final ResearchUnitEntity unit;
        private final List<MutableNode> children = new ArrayList<>();

        private MutableNode(ResearchUnitEntity unit) {
            this.unit = unit;
        }
    }
}
