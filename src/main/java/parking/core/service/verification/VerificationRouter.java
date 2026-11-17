package parking.core.service.verification;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.VerificationMode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class VerificationRouter {
    static final VerificationMode DEFAULT_MODE = VerificationMode.FACE_PLATE;

    private final Map<VerificationMode, VerificationStrategy> strategies = new EnumMap<>(VerificationMode.class);

    public VerificationRouter(List<VerificationStrategy> available) {
        for (VerificationStrategy s : available) {
            VerificationStrategy previous = strategies.put(s.mode(), s);
            if (previous != null) {
                throw new IllegalStateException("Two strategies registered for " + s.mode() + ": "
                        + previous.getClass().getSimpleName() + ", " + s.getClass().getSimpleName());
            }
        }
        List<VerificationMode> missing = Arrays.stream(VerificationMode.values())
                .filter(m -> !strategies.containsKey(m))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No verification strategy for modes " + missing);
        }
    }

    public void route(DetectionEvent event, VerificationMode siteMode) {
        VerificationStrategy strategy = strategies.get(siteMode != null ? siteMode : DEFAULT_MODE);
        switch (event.type()) {
            case PLATE -> strategy.onPlate(event);
            case FACE -> strategy.onFace(event);
        }
    }
}
