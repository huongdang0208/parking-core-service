package parking.core.service.verification;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.VerificationMode;
import parking.core.service.correlation.DetectionCorrelator;
import parking.core.service.session.AccessSessionService;
import org.springframework.stereotype.Component;

@Component
public class FacePlateStrategy implements VerificationStrategy {
    private final DetectionCorrelator correlator;
    private final AccessSessionService sessions;

    public FacePlateStrategy(DetectionCorrelator correlator, AccessSessionService sessions) {
        this.correlator = correlator;
        this.sessions = sessions;
    }

    @Override
    public VerificationMode mode() {
        return VerificationMode.FACE_PLATE;
    }

    @Override
    public void onPlate(DetectionEvent plate) {
        correlator.ingestPlate(plate);
    }

    @Override
    public void onFace(DetectionEvent face) {
        correlator.ingestFace(face).ifPresent(passage -> sessions.handle(passage, true));
    }
}
