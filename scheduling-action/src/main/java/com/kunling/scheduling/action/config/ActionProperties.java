package com.kunling.scheduling.action.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Action 模块的编译安全限制。 */
@ConfigurationProperties(prefix = "kunling.action")
public record ActionProperties(Compiler compiler) {

    public ActionProperties {
        compiler = compiler == null ? new Compiler(8, 500, 6, 524_288) : compiler;
    }

    public record Compiler(int maximumActionDepth, int maximumCompiledNodes,
                           int maximumForEachIterations, int maximumPlanBytes) {
    }
}
