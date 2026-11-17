package parking.core.service.messaging;

import parking.core.service.common.DetectionEvent;
import parking.core.service.correlation.LockUnavailableException;
import parking.core.service.persistence.GateEntity;
import io.github.huongdang0208.accessgate.persistence.GateRepository;
import parking.core.service.verification.VerificationRouter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DetectionMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(DetectionMessageHandler.class);
    static final int MAX_PAYLOAD_CHARS = 16 * 1024;

    private final JsonMapper json;
    private final Validator validator;
    private final GateRepository gates;
    private final VerificationRouter router;
    private final MeterRegistry meters;

    public DetectionMessageHandler(JsonMapper json, Validator validator, GateRepository gates,
                                   VerificationRouter router, MeterRegistry meters) {
        this.json = json;
        this.validator = validator;
        this.gates = gates;
        this.router = router;
        this.meters = meters;
    }

    public void handle(String topic, String payload) {
        Optional<DetectionEvent> event = parse(topic, payload);
        if (event.isEmpty()) {
            return;
        }
        DetectionEvent e = event.get();
        Optional<GateEntity> gate = gates.findByCode(e.gateCode());
        if (gate.isEmpty()) {
            drop("unknown_gate", "Unknown gate {} on {}", e.gateCode(), topic);
            return;
        }
        try {
            router.route(e, gate.get().getSite().getVerificationMode());
            meters.counter("access_gate.detections", "type", e.type().name()).increment();
        } catch (LockUnavailableException ex) {
            drop("lock_timeout", "Gate {} busy, dropped {} event: {}", e.gateCode(), e.type(), ex.getMessage());
        }
    }

    private Optional<DetectionEvent> parse(String topic, String payload) {
        if (payload == null || payload.length() > MAX_PAYLOAD_CHARS) {
            drop("too_large", "Payload on {} empty or larger than {} chars", topic, MAX_PAYLOAD_CHARS);
            return Optional.empty();
        }
        DetectionEvent event;
        try {
            event = json.readValue(payload, DetectionEvent.class);
        } catch (JacksonException ex) {
            drop("malformed", "Malformed JSON on {}: {}", topic, ex.getOriginalMessage());
            return Optional.empty();
        }
        Set<ConstraintViolation<DetectionEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            drop("invalid", "Invalid detection on {}: {}", topic, details);
            return Optional.empty();
        }
        return Optional.of(event);
    }

    private void drop(String reason, String message, Object... args) {
        meters.counter("access_gate.detections.dropped", "reason", reason).increment();
        log.warn(message, args);
    }
}
