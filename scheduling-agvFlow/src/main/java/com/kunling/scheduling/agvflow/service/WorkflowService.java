package com.kunling.scheduling.agvflow.service;

import com.kunling.workflow.queenDemo.domain.NodeInstance;
import com.kunling.workflow.queenDemo.domain.WorkflowDefinition;
import com.kunling.workflow.queenDemo.domain.WorkflowInstance;
import com.kunling.workflow.queenDemo.repository.WorkflowStore;
import com.kunling.workflow.queenDemo.service.NodeScheduler;

import java.util.List;

public final class WorkflowService {
    private final WorkflowStore store;
    private final NodeScheduler scheduler;

    public WorkflowService(WorkflowStore store, NodeScheduler scheduler) {
        this.store = store;
        this.scheduler = scheduler;
    }

    public void createDefinition(WorkflowDefinition definition) {
        store.saveDefinition(definition);
        store.addStateLog("保存流程定义: " + definition.workflowCode());
    }

    public long start(String workflowCode, String businessKey) {
        WorkflowInstance instance = store.createInstance(workflowCode, businessKey);
        store.createNodeInstances(instance.id());
        instance.markRunning();
        store.addStateLog("流程进入RUNNING: workflow=" + instance.id());

        List<NodeInstance> startNodes = store.findStartNodes(instance.id());
        for (NodeInstance startNode : startNodes) {
            scheduler.enqueue(startNode.id());
        }
        return instance.id();
    }
}
