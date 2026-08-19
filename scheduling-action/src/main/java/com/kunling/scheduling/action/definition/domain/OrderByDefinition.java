package com.kunling.scheduling.action.definition.domain;

public record OrderByDefinition(String property, SortDirection direction) {

    public OrderByDefinition {
        direction = direction == null ? SortDirection.ASCENDING : direction;
    }
}
