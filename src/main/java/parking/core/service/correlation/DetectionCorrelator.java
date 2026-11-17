package parking.core.service.correlation;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.Direction;
import parking.core.service.common.VerifiedPassage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DetectionCorrelator {
    private static final Logger log = LoggerFactory.getLogger(DetectionCorrelator.class);

    private final CorrelationStore store;
    private final CorrelationProperties props;

    public DetectionCorrelator(CorrelationStore store, CorrelationProperties props) {
        this.store = store;
        this.props = props;
    }

    public void ingestPlate(DetectionEvent plate) {
        String key = pendingKey(plate.gateCode(), plate.direction());
        if (store.isCoolingDown(cooldownKey(plate.gateCode(), plate.direction()))) {
            log.debug("Gate {} in cooldown, ignoring plate {}", plate.gateCode(), plate.normalizedPlate());
            return;
        }
        store.withLock(key, () -> {
            long ts = plate.timestampMs();
            PendingPlate next = store.find(key)
                    .filter(p -> ts - p.lastSeenMs() <= props.burstGap().toMillis())
                    .map(p -> p.merge(plate.normalizedPlate(), plate.confidenceOrZero(), ts))
                    .orElseGet(() -> PendingPlate.first(plate.normalizedPlate(), plate.confidenceOrZero(), ts));
            store.save(key, next, props.pendingTtl());
            log.debug("Gate {} pending plate {} (conf={}, hits={})",
                    plate.gateCode(), next.plateNumber(), next.confidence(), next.hits());
            return null;
        });
    }

    public Optional<VerifiedPassage> ingestFace(DetectionEvent face) {
        String key = pendingKey(face.gateCode(), face.direction());
        return store.withLock(key, () -> {
            Optional<PendingPlate> pending = store.remove(key);
            if (pending.isEmpty()) {
                log.debug("Gate {} face {} without pending plate", face.gateCode(), face.faceProfileId());
                return Optional.empty();
            }
            long delta = Math.abs(face.timestampMs() - pending.get().lastSeenMs());
            if (delta > props.matchWindow().toMillis()) {
                log.info("Gate {} face arrived {} ms after plate, outside match window — dropped",
                        face.gateCode(), delta);
                return Optional.empty();
            }
            return Optional.of(new VerifiedPassage(face.gateCode(), face.direction(),
                    pending.get().plateNumber(), face.faceProfileId(), face.timestampMs()));
        });
    }

    public Optional<VerifiedPassage> ingestPlateOnly(DetectionEvent plate) {
        if (store.isCoolingDown(cooldownKey(plate.gateCode(), plate.direction()))) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedPassage(plate.gateCode(), plate.direction(),
                plate.normalizedPlate(), null, plate.timestampMs()));
    }

    public void startCooldown(VerifiedPassage passage) {
        store.startCooldown(cooldownKey(passage.gateCode(), passage.direction()), props.postMatchCooldown());
    }

    static String pendingKey(String gateCode, Direction direction) {
        return "ag:pending:" + direction + ":" + gateCode;
    }

    static String cooldownKey(String gateCode, Direction direction) {
        return "ag:cooldown:" + direction + ":" + gateCode;
    }
}
