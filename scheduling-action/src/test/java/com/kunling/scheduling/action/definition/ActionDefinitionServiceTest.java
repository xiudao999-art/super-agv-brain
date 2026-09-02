package com.kunling.scheduling.action.definition;

import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionValidator;
import com.kunling.scheduling.action.definition.application.ActionExecutionLock;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.infrastructure.ActionDefinitionEntity;
import com.kunling.scheduling.action.definition.infrastructure.ActionDefinitionRepository;
import com.kunling.scheduling.action.robotbridge.application.ActionCapabilityValidator;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionDefinitionServiceTest {
    private final ActionDefinitionRepository repository = mock(ActionDefinitionRepository.class);
    private final ActionExecutionLock executionLock = mock(ActionExecutionLock.class);
    private final RobotActionTransport transport = mock(RobotActionTransport.class);
    private final JsonCodec jsonCodec = new JsonCodec(ActionTestFixtures.MAPPER);
    private final ActionDefinitionService service = new ActionDefinitionService(
            repository, new ActionDefinitionValidator(), executionLock, transport,
            new ActionCapabilityValidator(), jsonCodec);

    private ActionDefinitionEntity entity;

    @BeforeEach
    void setUp() {
        entity = new ActionDefinitionEntity("definition-1",
                ActionTestFixtures.definition("definition-1", false), jsonCodec, Instant.EPOCH);
        when(repository.findByIdForUpdate("definition-1")).thenReturn(Optional.of(entity));
        when(repository.save(any(ActionDefinitionEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(executionLock.findActiveExecutionIdByActionDefinitionId("definition-1"))
                .thenReturn(Optional.empty());
    }

    @Test
    void createUsesServerOwnedIdentityAndStartsDisabled() {
        ActionDefinition input = new ActionDefinition("client-supplied-id", "测试 Action", true,
                60_000, ActionTestFixtures.definition("x", false).steps());
        ActionDefinition persisted = service.create(input).definition();

        assertThat(persisted.id()).isNotEqualTo("client-supplied-id");
        assertThat(persisted.enabled()).isFalse();
    }

    @Test
    void createPersistsIncompleteDraftWithoutContentValidation() {
        ActionDefinition incompleteDraft = new ActionDefinition(null, "", true,
                -1, Collections.emptyList());

        ActionDefinition persisted = service.create(incompleteDraft).definition();

        assertThat(persisted.name()).isEmpty();
        assertThat(persisted.timeoutMs()).isEqualTo(-1);
        assertThat(persisted.steps()).isEmpty();
        assertThat(persisted.enabled()).isFalse();
    }

    @Test
    void updatePersistsIncompleteEnabledDraftWithoutContentValidation() {
        entity.changeEnabled(true, Instant.EPOCH);
        ActionDefinition incompleteDraft = new ActionDefinition("definition-1", "", true,
                -1, Collections.emptyList());

        ActionDefinition persisted = service.update("definition-1", incompleteDraft).definition();

        assertThat(persisted.name()).isEmpty();
        assertThat(persisted.timeoutMs()).isEqualTo(-1);
        assertThat(persisted.steps()).isEmpty();
        assertThat(persisted.enabled()).isTrue();
    }

    @Test
    void updateUsesPathIdentityAndPersistedEnabledStateWithoutRejectingTheDraft() {
        ActionDefinition draft = new ActionDefinition("client-supplied-id", "草稿", true,
                60_000, Collections.emptyList());

        ActionDefinition persisted = service.update("definition-1", draft).definition();

        assertThat(persisted.id()).isEqualTo("definition-1");
        assertThat(persisted.enabled()).isFalse();
    }

    @Test
    void enableStillRejectsAnIncompleteSavedDraft() {
        ActionDefinition incompleteDraft = new ActionDefinition("definition-1", "", false,
                -1, Collections.emptyList());
        service.update("definition-1", incompleteDraft);
        when(transport.findSession("R01")).thenReturn(Optional.of(ActionTestFixtures.session()));

        assertThatThrownBy(() -> service.enable("definition-1", "R01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void updateLocksDefinitionRowBeforeCheckingActiveExecution() {
        service.update("definition-1", ActionTestFixtures.definition("definition-1", false));

        InOrder order = inOrder(repository, executionLock);
        order.verify(repository).findByIdForUpdate("definition-1");
        order.verify(executionLock).findActiveExecutionIdByActionDefinitionId("definition-1");
    }

    @Test
    void definitionRowLockIsPessimisticAndExecutionLockMustJoinCallerTransaction() throws Exception {
        Method repositoryMethod = ActionDefinitionRepository.class
                .getMethod("findByIdForUpdate", String.class);
        Method serviceMethod = ActionDefinitionService.class
                .getMethod("lockEnabledForExecution", String.class);

        assertThat(repositoryMethod.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(serviceMethod.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void rejectsUpdateWhileDefinitionIsRunning() {
        when(executionLock.findActiveExecutionIdByActionDefinitionId("definition-1"))
                .thenReturn(Optional.of("execution-running-001"));

        assertThatThrownBy(() -> service.update("definition-1",
                ActionTestFixtures.definition("definition-1", false)))
                .isInstanceOf(ActionConflictException.class)
                .hasMessageContaining("execution-running-001");
    }

    @Test
    void enableRequiresOnlineRobotAndValidCapabilities() {
        when(transport.findSession("R01")).thenReturn(Optional.of(ActionTestFixtures.session()));

        assertThat(service.enable("definition-1", "R01").definition().enabled()).isTrue();
    }

    @Test
    void disableAndDeleteUseTheSameDefinitionLock() {
        entity.changeEnabled(true, Instant.EPOCH);
        service.disable("definition-1");
        service.delete("definition-1");

        verify(repository, org.mockito.Mockito.times(2)).findByIdForUpdate("definition-1");
        verify(repository).delete(entity);
    }
}
