package parking.core.service.verification;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.VerificationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlateRfidStrategy implements VerificationStrategy {
    private static final Logger log = LoggerFactory.getLogger(PlateRfidStrategy.class);

    private final PlateOnlyStrategy fallback;

    public PlateRfidStrategy(PlateOnlyStrategy fallback) {
        this.fallback = fallback;
    }

    @Override
    public VerificationMode mode() {
        return VerificationMode.PLATE_RFID;
    }

    @Override
    public void onPlate(DetectionEvent plate) {
        log.warn("Gate {}: PLATE_RFID has no RFID feed yet, falling back to PLATE_ONLY", plate.gateCode());
        fallback.onPlate(plate);
    }

    @Override
    public void onFace(DetectionEvent face) {
        fallback.onFace(face);
    }
}
