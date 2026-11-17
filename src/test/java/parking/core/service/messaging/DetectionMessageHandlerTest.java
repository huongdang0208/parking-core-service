package parking.core.service.messaging;

import parking.core.service.common.DetectionEvent;
import parking.core.service.common.DetectionType;
import parking.core.service.common.Direction;
import parking.core.service.common.VerificationMode;
import parking.core.service.correlation.LockUnavailableException;
import parking.core.service.persistence.GateEntity;
import io.github.huongdang0208.accessgate.persistence.GateRepository;
import parking.core.service.persistence.SiteEntity;
import parking.core.service.verification.VerificationRouter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DetectionMessageHandlerTest {
    private static final String TOPIC = "gates/hq-in-1/detections";

    private final GateRepository gates = mock(GateRepository.class);
    private final VerificationRouter router = mock(VerificationRouter.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private DetectionMessageHandler handler;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        handler = new DetectionMessageHandler(JsonMapper.builder().build(), validator, gates, router, meters);
        SiteEntity site = new SiteEntity("Demo", VerificationMode.PLATE_ONLY);
        when(gates.findByCode("hq-in-1")).thenReturn(Optional.of(new GateEntity("hq-in-1", Direction.ENTRY, site)));
    }

    @Test
    void validPlate_isRoutedWithTheSitesMode() {
        handler.handle(TOPIC, """
                {"type":"PLATE","gateCode":"hq-in-1","direction":"ENTRY",
                 "plateNumber":"30A-123.45","confidence":0.93,"timestampMs":1700000000000}
                """);

        verify(router).route(new DetectionEvent(DetectionType.PLATE, "hq-in-1", Direction.ENTRY,
                "30A-123.45", null, 0.93, 1_700_000_000_000L), VerificationMode.PLATE_ONLY);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "malformed  | {not json",
            "invalid    | {\"type\":\"PLATE\",\"gateCode\":\"hq-in-1\",\"direction\":\"ENTRY\",\"timestampMs\":1}",
            "invalid    | {\"type\":\"FACE\",\"gateCode\":\"hq-in-1\",\"direction\":\"ENTRY\",\"timestampMs\":1}",
            "invalid    | {\"type\":\"PLATE\",\"gateCode\":\"hq-in-1\",\"direction\":\"ENTRY\",\"plateNumber\":\"30A12345\",\"confidence\":7,\"timestampMs\":1}",
            "malformed  | {\"type\":\"LASER\",\"gateCode\":\"hq-in-1\",\"direction\":\"ENTRY\",\"timestampMs\":1}",
            "unknown_gate | {\"type\":\"PLATE\",\"gateCode\":\"nope\",\"direction\":\"ENTRY\",\"plateNumber\":\"30A12345\",\"timestampMs\":1}"
    })
    void badMessages_areDroppedAndCounted_neverThrown(String reason, String payload) {
        assertThatCode(() -> handler.handle(TOPIC, payload)).doesNotThrowAnyException();

        verifyNoInteractions(router);
        assertThat(meters.counter("access_gate.detections.dropped", "reason", reason).count()).isEqualTo(1.0);
    }

    @Test
    void oversizedPayload_isRejectedBeforeParsing() {
        handler.handle(TOPIC, "x".repeat(DetectionMessageHandler.MAX_PAYLOAD_CHARS + 1));

        assertThat(meters.counter("access_gate.detections.dropped", "reason", "too_large").count()).isEqualTo(1.0);
    }

    @Test
    void lockTimeout_isDroppedNotPropagated() {
        doThrow(new LockUnavailableException("k")).when(router).route(any(), any());

        assertThatCode(() -> handler.handle(TOPIC, """
                {"type":"PLATE","gateCode":"hq-in-1","direction":"ENTRY","plateNumber":"30A12345","timestampMs":1}
                """)).doesNotThrowAnyException();
        assertThat(meters.counter("access_gate.detections.dropped", "reason", "lock_timeout").count()).isEqualTo(1.0);
    }
}
