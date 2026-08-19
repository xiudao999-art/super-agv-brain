package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Component
public class ActionDefinitionDiffer {

    private final JsonCodec jsonCodec;

    public ActionDefinitionDiffer(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public List<ActionChange> compare(Object before, Object after) {
        List<ActionChange> changes = new ArrayList<>();
        walk("$", jsonCodec.readTree(jsonCodec.write(before)), jsonCodec.readTree(jsonCodec.write(after)), changes);
        return ImmutableCollections.copyList(changes);
    }

    private void walk(String path, JsonNode before, JsonNode after, List<ActionChange> changes) {
        if (before == null || before.isMissingNode()) {
            changes.add(new ActionChange(path, ActionChange.ChangeKind.ADDED, null, value(after), risk(path)));
            return;
        }
        if (after == null || after.isMissingNode()) {
            changes.add(new ActionChange(path, ActionChange.ChangeKind.REMOVED, value(before), null, risk(path)));
            return;
        }
        if (before.equals(after)) {
            return;
        }
        if (before.isObject() && after.isObject()) {
            TreeSet<String> names = new TreeSet<>();
            before.fieldNames().forEachRemaining(names::add);
            after.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                walk(path + "." + name, before.path(name), after.path(name), changes);
            }
            return;
        }
        if (before.isArray() && after.isArray()) {
            int maximum = Math.max(before.size(), after.size());
            for (int index = 0; index < maximum; index++) {
                walk(path + "[" + index + "]", before.path(index), after.path(index), changes);
            }
            return;
        }
        changes.add(new ActionChange(path, ActionChange.ChangeKind.MODIFIED, value(before), value(after), risk(path)));
    }

    private String value(JsonNode node) {
        return node == null || node.isMissingNode() ? null : node.toString();
    }

    private ActionChange.ChangeRisk risk(String path) {
        if (path.contains("steps") || path.contains("entryPoint") || path.contains("onFailure")) {
            return ActionChange.ChangeRisk.HIGH;
        }
        if (path.contains("inputSchema") || path.contains("defaultPolicy")) {
            return ActionChange.ChangeRisk.MEDIUM;
        }
        return ActionChange.ChangeRisk.NORMAL;
    }
}
