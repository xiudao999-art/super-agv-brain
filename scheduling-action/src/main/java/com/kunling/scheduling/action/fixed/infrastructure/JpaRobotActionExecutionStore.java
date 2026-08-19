package com.kunling.scheduling.action.fixed.infrastructure;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionNotFoundException;
import com.kunling.scheduling.action.fixed.application.RobotActionExecutionStore;
import com.kunling.scheduling.action.fixed.domain.CreateRobotActionExecutionResult;
import com.kunling.scheduling.action.fixed.domain.NewRobotActionExecution;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionState;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView;
import com.kunling.scheduling.action.shared.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class JpaRobotActionExecutionStore implements RobotActionExecutionStore {

    private static final List<RobotActionExecutionState> ACTIVE_STATES = ImmutableCollections.listOf(
            RobotActionExecutionState.DISPATCH_PENDING,
            RobotActionExecutionState.DISPATCHED,
            RobotActionExecutionState.ACCEPTED,
            RobotActionExecutionState.RUNNING
    );

    private final RobotActionExecutionRepository executionRepository;
    private final RobotActionEventRepository eventRepository;
    private final JsonCodec jsonCodec;
    private final TransactionTemplate requiresNew;

    public JpaRobotActionExecutionStore(RobotActionExecutionRepository executionRepository,
                                        RobotActionEventRepository eventRepository,
                                        JsonCodec jsonCodec,
                                        PlatformTransactionManager transactionManager) {
        this.executionRepository = executionRepository;
        this.eventRepository = eventRepository;
        this.jsonCodec = jsonCodec;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CreateRobotActionExecutionResult createIfAbsent(NewRobotActionExecution execution) {
        try {
            return inTransaction(() -> executionRepository.findById(execution.actionInstanceId())
                    .map(existing -> existingResult(existing, execution.requestHash()))
                    .orElseGet(() -> {
                        RobotActionExecutionEntity entity = new RobotActionExecutionEntity(execution, jsonCodec);
                        executionRepository.saveAndFlush(entity);
                        return new CreateRobotActionExecutionResult(true, entity.toView(jsonCodec));
                    }));
        } catch (DataIntegrityViolationException collision) {
            // 多请求并发抢占同一 actionInstanceId 时，在全新的事务里读取胜出记录并校验指纹。
            return inTransaction(() -> existingResult(required(execution.actionInstanceId()),
                    execution.requestHash()));
        }
    }

    @Override
    public RobotActionExecutionView markDispatched(String actionInstanceId, String sessionId,
                                                   String messageId, Instant sentAt) {
        return mutate(actionInstanceId, entity -> entity.markDispatched(sessionId, messageId, sentAt));
    }

    @Override
    public RobotActionExecutionView hold(String actionInstanceId, String code, String message, Instant now) {
        return mutate(actionInstanceId, entity -> entity.hold(code, message, jsonCodec, now));
    }

    @Override
    public RobotActionExecutionView applyEvent(RobotActionEvent event) {
        return inTransaction(() -> {
            RobotActionExecutionEntity entity = requiredForUpdate(event.actionInstanceId());
            if (eventRepository.existsById(event.messageId())) {
                return entity.toView(jsonCodec);
            }
            Instant receivedAt = Instant.now();
            eventRepository.save(new RobotActionEventEntity(event, jsonCodec, receivedAt));
            entity.applyEvent(event, jsonCodec, receivedAt);
            return executionRepository.save(entity).toView(jsonCodec);
        });
    }

    @Override
    public RobotActionExecutionView get(String actionInstanceId) {
        return inTransaction(() -> required(actionInstanceId).toView(jsonCodec));
    }

    @Override
    public Optional<RobotActionExecutionView> find(String actionInstanceId) {
        return inTransaction(() -> executionRepository.findById(actionInstanceId)
                .map(entity -> entity.toView(jsonCodec)));
    }

    @Override
    public List<RobotActionExecutionView> holdInterruptedExecutions(String reasonCode, String message,
                                                                   Instant now) {
        return inTransaction(() -> executionRepository.findByStateIn(ACTIVE_STATES).stream()
                .map(entity -> {
                    entity.hold(reasonCode, message, jsonCodec, now);
                    return entity.toView(jsonCodec);
                })
                .collect(ImmutableCollections.toImmutableList()));
    }

    @Override
    public List<RobotActionExecutionView> holdActiveExecutionsForRobot(String robotId, String reasonCode,
                                                                       String message, Instant now) {
        return inTransaction(() -> executionRepository.findByRobotIdAndStateIn(robotId, ACTIVE_STATES).stream()
                .map(entity -> {
                    entity.hold(reasonCode, message, jsonCodec, now);
                    return entity.toView(jsonCodec);
                })
                .collect(ImmutableCollections.toImmutableList()));
    }

    @Override
    public List<RobotActionExecutionView> findHeldExecutionsForRobot(String robotId) {
        return inTransaction(() -> executionRepository
                .findByRobotIdAndState(robotId, RobotActionExecutionState.UNKNOWN_HOLD).stream()
                .map(entity -> entity.toView(jsonCodec))
                .collect(ImmutableCollections.toImmutableList()));
    }

    @Override
    public List<RobotActionExecutionView> holdTimedOutExecutions(Instant now) {
        return inTransaction(() -> executionRepository.findByStateIn(ACTIVE_STATES).stream()
                .filter(entity -> entity.isTimedOutAt(now))
                .map(entity -> {
                    entity.hold("ACTION_TIMEOUT", "动作在约定时间内未返回确定终态", jsonCodec, now);
                    return entity.toView(jsonCodec);
                })
                .collect(ImmutableCollections.toImmutableList()));
    }

    private CreateRobotActionExecutionResult existingResult(RobotActionExecutionEntity entity,
                                                             String expectedRequestHash) {
        if (!entity.getRequestHash().equals(expectedRequestHash)) {
            throw new ActionConflictException("actionInstanceId 已绑定到不同的机器人、动作包或工作流上下文");
        }
        return new CreateRobotActionExecutionResult(false, entity.toView(jsonCodec));
    }

    private RobotActionExecutionView mutate(String id, java.util.function.Consumer<RobotActionExecutionEntity> change) {
        return inTransaction(() -> {
            RobotActionExecutionEntity entity = requiredForUpdate(id);
            change.accept(entity);
            return executionRepository.save(entity).toView(jsonCodec);
        });
    }

    private RobotActionExecutionEntity required(String id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new ActionNotFoundException("找不到机器人动作执行实例: " + id));
    }

    private RobotActionExecutionEntity requiredForUpdate(String id) {
        return executionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ActionNotFoundException("找不到机器人动作执行实例: " + id));
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return Objects.requireNonNull(requiresNew.execute(status -> supplier.get()));
    }
}
