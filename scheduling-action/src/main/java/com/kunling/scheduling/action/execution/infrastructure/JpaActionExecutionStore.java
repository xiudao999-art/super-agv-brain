package com.kunling.scheduling.action.execution.infrastructure;

import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionNotFoundException;
import com.kunling.scheduling.action.execution.application.ActionExecutionStore;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.action.execution.domain.CreateActionExecutionResult;
import com.kunling.scheduling.action.execution.domain.NewActionExecution;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.config.JsonCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class JpaActionExecutionStore implements ActionExecutionStore {

    private static final List<ActionExecutionState> ACTIVE_STATES = ImmutableCollections.listOf(
            ActionExecutionState.DISPATCH_PENDING,
            ActionExecutionState.DISPATCHED,
            ActionExecutionState.ACCEPTED,
            ActionExecutionState.RUNNING);

    private final ActionExecutionRepository executionRepository;
    private final ActionExecutionEventRepository eventRepository;
    private final JsonCodec jsonCodec;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    // 类中还保留了可注入 Clock 的测试构造器，因此必须显式声明生产环境的 Spring 注入入口。
    @Autowired
    public JpaActionExecutionStore(ActionExecutionRepository executionRepository,
                                   ActionExecutionEventRepository eventRepository,
                                   JsonCodec jsonCodec,
                                   PlatformTransactionManager transactionManager) {
        this(executionRepository, eventRepository, jsonCodec, transactionManager, Clock.systemUTC());
    }

    JpaActionExecutionStore(ActionExecutionRepository executionRepository,
                            ActionExecutionEventRepository eventRepository,
                            JsonCodec jsonCodec,
                            PlatformTransactionManager transactionManager,
                            Clock clock) {
        this.executionRepository = executionRepository;
        this.eventRepository = eventRepository;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CreateActionExecutionResult createIfAbsent(NewActionExecution execution) {
        try {
            return inTransaction(() -> executionRepository.findById(execution.actionInstanceId())
                    .map(existing -> existingResult(existing, execution.requestHash()))
                    .orElseGet(() -> {
                        ActionExecutionEntity entity = new ActionExecutionEntity(execution, jsonCodec);
                        executionRepository.saveAndFlush(entity);
                        return new CreateActionExecutionResult(true, entity.toView(jsonCodec));
                    }));
        } catch (DataIntegrityViolationException collision) {
            return inTransaction(() -> existingResult(required(execution.actionInstanceId()),
                    execution.requestHash()));
        }
    }

    @Override
    public ActionExecutionView markDispatched(String actionInstanceId, String sessionId,
                                              String messageId, Instant sentAt) {
        return mutate(actionInstanceId, entity -> entity.markDispatched(sessionId, messageId, sentAt));
    }

    @Override
    public ActionExecutionView hold(String actionInstanceId, String code, String message, Instant now) {
        return mutate(actionInstanceId, entity -> entity.hold(code, message, jsonCodec, now));
    }

    @Override
    public Optional<ActionExecutionView> applyEvent(RobotActionEvent event) {
        return inTransaction(() -> {
            ActionExecutionEntity entity = requiredForUpdate(event.actionInstanceId());
            if (eventRepository.existsById(event.messageId())) {
                return Optional.empty();
            }
            Instant receivedAt = clock.instant();
            eventRepository.save(new ActionExecutionEventEntity(event, jsonCodec, receivedAt));
            boolean reportable = entity.applyEvent(event, jsonCodec, receivedAt);
            ActionExecutionView view = executionRepository.save(entity).toView(jsonCodec);
            return reportable ? Optional.of(view) : Optional.empty();
        });
    }

    @Override
    public ActionExecutionView get(String actionInstanceId) {
        return inTransaction(() -> required(actionInstanceId).toView(jsonCodec));
    }

    @Override
    public List<ActionExecutionEventView> getEvents(String actionInstanceId, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit 必须在 1 到 1000 之间。");
        }
        return inTransaction(() -> {
            // 先确认执行实例存在，使不存在和“暂时还没有事件”具有明确不同的 API 语义。
            required(actionInstanceId);
            return eventRepository
                    .findByActionInstanceIdOrderByReceivedAtAscEventSequenceAsc(
                            actionInstanceId, PageRequest.of(0, limit))
                    .stream().map(entity -> entity.toView(jsonCodec))
                    .collect(ImmutableCollections.toImmutableList());
        });
    }

    @Override
    public Optional<ActionExecutionView> find(String actionInstanceId) {
        return inTransaction(() -> executionRepository.findById(actionInstanceId)
                .map(entity -> entity.toView(jsonCodec)));
    }

    @Override
    public Optional<String> findActiveExecutionIdByActionKey(String actionKey) {
        if (actionKey == null) return Optional.empty();
        return executionRepository.findFirstByActionKeyAndStateInOrderByCreatedAtDesc(actionKey, ACTIVE_STATES)
                .map(ActionExecutionEntity::getActionInstanceId);
    }

    @Override
    public Optional<String> findActiveExecutionIdByParameterSetId(String parameterSetId) {
        if (parameterSetId == null) return Optional.empty();
        return executionRepository.findFirstByParameterSetIdAndStateInOrderByCreatedAtDesc(
                        parameterSetId, ACTIVE_STATES)
                .map(ActionExecutionEntity::getActionInstanceId);
    }

    @Override
    public List<ActionExecutionView> holdInterruptedExecutions(String reasonCode, String message, Instant now) {
        return inTransaction(() -> executionRepository.findByStateInForUpdate(ACTIVE_STATES).stream()
                .map(entity -> holdAndView(entity, reasonCode, message, now))
                .collect(ImmutableCollections.toImmutableList()));
    }

    @Override
    public List<ActionExecutionView> holdActiveExecutionsForRobot(String robotId, String reasonCode,
                                                                  String message, Instant now) {
        return inTransaction(() -> executionRepository.findByRobotIdAndStateInForUpdate(robotId, ACTIVE_STATES)
                .stream().map(entity -> holdAndView(entity, reasonCode, message, now))
                .collect(ImmutableCollections.toImmutableList()));
    }

    @Override
    public List<ActionExecutionView> findHeldExecutionsForRobot(String robotId) {
        return inTransaction(() -> executionRepository
                .findByRobotIdAndState(robotId, ActionExecutionState.UNKNOWN_HOLD).stream()
                .map(entity -> entity.toView(jsonCodec))
                .collect(ImmutableCollections.toImmutableList()));
    }

    @Override
    public List<ActionExecutionView> holdTimedOutExecutions(Instant now) {
        return inTransaction(() -> executionRepository.findByStateInForUpdate(ACTIVE_STATES).stream()
                .filter(entity -> entity.isTimedOutAt(now))
                .map(entity -> holdAndView(entity, "ACTION_TIMEOUT",
                        "动作在约定时间内未返回确定终态", now))
                .collect(ImmutableCollections.toImmutableList()));
    }

    private ActionExecutionView holdAndView(ActionExecutionEntity entity, String code,
                                            String message, Instant now) {
        entity.hold(code, message, jsonCodec, now);
        return entity.toView(jsonCodec);
    }

    private CreateActionExecutionResult existingResult(ActionExecutionEntity entity, String expectedRequestHash) {
        if (!entity.getRequestHash().equals(expectedRequestHash)) {
            throw new ActionConflictException("actionInstanceId 已绑定到不同的动作包或工作流上下文。");
        }
        return new CreateActionExecutionResult(false, entity.toView(jsonCodec));
    }

    private ActionExecutionView mutate(String id, java.util.function.Consumer<ActionExecutionEntity> change) {
        return inTransaction(() -> {
            ActionExecutionEntity entity = requiredForUpdate(id);
            change.accept(entity);
            return executionRepository.save(entity).toView(jsonCodec);
        });
    }

    private ActionExecutionEntity required(String id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new ActionNotFoundException("找不到动作执行实例：" + id));
    }

    private ActionExecutionEntity requiredForUpdate(String id) {
        return executionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ActionNotFoundException("找不到动作执行实例：" + id));
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return Objects.requireNonNull(requiresNew.execute(status -> supplier.get()));
    }
}
