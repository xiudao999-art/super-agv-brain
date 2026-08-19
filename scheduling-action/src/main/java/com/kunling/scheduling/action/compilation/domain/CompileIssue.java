package com.kunling.scheduling.action.compilation.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CompileIssue {
    String code;
    Severity severity;
    String path;
    String message;
    @ConstructorProperties({"code", "severity", "path", "message"})
    public CompileIssue(
            String code,
            Severity severity,
            String path,
            String message
    ) {
        this.code = code;
        this.severity = severity;
        this.path = path;
        this.message = message;
    }


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
