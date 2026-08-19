package com.kunling.scheduling.agvflow.controller;

import com.kunling.scheduling.agvflow.domain.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.agvflow.domain.dto.FlowTemplateDetail;

import javax.validation.Valid;

import com.kunling.scheduling.agvflow.service.FlowTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/flow-templates")
public class FlowTemplateController {
    private final FlowTemplateService templateService;

    public FlowTemplateController(FlowTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> create(@Valid @RequestBody FlowTemplateCreateRequest request) {
        return java.util.Collections.singletonMap("id", templateService.createTemplate(request));
    }

    @GetMapping("/{id}")
    public FlowTemplateDetail detail(@PathVariable Integer id) {
        return templateService.getTemplateDetail(id);
    }
}
