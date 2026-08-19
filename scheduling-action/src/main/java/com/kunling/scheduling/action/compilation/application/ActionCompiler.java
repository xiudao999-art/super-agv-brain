package com.kunling.scheduling.action.compilation.application;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.kunling.scheduling.action.capability.application.CapabilityCatalog;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import com.kunling.scheduling.action.capability.domain.CapabilitySideEffect;
import com.kunling.scheduling.action.compilation.domain.ActionDependency;
import com.kunling.scheduling.action.compilation.domain.ActionGroupReference;
import com.kunling.scheduling.action.compilation.domain.CapabilityRequirement;
import com.kunling.scheduling.action.compilation.domain.CompileIssue;
import com.kunling.scheduling.action.compilation.domain.CompileResult;
import com.kunling.scheduling.action.compilation.domain.ConditionGuard;
import com.kunling.scheduling.action.compilation.domain.ExecutionNode;
import com.kunling.scheduling.action.compilation.domain.ExecutionPlan;
import com.kunling.scheduling.action.compilation.domain.LoopFrame;
import com.kunling.scheduling.action.config.ActionProperties;
import com.kunling.scheduling.action.definition.application.PublishedAction;
import com.kunling.scheduling.action.definition.application.PublishedActionLookup;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionReferenceStepDefinition;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import com.kunling.scheduling.action.definition.domain.CapabilityStepDefinition;
import com.kunling.scheduling.action.definition.domain.ConditionExpression;
import com.kunling.scheduling.action.definition.domain.ConditionStepDefinition;
import com.kunling.scheduling.action.definition.domain.ForEachStepDefinition;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import com.kunling.scheduling.action.definition.domain.ParameterType;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ActionCompiler {

    public static final String COMPILER_VERSION = "3.0.0";
    private static final Pattern ACTION_KEY = Pattern.compile("[A-Z][A-Z0-9_.-]{1,127}");
    private static final Pattern VERSION = Pattern.compile("(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)");
    private static final Pattern STEP_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    private final CapabilityCatalog capabilityCatalog;
    private final PublishedActionLookup publishedActionLookup;
    private final ActionProperties.Compiler constraints;
    private final JsonCodec jsonCodec;

    public ActionCompiler(
            CapabilityCatalog capabilityCatalog,
            PublishedActionLookup publishedActionLookup,
            ActionProperties properties,
            JsonCodec jsonCodec) {
        this.capabilityCatalog = capabilityCatalog;
        this.publishedActionLookup = publishedActionLookup;
        this.constraints = properties.compiler();
        this.jsonCodec = jsonCodec;
    }

    public CompileResult compile(ActionDefinition definition) {
        Map<String, CapabilityManifest> capabilitySnapshot = new LinkedHashMap<>();
        for (CapabilityManifest capability : capabilityCatalog.listAll()) {
            CapabilityManifest duplicate = capabilitySnapshot.putIfAbsent(capability.capabilityKey(), capability);
            if (duplicate != null) {
                throw new IllegalStateException("当前原子能力目录包含重复 capabilityKey："
                        + capability.capabilityKey());
            }
        }
        CompilationSession session = new CompilationSession(definition, ImmutableCollections.copyMap(capabilitySnapshot));
        session.validateRoot();
        if (session.hasErrors()) {
            return session.failure();
        }

        Map<String, JsonNode> rootEnvironment = new HashMap<>();
        definition.inputSchema().keySet().forEach(name ->
                rootEnvironment.put(name, TextNode.valueOf("$input." + name)));
        session.expand(definition, rootEnvironment, ImmutableCollections.listOf(), ImmutableCollections.listOf(), ImmutableCollections.listOf(), ImmutableCollections.mapOf(), "$", 0);
        session.validateFinalPlan();
        return session.finish();
    }

    private final class CompilationSession {

        private final ActionDefinition root;
        private final Map<String, CapabilityManifest> capabilitySnapshot;
        private final List<CompileIssue> issues = new ArrayList<>();
        private final List<ExecutionNode> nodes = new ArrayList<>();
        private final Map<String, CapabilityRequirement> requirements = new LinkedHashMap<>();
        private final Map<String, ActionDependency> dependencies = new LinkedHashMap<>();
        private final LinkedHashSet<String> actionStack = new LinkedHashSet<>();

        private CompilationSession(ActionDefinition root, Map<String, CapabilityManifest> capabilitySnapshot) {
            this.root = root;
            this.capabilitySnapshot = capabilitySnapshot;
        }

        private void validateRoot() {
            if (root == null) {
                issues.add(CompileIssue.error("DEFINITION_REQUIRED", "$", "Action 定义不能为空。"));
                return;
            }
            if (root.actionKey() == null || !ACTION_KEY.matcher(root.actionKey()).matches()) {
                issues.add(CompileIssue.error("ACTION_KEY_INVALID", "$.actionKey",
                        "actionKey 必须使用大写字母、数字、点、横线或下划线，且以字母开头。"));
            }
            if (root.version() == null || root.version().length() > 32 || !VERSION.matcher(root.version()).matches()) {
                issues.add(CompileIssue.error("VERSION_INVALID", "$.version", "version 必须是完整 SemVer，例如 1.0.0。"));
            }
            if (root.displayName() == null || root.displayName().trim().isEmpty()) {
                issues.add(CompileIssue.error("DISPLAY_NAME_REQUIRED", "$.displayName", "动作名称不能为空。"));
            }
            if (root.steps().isEmpty()) {
                issues.add(CompileIssue.error("STEPS_REQUIRED", "$.steps", "动作至少需要一个启用节点。"));
            }
        }

        private void expand(
                ActionDefinition definition,
                Map<String, JsonNode> inputEnvironment,
                List<ActionGroupReference> groups,
                List<LoopFrame> loops,
                List<ConditionGuard> guards,
                Map<String, JsonNode> runtimeVariables,
                String sourcePath,
                int depth) {
            String identity = definition.actionKey() + "@" + definition.version();
            if (depth > constraints.maximumActionDepth()) {
                issues.add(CompileIssue.error("MAX_ACTION_DEPTH_EXCEEDED", sourcePath,
                        "动作引用深度超过限制 " + constraints.maximumActionDepth() + "。"));
                return;
            }
            if (!actionStack.add(identity)) {
                issues.add(CompileIssue.error("ACTION_REFERENCE_CYCLE", sourcePath,
                        "检测到循环引用：" + String.join(" -> ", actionStack) + " -> " + identity));
                return;
            }

            expandSteps(definition, definition.steps(), inputEnvironment, groups, loops, guards,
                    runtimeVariables, ImmutableCollections.mapOf(), sourcePath, depth);
            actionStack.remove(identity);
        }

        private void expandSteps(
                ActionDefinition definition,
                List<ActionStepDefinition> steps,
                Map<String, JsonNode> inputEnvironment,
                List<ActionGroupReference> groups,
                List<LoopFrame> loops,
                List<ConditionGuard> guards,
                Map<String, JsonNode> runtimeVariables,
                Map<String, String> inheritedStepOutputs,
                String sourcePath,
                int depth) {
            Set<String> stepIds = new HashSet<>();
            Map<String, String> visibleStepOutputs = new LinkedHashMap<>(inheritedStepOutputs);
            for (int index = 0; index < steps.size(); index++) {
                ActionStepDefinition step = steps.get(index);
                String stepPath = sourcePath + ".steps[" + index + "]";
                if (!step.isEnabled()) {
                    continue;
                }
                if (step.stepId() == null || !STEP_ID.matcher(step.stepId()).matches()) {
                    issues.add(CompileIssue.error("STEP_ID_INVALID", stepPath + ".stepId",
                            "stepId 必须以字母开头，只能包含字母、数字、横线或下划线，最长 64 字符。"));
                    continue;
                }
                if (!stepIds.add(step.stepId())) {
                    issues.add(CompileIssue.error("STEP_ID_DUPLICATED", stepPath + ".stepId",
                            "同一层级 stepId 重复：" + step.stepId()));
                    continue;
                }

                if (step instanceof CapabilityStepDefinition) {
                    CapabilityStepDefinition capabilityStep = (CapabilityStepDefinition) step;
                    appendCapability(definition, capabilityStep, inputEnvironment, groups, loops, guards,
                            runtimeVariables, visibleStepOutputs, stepPath);
                } else if (step instanceof ActionReferenceStepDefinition) {
                    ActionReferenceStepDefinition referenceStep = (ActionReferenceStepDefinition) step;
                    appendReference(referenceStep, inputEnvironment, groups, loops, guards,
                            runtimeVariables, visibleStepOutputs, stepPath, depth);
                } else if (step instanceof ConditionStepDefinition) {
                    ConditionStepDefinition conditionStep = (ConditionStepDefinition) step;
                    appendCondition(definition, conditionStep, inputEnvironment, groups, loops, guards,
                            runtimeVariables, visibleStepOutputs, stepPath, depth);
                } else if (step instanceof ForEachStepDefinition) {
                    ForEachStepDefinition forEachStep = (ForEachStepDefinition) step;
                    appendForEach(definition, forEachStep, inputEnvironment, groups, loops, guards,
                            runtimeVariables, visibleStepOutputs, stepPath, depth);
                }
            }
        }

        private void appendCapability(
                ActionDefinition source,
                CapabilityStepDefinition step,
                Map<String, JsonNode> inputEnvironment,
                List<ActionGroupReference> groups,
                List<LoopFrame> loops,
                List<ConditionGuard> guards,
                Map<String, JsonNode> runtimeVariables,
                Map<String, String> visibleStepOutputs,
                String sourcePath) {
            if (step.capabilityKey() == null || step.capabilityKey().trim().isEmpty()) {
                issues.add(CompileIssue.error("CAPABILITY_IDENTITY_REQUIRED", sourcePath,
                        "原子能力节点必须配置 capabilityKey。"));
                return;
            }
            Optional<CapabilityManifest> manifestResult = Optional.ofNullable(
                    capabilitySnapshot.get(step.capabilityKey()));
            if (!manifestResult.isPresent()) {
                issues.add(CompileIssue.error("CAPABILITY_NOT_REGISTERED", sourcePath,
                        "上游能力目录未提供原子能力 " + step.capabilityKey() + "。"));
                return;
            }
            CapabilityManifest manifest = manifestResult.get();
            Map<String, JsonNode> bindings = substituteBindings(step.bindings(), inputEnvironment, runtimeVariables,
                    visibleStepOutputs);
            validateBindings(bindings, manifest.inputSchema(), sourcePath + ".with");

            String executionNodeId = createExecutionNodeId(groups, loops, guards, step.stepId());
            int timeoutMs = step.timeoutMs() == null || step.timeoutMs() <= 0
                    ? source.defaultPolicy().timeoutMs()
                    : step.timeoutMs();
            nodes.add(new ExecutionNode(
                    executionNodeId,
                    step.stepId(),
                    step.displayName(),
                    sourcePath,
                    source.actionKey(),
                    source.version(),
                    groups,
                    loops,
                    guards,
                    step.capabilityKey(),
                    manifest.contractHash(),
                    bindings,
                    timeoutMs,
                    step.onFailure(),
                    step.gate(),
                    manifest.resources(),
                    manifest.sideEffect() == CapabilitySideEffect.PHYSICAL));
            visibleStepOutputs.put(step.stepId(), executionNodeId);
            requirements.put(manifest.identity(),
                    new CapabilityRequirement(manifest.capabilityKey(), manifest.contractHash()));
        }

        private void appendReference(
                ActionReferenceStepDefinition step,
                Map<String, JsonNode> parentEnvironment,
                List<ActionGroupReference> parentGroups,
                List<LoopFrame> loops,
                List<ConditionGuard> guards,
                Map<String, JsonNode> runtimeVariables,
                Map<String, String> visibleStepOutputs,
                String sourcePath,
                int depth) {
            if (step.actionRef() == null || step.actionRef().actionKey() == null || step.actionRef().version() == null) {
                issues.add(CompileIssue.error("ACTION_REFERENCE_REQUIRED", sourcePath + ".actionRef",
                        "组合动作引用必须包含 actionKey 和 version。"));
                return;
            }
            Optional<PublishedAction> targetResult = publishedActionLookup.findPublished(
                    step.actionRef().actionKey(), step.actionRef().version());
            if (!targetResult.isPresent()) {
                issues.add(CompileIssue.error("ACTION_REFERENCE_NOT_PUBLISHED", sourcePath + ".actionRef",
                        "引用的组合动作尚未发布：" + step.actionRef().actionKey() + "@" + step.actionRef().version()));
                return;
            }
            PublishedAction target = targetResult.get();
            if (target.definition().entryPoint()) {
                issues.add(CompileIssue.error("ACTION_REFERENCE_ENTRY_POINT_FORBIDDEN", sourcePath + ".actionRef",
                        "主 Action 不能作为组合动作被引用。"));
                return;
            }

            Map<String, JsonNode> childEnvironment = substituteBindings(
                    step.bindings(), parentEnvironment, runtimeVariables, visibleStepOutputs);
            validateBindings(childEnvironment, target.definition().inputSchema(), sourcePath + ".with");
            List<ActionGroupReference> groups = new ArrayList<>(parentGroups);
            groups.add(new ActionGroupReference(target.definition().actionKey(), target.definition().version(),
                    step.stepId(), step.displayName()));
            dependencies.put(target.definition().actionKey() + "@" + target.definition().version(),
                    new ActionDependency(target.definition().actionKey(), target.definition().version(), target.planHash()));
            expand(target.definition(), childEnvironment, groups, loops, guards, runtimeVariables,
                    sourcePath + ".actionRef", depth + 1);
        }

        private void appendCondition(
                ActionDefinition definition,
                ConditionStepDefinition step,
                Map<String, JsonNode> inputEnvironment,
                List<ActionGroupReference> groups,
                List<LoopFrame> loops,
                List<ConditionGuard> guards,
                Map<String, JsonNode> runtimeVariables,
                Map<String, String> visibleStepOutputs,
                String sourcePath,
                int depth) {
            if (step.condition() == null || step.condition().operator() == null || step.condition().left() == null) {
                issues.add(CompileIssue.error("CONDITION_INVALID", sourcePath + ".condition",
                        "条件节点必须配置 operator 和 left。"));
                return;
            }
            ConditionExpression condition = new ConditionExpression(step.condition().operator(),
                    substitute(step.condition().left(), inputEnvironment, runtimeVariables, visibleStepOutputs),
                    substitute(step.condition().right(), inputEnvironment, runtimeVariables, visibleStepOutputs));

            List<ConditionGuard> thenGuards = new ArrayList<>(guards);
            thenGuards.add(new ConditionGuard(step.stepId(), condition, true));
            expandSteps(definition, step.then(), inputEnvironment, groups, loops, thenGuards,
                    runtimeVariables, visibleStepOutputs, sourcePath + ".then", depth);

            List<ConditionGuard> elseGuards = new ArrayList<>(guards);
            elseGuards.add(new ConditionGuard(step.stepId(), condition, false));
            expandSteps(definition, step.elseSteps(), inputEnvironment, groups, loops, elseGuards,
                    runtimeVariables, visibleStepOutputs, sourcePath + ".else", depth);
        }

        private void appendForEach(
                ActionDefinition definition,
                ForEachStepDefinition step,
                Map<String, JsonNode> inputEnvironment,
                List<ActionGroupReference> groups,
                List<LoopFrame> parentLoops,
                List<ConditionGuard> guards,
                Map<String, JsonNode> runtimeVariables,
                Map<String, String> visibleStepOutputs,
                String sourcePath,
                int depth) {
            if (step.maxIterations() <= 0 || step.maxIterations() > constraints.maximumForEachIterations()) {
                issues.add(CompileIssue.error("FOREACH_MAX_ITERATIONS_INVALID", sourcePath + ".maxIterations",
                        "maxIterations 必须在 1 到 " + constraints.maximumForEachIterations() + " 之间。"));
                return;
            }
            JsonNode items = substitute(TextNode.valueOf(step.items()), inputEnvironment, runtimeVariables,
                    visibleStepOutputs);
            if (!items.isTextual() || !items.textValue().startsWith("$")) {
                issues.add(CompileIssue.error("FOREACH_ITEMS_INVALID", sourcePath + ".items",
                        "items 必须是可解析的运行时数组表达式。"));
                return;
            }
            String itemVariable = step.itemVariable() == null || step.itemVariable().trim().isEmpty()
                    ? "$item" : step.itemVariable();
            if (!itemVariable.startsWith("$")) {
                issues.add(CompileIssue.error("FOREACH_ITEM_VARIABLE_INVALID", sourcePath + ".itemVariable",
                        "itemVariable 必须以 $ 开头。"));
                return;
            }

            // 一期循环上限很小，编译期展开固定槽位；运行时仅物化真实存在且条件成立的原子节点。
            for (int iteration = 0; iteration < step.maxIterations(); iteration++) {
                String itemToken = "$foreach." + createControlId(groups, parentLoops, step.stepId())
                        + "[" + iteration + "]";
                List<LoopFrame> loops = new ArrayList<>(parentLoops);
                loops.add(new LoopFrame(step.stepId(), items.textValue(), itemToken, iteration,
                        step.maxIterations(), step.orderBy()));
                Map<String, JsonNode> variables = new HashMap<>(runtimeVariables);
                variables.put(itemVariable, TextNode.valueOf(itemToken));
                expandSteps(definition, step.steps(), inputEnvironment, groups, loops, guards,
                        variables, visibleStepOutputs, sourcePath + ".iterations[" + iteration + "]", depth);
            }
        }

        private Map<String, JsonNode> substituteBindings(
                Map<String, JsonNode> bindings,
                Map<String, JsonNode> inputEnvironment,
                Map<String, JsonNode> runtimeVariables,
                Map<String, String> visibleStepOutputs) {
            Map<String, JsonNode> resolved = new LinkedHashMap<>();
            bindings.forEach((key, value) -> resolved.put(key,
                    substitute(value, inputEnvironment, runtimeVariables, visibleStepOutputs)));
            return resolved;
        }

        private JsonNode substitute(JsonNode value, Map<String, JsonNode> inputEnvironment,
                                    Map<String, JsonNode> runtimeVariables,
                                    Map<String, String> visibleStepOutputs) {
            if (value == null) {
                return com.fasterxml.jackson.databind.node.NullNode.instance;
            }
            if (value.isTextual() && value.textValue().startsWith("$steps.")) {
                return rewriteLocalStepOutput(value.textValue(), visibleStepOutputs);
            }
            if (value.isTextual() && value.textValue().startsWith("$input.")) {
                String path = value.textValue().substring("$input.".length());
                int separator = path.indexOf('.');
                String parameter = separator < 0 ? path : path.substring(0, separator);
                JsonNode replacement = inputEnvironment.get(parameter);
                if (replacement == null) {
                    return substituteRuntimeVariable(value, runtimeVariables);
                }
                if (separator < 0) {
                    return substituteRuntimeVariable(replacement.deepCopy(), runtimeVariables);
                }
                String suffix = path.substring(separator);
                if (replacement.isTextual() && replacement.textValue().startsWith("$")) {
                    return substituteRuntimeVariable(TextNode.valueOf(replacement.textValue() + suffix), runtimeVariables);
                }
                return substituteRuntimeVariable(
                        navigate(replacement, path.substring(separator + 1)).orElse(value).deepCopy(), runtimeVariables);
            }
            if (value.isTextual()) {
                return substituteRuntimeVariable(value, runtimeVariables);
            }
            if (value.isObject()) {
                ObjectNode copy = ((ObjectNode) value).objectNode();
                value.fields().forEachRemaining(entry -> copy.set(entry.getKey(),
                        substitute(entry.getValue(), inputEnvironment, runtimeVariables, visibleStepOutputs)));
                return copy;
            }
            if (value.isArray()) {
                ArrayNode copy = ((ArrayNode) value).arrayNode();
                value.forEach(item -> copy.add(
                        substitute(item, inputEnvironment, runtimeVariables, visibleStepOutputs)));
                return copy;
            }
            return value.deepCopy();
        }

        private JsonNode rewriteLocalStepOutput(
                String expression,
                Map<String, String> visibleStepOutputs) {
            int outputMarker = expression.indexOf(".output", "$steps.".length());
            if (outputMarker < 0) {
                issues.add(CompileIssue.error("STEP_OUTPUT_EXPRESSION_INVALID", "$",
                        "节点输出表达式必须使用 $steps.<stepId>.output 格式：" + expression));
                return TextNode.valueOf(expression);
            }
            String localStepId = expression.substring("$steps.".length(), outputMarker);
            String suffix = expression.substring(outputMarker);
            String executionNodeId = visibleStepOutputs.get(localStepId);
            if (executionNodeId == null) {
                issues.add(CompileIssue.error("STEP_OUTPUT_NOT_AVAILABLE", "$",
                        "节点输出只能引用同一顺序作用域内已经出现的原子节点：" + localStepId));
                return TextNode.valueOf(expression);
            }
            return TextNode.valueOf("$steps." + executionNodeId + suffix);
        }

        private JsonNode substituteRuntimeVariable(JsonNode value, Map<String, JsonNode> runtimeVariables) {
            if (!value.isTextual()) {
                return value.deepCopy();
            }
            String expression = value.textValue();
            return runtimeVariables.entrySet().stream()
                    .sorted(Map.Entry.<String, JsonNode>comparingByKey(
                            Comparator.comparingInt(String::length).reversed()))
                    .filter(entry -> expression.equals(entry.getKey()) || expression.startsWith(entry.getKey() + "."))
                    .findFirst()
                    .map(entry -> {
                        String suffix = expression.substring(entry.getKey().length());
                        JsonNode replacement = entry.getValue();
                        if (suffix.isEmpty()) {
                            return replacement.deepCopy();
                        }
                        if (replacement.isTextual() && replacement.textValue().startsWith("$")) {
                            return TextNode.valueOf(replacement.textValue() + suffix);
                        }
                        return navigate(replacement, suffix.substring(1)).orElse(value).deepCopy();
                    })
                    .orElseGet(value::deepCopy);
        }

        private Optional<JsonNode> navigate(JsonNode value, String path) {
            JsonNode current = value;
            for (String segment : path.split("\\.")) {
                current = current == null ? null : current.get(segment);
            }
            return Optional.ofNullable(current);
        }

        private void validateBindings(
                Map<String, JsonNode> bindings,
                Map<String, ParameterSchema> schema,
                String path) {
            schema.forEach((name, parameter) -> {
                if (parameter.required() && (!bindings.containsKey(name) || bindings.get(name) == null || bindings.get(name).isNull())) {
                    issues.add(CompileIssue.error("REQUIRED_PARAMETER_MISSING", path + "." + name,
                            "缺少必填参数 " + name + "。"));
                }
            });
            bindings.forEach((name, value) -> {
                ParameterSchema parameter = schema.get(name);
                if (parameter == null) {
                    issues.add(CompileIssue.error("UNKNOWN_PARAMETER", path + "." + name,
                            "原子能力未声明参数 " + name + "。"));
                } else if (value != null && !value.isNull()) {
                    validateLiteral(value, parameter, path + "." + name);
                }
            });
        }

        private void validateLiteral(JsonNode value, ParameterSchema schema, String path) {
            if (isExpression(value)) {
                return;
            }
            if (schema.type() == null || !matchesType(value, schema.type())) {
                issues.add(CompileIssue.error("PARAMETER_TYPE_MISMATCH", path,
                        "参数与 Schema 类型 " + schema.type() + " 不匹配。"));
                return;
            }
            if (!schema.enumValues().isEmpty()
                    && (!value.isTextual() || !schema.enumValues().contains(value.textValue()))) {
                issues.add(CompileIssue.error("PARAMETER_ENUM_INVALID", path,
                        "参数不在允许值 " + schema.enumValues() + " 中。"));
            }
            if (value.isNumber()) {
                java.math.BigDecimal number = value.decimalValue();
                if (schema.minimum() != null && number.compareTo(schema.minimum()) < 0) {
                    issues.add(CompileIssue.error("PARAMETER_BELOW_MINIMUM", path,
                            "参数不能小于 " + schema.minimum() + (schema.unit() == null ? "" : " " + schema.unit()) + "。"));
                }
                if (schema.maximum() != null && number.compareTo(schema.maximum()) > 0) {
                    issues.add(CompileIssue.error("PARAMETER_ABOVE_MAXIMUM", path,
                            "参数不能大于 " + schema.maximum() + (schema.unit() == null ? "" : " " + schema.unit()) + "。"));
                }
            }
            if (value.isObject()) {
                validateBindings(objectFields(value), schema.properties(), path);
            }
            if (value.isArray() && schema.items() != null) {
                for (int index = 0; index < value.size(); index++) {
                    validateLiteral(value.get(index), schema.items(), path + "[" + index + "]");
                }
            }
        }

        private Map<String, JsonNode> objectFields(JsonNode value) {
            Map<String, JsonNode> fields = new LinkedHashMap<>();
            value.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            return fields;
        }

        private boolean isExpression(JsonNode value) {
            return value.isTextual() && value.textValue().startsWith("$");
        }

        private boolean matchesType(JsonNode value, ParameterType type) {
            switch (type) {
                case STRING:
                    return value.isTextual();
                case NUMBER:
                    return value.isNumber();
                case INTEGER:
                    return value.isIntegralNumber();
                case BOOLEAN:
                    return value.isBoolean();
                case OBJECT:
                    return value.isObject();
                case ARRAY:
                    return value.isArray();
                default:
                    throw new IllegalArgumentException("不支持的参数类型：" + type);
            }
        }

        private String createExecutionNodeId(List<ActionGroupReference> groups, List<LoopFrame> loops,
                                             List<ConditionGuard> guards, String stepId) {
            StringBuilder result = new StringBuilder();
            groups.forEach(group -> result.append(group.referenceStepId()).append('/'));
            loops.forEach(loop -> result.append(loop.stepId()).append('[')
                    .append(loop.iterationIndex()).append("]/"));
            guards.forEach(guard -> result.append(guard.stepId())
                    .append(guard.expected() ? "[T]/" : "[F]/"));
            return result.append(stepId).toString();
        }

        private String createControlId(List<ActionGroupReference> groups, List<LoopFrame> loops, String stepId) {
            StringBuilder result = new StringBuilder();
            groups.forEach(group -> result.append(group.referenceStepId()).append('/'));
            loops.forEach(loop -> result.append(loop.stepId()).append('[')
                    .append(loop.iterationIndex()).append("]/"));
            return result.append(stepId).toString();
        }

        private void validateFinalPlan() {
            if (nodes.isEmpty() && issues.isEmpty()) {
                issues.add(CompileIssue.error("NO_EXECUTABLE_NODE", "$.steps", "动作没有可执行的原子节点。"));
            }
            if (nodes.size() > constraints.maximumCompiledNodes()) {
                issues.add(CompileIssue.error("MAX_COMPILED_NODES_EXCEEDED", "$.steps",
                        "展开后节点数 " + nodes.size() + " 超过限制 " + constraints.maximumCompiledNodes() + "。"));
            }
            Set<String> ids = new HashSet<>();
            nodes.forEach(node -> {
                if (!ids.add(node.executionNodeId())) {
                    issues.add(CompileIssue.error("EXECUTION_NODE_ID_DUPLICATED", node.sourcePath(),
                            "展开后的执行节点标识重复：" + node.executionNodeId()));
                }
                if (node.executionNodeId().length() > 1000) {
                    issues.add(CompileIssue.error("EXECUTION_NODE_ID_TOO_LONG", node.sourcePath(),
                            "展开后的执行节点标识超过 1000 字符，请减少控制节点或组合动作嵌套。"));
                }
            });
            boolean hasDeferredFailurePolicy = root.defaultPolicy().onFailure().maxRetries() > 0
                    || nodes.stream().anyMatch(node -> node.onFailure().maxRetries() > 0
                    || node.onFailure().strategy() != com.kunling.scheduling.action.definition.domain.FailureStrategy.ABORT);
            if (hasDeferredFailurePolicy) {
                issues.add(CompileIssue.warning("FAILURE_POLICY_DEFERRED", "$.steps",
                        "一期不执行节点自动重试、跳过或自定义 HOLD；已知失败将终止 Action 并交由工作流处置。"));
            }
        }

        private boolean hasErrors() {
            return issues.stream().anyMatch(issue -> issue.severity() == CompileIssue.Severity.ERROR);
        }

        private CompileResult failure() {
            return new CompileResult(false, sortedIssues(), null, ImmutableCollections.listOf(), ImmutableCollections.listOf(), null, null, COMPILER_VERSION);
        }

        private CompileResult finish() {
            if (hasErrors()) {
                return failure();
            }
            List<CapabilityRequirement> sortedRequirements = requirements.values().stream()
                    .sorted(Comparator.comparing(CapabilityRequirement::capabilityKey)
                            .thenComparing(CapabilityRequirement::contractHash))
                    .collect(ImmutableCollections.toImmutableList());
            List<ActionDependency> sortedDependencies = dependencies.values().stream()
                    .sorted(Comparator.comparing(ActionDependency::actionKey)
                            .thenComparing(ActionDependency::version))
                    .collect(ImmutableCollections.toImmutableList());
            ExecutionPlan unhashed = new ExecutionPlan("1.1", COMPILER_VERSION, root.actionKey(), root.version(), "",
                    nodes, sortedRequirements, sortedDependencies, nodes.size(), nodes.size());
            String canonicalJson = jsonCodec.writeCanonical(unhashed);
            if (canonicalJson.getBytes(StandardCharsets.UTF_8).length > constraints.maximumPlanBytes()) {
                issues.add(CompileIssue.error("MAX_PLAN_BYTES_EXCEEDED", "$",
                        "执行计划超过大小限制 " + constraints.maximumPlanBytes() + " 字节。"));
                return failure();
            }
            String planHash = jsonCodec.sha256(canonicalJson);
            ExecutionPlan plan = unhashed.withPlanHash(planHash);
            return new CompileResult(true, sortedIssues(), plan, sortedRequirements, sortedDependencies,
                    canonicalJson, planHash, COMPILER_VERSION);
        }

        private List<CompileIssue> sortedIssues() {
            return issues.stream()
                    .sorted(Comparator.comparing(CompileIssue::path).thenComparing(CompileIssue::code))
                    .collect(ImmutableCollections.toImmutableList());
        }
    }
}
