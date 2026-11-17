package parking.core.service.correlation;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.DetectionType;
import parking.core.service.common.Direction;
import parking.core.service.common.VerifiedPassage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionCorrelatorTest {
    private static final String GATE = "hq-in-1";
    private static final long T0 = 1_700_000_000_000L;

    private MutableClock clock;
    private DetectionCorrelator correlator;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.ofEpochMilli(T0));
        CorrelationProperties props = new CorrelationProperties(
                Duration.ofSeconds(8), Duration.ofMinutes(3), Duration.ofSeconds(210), Duration.ofSeconds(15));
        correlator = new DetectionCorrelator(new InMemoryCorrelationStore(clock), props);
    }

    @Nested
    class Pairing {
        @Test
        void plateThenFaceWithinWindow_producesPassage() {
            correlator.ingestPlate(plate("30A-123.45", 0.91, T0));

            Optional<VerifiedPassage> passage = correlator.ingestFace(face("profile-7", T0 + 20_000));

            assertThat(passage).hasValueSatisfying(p -> {
                assertThat(p.plateNumber()).isEqualTo("30A12345");
                assertThat(p.faceProfileId()).isEqualTo("profile-7");
                assertThat(p.direction()).isEqualTo(Direction.ENTRY);
            });
        }

        @Test
        void faceWithoutPlate_isIgnored() {
            assertThat(correlator.ingestFace(face("profile-7", T0))).isEmpty();
        }

        @Test
        void faceOutsideMatchWindow_isDroppedAndConsumesThePlate() {
            correlator.ingestPlate(plate("30A12345", 0.9, T0));

            assertThat(correlator.ingestFace(face("profile-7", T0 + Duration.ofMinutes(4).toMillis()))).isEmpty();
            assertThat(correlator.ingestFace(face("profile-7", T0 + 10))).isEmpty();
        }

        @Test
        void onePlate_canOnlyBePairedOnce() {
            correlator.ingestPlate(plate("30A12345", 0.9, T0));

            assertThat(correlator.ingestFace(face("profile-7", T0 + 1_000))).isPresent();
            assertThat(correlator.ingestFace(face("profile-8", T0 + 2_000))).isEmpty();
        }

        @Test
        void entryAndExitGatesAreIndependent() {
            correlator.ingestPlate(plate("30A12345", 0.9, T0));

            DetectionEvent exitFace = new DetectionEvent(DetectionType.FACE, GATE, Direction.EXIT,
                    null, "profile-7", 0.9, T0 + 1_000);
            assertThat(correlator.ingestFace(exitFace)).isEmpty();
        }
    }

    @Nested
    class BurstSmoothing {
        @Test
        void readingsWithinBurstGap_keepMostConfidentPlate() {
            correlator.ingestPlate(plate("30A12346", 0.62, T0));
            correlator.ingestPlate(plate("30A12345", 0.97, T0 + 1_500));
            correlator.ingestPlate(plate("30A12845", 0.70, T0 + 3_000));

            assertThat(correlator.ingestFace(face("p", T0 + 4_000)))
                    .map(VerifiedPassage::plateNumber)
                    .contains("30A12345");
        }

        @Test
        void readingAfterBurstGap_isTreatedAsNewVehicle() {
            correlator.ingestPlate(plate("30A12345", 0.99, T0));
            correlator.ingestPlate(plate("51F67890", 0.60, T0 + 9_000));

            assertThat(correlator.ingestFace(face("p", T0 + 10_000)))
                    .map(VerifiedPassage::plateNumber)
                    .contains("51F67890");
        }
    }

    @Nested
    class Cooldown {
        @Test
        void afterPassage_trailingReadingsAreIgnoredUntilCooldownExpires() {
            correlator.ingestPlate(plate("30A12345", 0.9, T0));
            VerifiedPassage passage = correlator.ingestFace(face("p", T0 + 1_000)).orElseThrow();
            correlator.startCooldown(passage);

            correlator.ingestPlate(plate("30A12345", 0.9, T0 + 2_000));
            assertThat(correlator.ingestFace(face("p", T0 + 3_000))).isEmpty();
            assertThat(correlator.ingestPlateOnly(plate("30A12345", 0.9, T0 + 3_000))).isEmpty();

            clock.advance(Duration.ofSeconds(16));
            assertThat(correlator.ingestPlateOnly(plate("30A12345", 0.9, T0 + 20_000))).isPresent();
        }
    }

    @Test
    void concurrentFaces_pairWithThePlateExactlyOnce() throws Exception {
        correlator.ingestPlate(plate("30A12345", 0.9, T0));
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            var futures = new java.util.ArrayList<Future<Optional<VerifiedPassage>>>();
            for (int i = 0; i < threads; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return correlator.ingestFace(face("p" + n, T0 + 1_000));
                }));
            }
            start.countDown();
            long matched = 0;
            for (Future<Optional<VerifiedPassage>> f : futures) {
                if (f.get(5, TimeUnit.SECONDS).isPresent()) {
                    matched++;
                }
            }
            assertThat(matched).isEqualTo(1);
        }
    }

    private static DetectionEvent plate(String plate, double conf, long ts) {
        return new DetectionEvent(DetectionType.PLATE, GATE, Direction.ENTRY, plate, null, conf, ts);
    }

    private static DetectionEvent face(String profile, long ts) {
        return new DetectionEvent(DetectionType.FACE, GATE, Direction.ENTRY, null, profile, 0.95, ts);
    }
}
