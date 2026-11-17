package parking.core.service.session;

import parking.core.service.common.Direction;
import parking.core.service.common.VerificationMode;
import parking.core.service.common.VerifiedPassage;
import parking.core.service.correlation.DetectionCorrelator;
import io.github.huongdang0208.accessgate.persistence.*;
import parking.core.service.persistence.AccessSessionEntity;
import parking.core.service.persistence.GateEntity;
import parking.core.service.persistence.SiteEntity;
import parking.core.service.persistence.VehicleEntity;
import parking.core.service.session.AccessDecision.Reason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-10T08:00:00Z");

    @Mock GateRepository gates;
    @Mock VehicleRepository vehicles;
    @Mock AccessSessionRepository sessions;
    @Mock GateCommandPublisher gateCommands;
    @Mock DetectionCorrelator correlator;
    @Mock TransactionTemplate tx;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private AccessSessionService service;

    private final SiteEntity site = new SiteEntity("Demo", VerificationMode.FACE_PLATE);
    private final GateEntity entryGate = new GateEntity("in-1", Direction.ENTRY, site);
    private final GateEntity exitGate = new GateEntity("out-1", Direction.EXIT, site);
    private final VehicleEntity vehicle = withId(new VehicleEntity("30A12345"), 1L);

    @BeforeEach
    void setUp() {
        lenient().when(tx.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<?>>getArgument(0).doInTransaction(new SimpleTransactionStatus()));
        service = new AccessSessionService(gates, vehicles, sessions, gateCommands, correlator, tx, meters,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(gates.findByCode("in-1")).thenReturn(Optional.of(entryGate));
        lenient().when(gates.findByCode("out-1")).thenReturn(Optional.of(exitGate));
        lenient().when(vehicles.findByPlateNumber("30A12345")).thenReturn(Optional.of(vehicle));
    }

    @Test
    void entry_opensSessionThenOpensGateAndStartsCooldown() {
        when(sessions.findFirstByVehicleIdAndExitedAtIsNullOrderByEnteredAtDesc(1L)).thenReturn(Optional.empty());
        when(sessions.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0), 42L));
        VerifiedPassage passage = passage("in-1", Direction.ENTRY, "face-1");

        AccessDecision decision = service.handle(passage, true);

        assertThat(decision).isEqualTo(new AccessDecision(true, Reason.ENTRY_GRANTED, 42L));
        var order = inOrder(sessions, gateCommands, correlator);
        order.verify(sessions).saveAndFlush(any());
        order.verify(gateCommands).open("in-1", 42L, "30A12345");
        order.verify(correlator).startCooldown(passage);
        assertThat(meters.counter("access_gate.decisions", "reason", "ENTRY_GRANTED").count()).isEqualTo(1.0);
    }

    @Test
    void entry_whenAlreadyInside_isRejected() {
        when(sessions.findFirstByVehicleIdAndExitedAtIsNullOrderByEnteredAtDesc(1L))
                .thenReturn(Optional.of(openSession("face-1")));

        AccessDecision decision = service.handle(passage("in-1", Direction.ENTRY, "face-1"), true);

        assertThat(decision.reason()).isEqualTo(Reason.ALREADY_INSIDE);
        verify(gateCommands).reject("in-1", Reason.ALREADY_INSIDE, "30A12345");
        verify(gateCommands, never()).open(any(), anyLong(), any());
    }

    @Test
    void entry_losingConcurrentRaceOnUniqueIndex_isRejectedNotFailed() {
        doThrow(new DataIntegrityViolationException("ux_access_session_open_per_vehicle")).when(tx).execute(any());

        AccessDecision decision = service.handle(passage("in-1", Direction.ENTRY, "face-1"), true);

        assertThat(decision.reason()).isEqualTo(Reason.ALREADY_INSIDE);
        verify(gateCommands, never()).open(any(), anyLong(), any());
    }

    @Test
    void exit_withDifferentFace_isRejectedOnFacePlateSites() {
        AccessSessionEntity open = openSession("face-1");
        when(sessions.findFirstByVehicleIdAndExitedAtIsNullOrderByEnteredAtDesc(1L)).thenReturn(Optional.of(open));

        AccessDecision decision = service.handle(passage("out-1", Direction.EXIT, "someone-else"), true);

        assertThat(decision.reason()).isEqualTo(Reason.FACE_MISMATCH);
        assertThat(open.isOpen()).isTrue();
    }

    @Test
    void exit_onPlateOnlySites_ignoresFace() {
        AccessSessionEntity open = openSession(null);
        when(sessions.findFirstByVehicleIdAndExitedAtIsNullOrderByEnteredAtDesc(1L)).thenReturn(Optional.of(open));

        AccessDecision decision = service.handle(passage("out-1", Direction.EXIT, null), false);

        assertThat(decision.reason()).isEqualTo(Reason.EXIT_GRANTED);
        assertThat(open.getExitedAt()).isEqualTo(NOW);
        assertThat(open.getExitGate()).isSameAs(exitGate);
        verify(gateCommands).open("out-1", 7L, "30A12345");
    }

    @Test
    void exit_withoutOpenSession_isRejected() {
        when(sessions.findFirstByVehicleIdAndExitedAtIsNullOrderByEnteredAtDesc(1L)).thenReturn(Optional.empty());

        assertThat(service.handle(passage("out-1", Direction.EXIT, "face-1"), true).reason())
                .isEqualTo(Reason.NO_OPEN_SESSION);
    }

    @Test
    void eventFromEntryCameraOnExitGate_isRejected() {
        assertThat(service.handle(passage("out-1", Direction.ENTRY, "face-1"), true).reason())
                .isEqualTo(Reason.WRONG_DIRECTION);
        verifyNoInteractions(sessions);
    }

    @Test
    void unknownVehicle_isRejected() {
        VerifiedPassage p = new VerifiedPassage("in-1", Direction.ENTRY, "99Z99999", "face-1", 1L);
        when(vehicles.findByPlateNumber("99Z99999")).thenReturn(Optional.empty());

        assertThat(service.handle(p, true).reason()).isEqualTo(Reason.VEHICLE_NOT_REGISTERED);
    }

    private AccessSessionEntity openSession(String face) {
        return withId(AccessSessionEntity.open(vehicle, entryGate, face, NOW.minusSeconds(3600)), 7L);
    }

    private static VerifiedPassage passage(String gate, Direction d, String face) {
        return new VerifiedPassage(gate, d, "30A12345", face, NOW.toEpochMilli());
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
