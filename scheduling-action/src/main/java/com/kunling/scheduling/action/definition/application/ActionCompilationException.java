package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.compilation.domain.CompileIssue;

import java.util.List;

public class ActionCompilationException extends RuntimeException {

    private final List<CompileIssue> issues;

    public ActionCompilationException(List<CompileIssue> issues) {
        super("Action 编译失败");
        this.issues = List.copyOf(issues);
    }

    public List<CompileIssue> getIssues() {
        return issues;
    }
}
