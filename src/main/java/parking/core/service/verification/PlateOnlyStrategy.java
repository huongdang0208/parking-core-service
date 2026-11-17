package parking.core.service.verification;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.VerificationMode;
import parking.core.service.correlation.DetectionCorrelator;
import parking.core.service.session.AccessSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlateOnlyStrategy implements VerificationStrategy {
    private static final Logger log = LoggerFactory.getLogger(PlateOnlyStrategy.class);

    private final DetectionCorrelator correlator;
    private final AccessSessionService sessions;

    public PlateOnlyStrategy(DetectionCorrelator correlator, AccessSessionService sessions) {
        this.correlator = correlator;
        this.sessions = sessions;
    }

    @Override
    public VerificationMode mode() {
        return VerificationMode.PLATE_ONLY;
    }

    @Override
    public void onPlate(DetectionEvent plate) {
        correlator.ingestPlateOnly(plate).ifPresent(passage -> sessions.handle(passage, false));
    }

    @Override
    public void onFace(DetectionEvent face) {
        log.debug("Gate {} is PLATE_ONLY, face event ignored", face.gateCode());
    }
}
