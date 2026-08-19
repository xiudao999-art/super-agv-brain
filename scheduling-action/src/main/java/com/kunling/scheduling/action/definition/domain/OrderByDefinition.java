package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class OrderByDefinition {
    String property;
    SortDirection direction;
    @ConstructorProperties({"property", "direction"})
    public OrderByDefinition(
            String property,
            SortDirection direction
    ) {
        direction = direction == null ? SortDirection.ASCENDING : direction;
        this.property = property;
        this.direction = direction;
    }
}
