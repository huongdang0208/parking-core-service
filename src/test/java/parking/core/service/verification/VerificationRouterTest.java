package parking.core.service.verification;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.DetectionType;
import parking.core.service.common.Direction;
import parking.core.service.common.VerificationMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationRouterTest {
    static class RecordingStrategy implements VerificationStrategy {
        final VerificationMode mode;
        final List<String> calls = new ArrayList<>();

        RecordingStrategy(VerificationMode mode) {
            this.mode = mode;
        }

        @Override public VerificationMode mode() { return mode; }
        @Override public void onPlate(DetectionEvent plate) { calls.add("plate:" + plate.plateNumber()); }
        @Override public void onFace(DetectionEvent face) { calls.add("face:" + face.faceProfileId()); }
    }

    private final RecordingStrategy facePlate = new RecordingStrategy(VerificationMode.FACE_PLATE);
    private final RecordingStrategy plateOnly = new RecordingStrategy(VerificationMode.PLATE_ONLY);
    private final RecordingStrategy plateRfid = new RecordingStrategy(VerificationMode.PLATE_RFID);
    private final VerificationRouter router = new VerificationRouter(List.of(facePlate, plateOnly, plateRfid));

    @Test
    void routesByEventTypeToTheSitesStrategy() {
        router.route(plate(), VerificationMode.PLATE_ONLY);
        router.route(face(), VerificationMode.FACE_PLATE);

        assertThat(plateOnly.calls).containsExactly("plate:30A12345");
        assertThat(facePlate.calls).containsExactly("face:p1");
        assertThat(plateRfid.calls).isEmpty();
    }

    @Test
    void siteWithoutConfiguredMode_usesFacePlate() {
        router.route(plate(), null);

        assertThat(facePlate.calls).containsExactly("plate:30A12345");
    }

    @Test
    void failsFastWhenAModeHasNoStrategy() {
        assertThatThrownBy(() -> new VerificationRouter(List.of(facePlate, plateOnly)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATE_RFID");
    }

    @Test
    void failsFastOnDuplicateStrategies() {
        assertThatThrownBy(() -> new VerificationRouter(List.of(facePlate, plateOnly, plateRfid,
                new RecordingStrategy(VerificationMode.PLATE_ONLY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two strategies");
    }

    private static DetectionEvent plate() {
        return new DetectionEvent(DetectionType.PLATE, "g", Direction.ENTRY, "30A12345", null, 0.9, 1L);
    }

    private static DetectionEvent face() {
        return new DetectionEvent(DetectionType.FACE, "g", Direction.ENTRY, null, "p1", 0.9, 1L);
    }
}
