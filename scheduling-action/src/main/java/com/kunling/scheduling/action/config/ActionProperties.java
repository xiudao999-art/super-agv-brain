package com.kunling.scheduling.action.config;

/** Action 编译器的只读安全限制。 */
public final class ActionProperties {

    private final Compiler compiler;

    public ActionProperties(Compiler compiler) {
        this.compiler = compiler == null ? Compiler.defaults() : compiler;
    }

    public Compiler compiler() {
        return compiler;
    }

    /** 编译阶段的资源与复杂度上限。 */
    public static final class Compiler {

        private final int maximumActionDepth;
        private final int maximumCompiledNodes;
        private final int maximumForEachIterations;
        private final int maximumPlanBytes;

        public Compiler(int maximumActionDepth, int maximumCompiledNodes,
                        int maximumForEachIterations, int maximumPlanBytes) {
            this.maximumActionDepth = maximumActionDepth;
            this.maximumCompiledNodes = maximumCompiledNodes;
            this.maximumForEachIterations = maximumForEachIterations;
            this.maximumPlanBytes = maximumPlanBytes;
        }

        public static Compiler defaults() {
            return new Compiler(
                    ActionModuleDefaults.MAXIMUM_ACTION_DEPTH,
                    ActionModuleDefaults.MAXIMUM_COMPILED_NODES,
                    ActionModuleDefaults.MAXIMUM_FOR_EACH_ITERATIONS,
                    ActionModuleDefaults.MAXIMUM_PLAN_BYTES
            );
        }

        public int maximumActionDepth() {
            return maximumActionDepth;
        }

        public int maximumCompiledNodes() {
            return maximumCompiledNodes;
        }

        public int maximumForEachIterations() {
            return maximumForEachIterations;
        }

        public int maximumPlanBytes() {
            return maximumPlanBytes;
        }
    }
}
