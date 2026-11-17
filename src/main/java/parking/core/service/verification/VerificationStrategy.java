package parking.core.service.verification;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.VerificationMode;

public interface VerificationStrategy {
    VerificationMode mode();

    void onPlate(DetectionEvent plate);

    void onFace(DetectionEvent face);
}
