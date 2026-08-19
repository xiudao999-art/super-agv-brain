package com.kunling.scheduling.action.compilation.domain;

public record CompileIssue(String code, Severity severity, String path, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static CompileIssue error(String code, String path, String message) {
        return new CompileIssue(code, Severity.ERROR, path, message);
    }

    public static CompileIssue warning(String code, String path, String message) {
        return new CompileIssue(code, Severity.WARNING, path, message);
    }
}
