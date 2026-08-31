package com.kunling.scheduling.action.execution.infrastructure;

import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaActionExecutionStoreTest {
    private final JsonCodec codec = new JsonCodec(ActionTestFixtures.MAPPER);

    @Mock private ActionExecutionRepository executionRepository;
    @Mock private ActionExecutionEventRepository eventRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    private JpaActionExecutionStore store;

    @BeforeEach
    void setUp() {
        store = new JpaActionExecutionStore(executionRepository, eventRepository, codec,
                transactionManager, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void activeLockLookupUsesActionDefinitionId() {
        ActionExecutionEntity entity = new ActionExecutionEntity(ActionTestFixtures.newExecution(), codec);
        when(executionRepository.findFirstByActionDefinitionIdAndStateInOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq("definition-1"), any())).thenReturn(Optional.of(entity));

        assertThat(store.findActiveExecutionIdByActionDefinitionId("definition-1"))
                .contains("action-1");
    }

    @Test
    void outOfOrderEventIsNotPersisted() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        ActionExecutionEntity entity = new ActionExecutionEntity(ActionTestFixtures.newExecution(), codec);
        entity.applyEvent(event("event-2", 2), codec, Instant.EPOCH);
        when(executionRepository.findByIdForUpdate("action-1")).thenReturn(Optional.of(entity));
        when(eventRepository.existsById("event-1")).thenReturn(false);

        assertThat(store.applyEvent(event("event-1", 1))).isEmpty();
        verify(eventRepository, never()).save(any(ActionExecutionEventEntity.class));
        verify(executionRepository, never()).save(any(ActionExecutionEntity.class));
    }

    private RobotActionEvent event(String messageId, long sequence) {
        return new RobotActionEvent("ACTION_EVENT", messageId, "session-1", "R01",
                "action-1", "dc-1", sequence, RobotActionEvent.State.RUNNING,
                null, null, null, null, Instant.EPOCH);
    }
}
