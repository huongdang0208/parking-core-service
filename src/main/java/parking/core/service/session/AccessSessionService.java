package parking.core.service.session;

import parking.core.service.common.Direction;
import parking.core.service.common.VerifiedPassage;
import parking.core.service.correlation.DetectionCorrelator;
import io.github.huongdang0208.accessgate.persistence.*;
import parking.core.service.persistence.AccessSessionEntity;
import parking.core.service.persistence.GateEntity;
import parking.core.service.persistence.VehicleEntity;
import parking.core.service.session.AccessDecision.Reason;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

@Service
public class AccessSessionService {
    private static final Logger log = LoggerFactory.getLogger(AccessSessionService.class);

    private final GateRepository gates;
    private final VehicleRepository vehicles;
    private final AccessSessionRepository sessions;
    private final GateCommandPublisher gateCommands;
    private final DetectionCorrelator correlator;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;
    private final Clock clock;

    public AccessSessionService(GateRepository gates, VehicleRepository vehicles, AccessSessionRepository sessions,
                                GateCommandPublisher gateCommands, DetectionCorrelator correlator,
                                TransactionTemplate tx, MeterRegistry meters, Clock clock) {
        this.gates = gates;
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.gateCommands = gateCommands;
        this.correlator = correlator;
        this.tx = tx;
        this.meters = meters;
        this.clock = clock;
    }

    public AccessDecision handle(VerifiedPassage passage, boolean faceRequired) {
        AccessDecision decision;
        try {
            decision = tx.execute(status -> decide(passage, faceRequired));
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent entry for plate {} rejected by DB constraint", passage.plateNumber());
            decision = AccessDecision.rejected(Reason.ALREADY_INSIDE);
        }
        Objects.requireNonNull(decision);
        publish(passage, decision);
        meters.counter("access_gate.decisions", "reason", decision.reason().name()).increment();
        return decision;
    }

    private AccessDecision decide(VerifiedPassage passage, boolean faceRequired) {
        Optional<GateEntity> gate = gates.findByCode(passage.gateCode());
        if (gate.isEmpty()) {
            return AccessDecision.rejected(Reason.UNKNOWN_GATE);
        }
        if (gate.get().getDirection() != passage.direction()) {
            return AccessDecision.rejected(Reason.WRONG_DIRECTION);
        }
        Optional<VehicleEntity> vehicle = vehicles.findByPlateNumber(passage.plateNumber());
        if (vehicle.isEmpty()) {
            return AccessDecision.rejected(Reason.VEHICLE_NOT_REGISTERED);
        }
        Optional<AccessSessionEntity> open =
                sessions.findFirstByVehicleIdAndExitedAtIsNullOrderByEnteredAtDesc(vehicle.get().getId());

        return passage.direction() == Direction.ENTRY
                ? enter(passage, gate.get(), vehicle.get(), open)
                : exit(passage, gate.get(), open, faceRequired);
    }

    private AccessDecision enter(VerifiedPassage p, GateEntity gate, VehicleEntity vehicle,
                                 Optional<AccessSessionEntity> open) {
        if (open.isPresent()) {
            return AccessDecision.rejected(Reason.ALREADY_INSIDE);
        }
        AccessSessionEntity session = sessions.saveAndFlush(
                AccessSessionEntity.open(vehicle, gate, p.faceProfileId(), clock.instant()));
        return AccessDecision.granted(Reason.ENTRY_GRANTED, session.getId());
    }

    private AccessDecision exit(VerifiedPassage p, GateEntity gate, Optional<AccessSessionEntity> open,
                                boolean faceRequired) {
        if (open.isEmpty()) {
            return AccessDecision.rejected(Reason.NO_OPEN_SESSION);
        }
        AccessSessionEntity session = open.get();
        if (faceRequired && !Objects.equals(session.getEntryFaceProfileId(), p.faceProfileId())) {
            return AccessDecision.rejected(Reason.FACE_MISMATCH);
        }
        session.close(gate, clock.instant());
        return AccessDecision.granted(Reason.EXIT_GRANTED, session.getId());
    }

    private void publish(VerifiedPassage p, AccessDecision decision) {
        if (decision.granted()) {
            gateCommands.open(p.gateCode(), decision.sessionId(), p.plateNumber());
            correlator.startCooldown(p);
            log.info("{} gate={} plate={} session={}", decision.reason(), p.gateCode(), p.plateNumber(),
                    decision.sessionId());
        } else {
            gateCommands.reject(p.gateCode(), decision.reason(), p.plateNumber());
            log.warn("Rejected gate={} plate={} reason={}", p.gateCode(), p.plateNumber(), decision.reason());
        }
    }
}
