package com.kunling.scheduling.action.fixed;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.fixed.application.FixedActionExecutionService;
import com.kunling.scheduling.action.fixed.application.FixedActionPackageCatalog;
import com.kunling.scheduling.action.fixed.application.RobotActionExecutionStore;
import com.kunling.scheduling.action.fixed.application.StartFixedActionExecutionRequest;
import com.kunling.scheduling.action.fixed.domain.CreateRobotActionExecutionResult;
import com.kunling.scheduling.action.fixed.domain.FixedActionType;
import com.kunling.scheduling.action.fixed.domain.MaterializedFixedActionPackage;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionState;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView;
import com.kunling.scheduling.action.shared.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.DispatchReceipt;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixedActionExecutionServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final Instant now = Instant.parse("2026-08-19T01:00:00Z");
    private final FixedActionPackageCatalog catalog = mock(FixedActionPackageCatalog.class);
    private final RobotActionExecutionStore store = mock(RobotActionExecutionStore.class);
    private final RobotActionTransport transport = mock(RobotActionTransport.class);
    private final FixedActionExecutionService service = new FixedActionExecutionService(
            catalog, store, transport, new JsonCodec(objectMapper), Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void executionIsPersistedBeforeTheCompletePackageIsDispatched() throws Exception {
        StartFixedActionExecutionRequest request = request("action-1");
        MaterializedFixedActionPackage actionPackage = actionPackage();
        RobotActionExecutionView pending = view("action-1", RobotActionExecutionState.DISPATCH_PENDING);
        RobotActionExecutionView dispatched = view("action-1", RobotActionExecutionState.DISPATCHED);
        when(catalog.materialize(FixedActionType.MOVE, request.input())).thenReturn(actionPackage);
        when(transport.findSession("ROBOT-01")).thenReturn(Optional.of(session()));
        when(store.createIfAbsent(any())).thenReturn(new CreateRobotActionExecutionResult(true, pending));
        when(transport.dispatch(any())).thenReturn(new DispatchReceipt("session-1", "message-1", now));
        when(store.markDispatched("action-1", "session-1", "message-1", now)).thenReturn(dispatched);

        assertThat(service.start(request).state()).isEqualTo(RobotActionExecutionState.DISPATCHED);

        InOrder order = inOrder(store, transport);
        order.verify(store).createIfAbsent(any());
        order.verify(transport).dispatch(any());
        order.verify(store).markDispatched("action-1", "session-1", "message-1", now);
    }

    @Test
    void retryingAnExistingActionInstanceNeverResendsThePhysicalAction() throws Exception {
        StartFixedActionExecutionRequest request = request("action-1");
        when(catalog.materialize(FixedActionType.MOVE, request.input())).thenReturn(actionPackage());
        when(transport.findSession("ROBOT-01")).thenReturn(Optional.of(session()));
        when(store.createIfAbsent(any())).thenReturn(
                new CreateRobotActionExecutionResult(false, view("action-1", RobotActionExecutionState.RUNNING)));

        assertThat(service.start(request).state()).isEqualTo(RobotActionExecutionState.RUNNING);
        verify(transport, never()).dispatch(any());
    }

    @Test
    void anAmbiguousSocketWriteMovesTheExecutionToUnknownHold() throws Exception {
        StartFixedActionExecutionRequest request = request("action-1");
        when(catalog.materialize(FixedActionType.MOVE, request.input())).thenReturn(actionPackage());
        when(transport.findSession("ROBOT-01")).thenReturn(Optional.of(session()));
        when(store.createIfAbsent(any())).thenReturn(new CreateRobotActionExecutionResult(true,
                view("action-1", RobotActionExecutionState.DISPATCH_PENDING)));
        when(transport.dispatch(any())).thenThrow(new RobotUnavailableException("连接写入失败"));
        when(store.hold("action-1", "DISPATCH_RESULT_UNKNOWN", "连接写入失败", now))
                .thenReturn(view("action-1", RobotActionExecutionState.UNKNOWN_HOLD));

        assertThat(service.start(request).state()).isEqualTo(RobotActionExecutionState.UNKNOWN_HOLD);
    }

    @Test
    void offlineRobotIsRejectedBeforeAnExecutionRowIsCreated() throws Exception {
        StartFixedActionExecutionRequest request = request("action-1");
        when(catalog.materialize(FixedActionType.MOVE, request.input())).thenReturn(actionPackage());
        when(transport.findSession("ROBOT-01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(RobotUnavailableException.class)
                .hasMessageContaining("未连接");
        verify(store, never()).createIfAbsent(any());
    }

    @Test
    void anExistingIdempotentExecutionCanBeReadEvenWhenTheRobotIsOffline() throws Exception {
        StartFixedActionExecutionRequest request = request("action-1");
        RobotActionExecutionView existing = view("action-1", RobotActionExecutionState.RUNNING);
        when(catalog.materialize(FixedActionType.MOVE, request.input())).thenReturn(actionPackage());
        when(store.find("action-1")).thenReturn(Optional.of(existing));

        assertThat(service.start(request)).isSameAs(existing);
        verify(transport, never()).dispatch(any());
    }

    private StartFixedActionExecutionRequest request(String id) throws Exception {
        return new StartFixedActionExecutionRequest(id, "ROBOT-01", "MOVE",
                objectMapper.readTree("{\"pointName\":\"P01\"}"), "workflow-1", "node-1");
    }

    private MaterializedFixedActionPackage actionPackage() throws Exception {
        return new MaterializedFixedActionPackage(FixedActionType.MOVE, "1.0", "1.0.0", 35_000,
                objectMapper.readTree("{\"MainAction\":{\"actionType\":\"MOVE\",\"phases\":[]}}"),
                "package-hash");
    }

    private RobotSessionView session() {
        return new RobotSessionView("session-1", "ROBOT-01", "COMPOSITE", "client-1",
                ImmutableCollections.setOf("MOVE"), now, now);
    }

    private RobotActionExecutionView view(String id, RobotActionExecutionState state) {
        return new RobotActionExecutionView(id, "ROBOT-01", "device-1", "MOVE", "1.0", "1.0.0",
                expectedRequestHash(), "package-hash", state, state != RobotActionExecutionState.UNKNOWN_HOLD,
                "workflow-1", "node-1", null, null, null, null, now, now, null);
    }

    private String expectedRequestHash() {
        com.fasterxml.jackson.databind.node.ObjectNode fingerprint = objectMapper.createObjectNode();
        fingerprint.put("robotId", "ROBOT-01");
        fingerprint.put("actionType", "MOVE");
        fingerprint.put("packageHash", "package-hash");
        fingerprint.put("workflowInstanceId", "workflow-1");
        fingerprint.put("workflowNodeInstanceId", "node-1");
        JsonCodec codec = new JsonCodec(objectMapper);
        return codec.sha256(codec.writeCanonical(fingerprint));
    }
}
