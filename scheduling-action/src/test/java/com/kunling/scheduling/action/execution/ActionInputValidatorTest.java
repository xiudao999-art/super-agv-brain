package com.kunling.scheduling.action.execution;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import com.kunling.scheduling.action.definition.domain.ParameterType;
import com.kunling.scheduling.action.execution.application.ActionInputValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionInputValidatorTest {

    private final JsonMapper objectMapper = new JsonMapper();
    private final ActionInputValidator validator = new ActionInputValidator();
    private final Map<String, ParameterSchema> schema = Map.of(
            "slots", new ParameterSchema(ParameterType.ARRAY, true, null, List.of(), Map.of(),
                    new ParameterSchema(ParameterType.OBJECT, true, null, List.of(), Map.of(
                            "slotId", new ParameterSchema(ParameterType.STRING, true, null,
                                    List.of("A", "B"), Map.of(), null)), null)));

    @Test
    void recursivelyValidatesObjectsArraysAndEnums() throws Exception {
        assertThatNoException().isThrownBy(() -> validator.validate(
                objectMapper.readTree("{\"slots\":[{\"slotId\":\"A\"}]}"), schema));
        assertThatThrownBy(() -> validator.validate(
                objectMapper.readTree("{\"slots\":[{\"slotId\":\"C\"}]}"), schema))
                .hasMessageContaining("不在允许值");
        assertThatThrownBy(() -> validator.validate(
                objectMapper.readTree("{\"slots\":[],\"unexpected\":true}"), schema))
                .hasMessageContaining("未声明参数");
    }
}
