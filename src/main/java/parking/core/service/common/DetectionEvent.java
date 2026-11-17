package parking.core.service.common;

import jakarta.validation.constraints.*;

public record DetectionEvent(
        @NotNull DetectionType type,
        @NotBlank @Size(max = 64) String gateCode,
        @NotNull Direction direction,
        @Size(max = 20) String plateNumber,
        @Size(max = 64) String faceProfileId,
        @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        @Positive long timestampMs) {
    @AssertTrue(message = "PLATE events need plateNumber, FACE events need faceProfileId")
    public boolean isPayloadConsistent() {
        if (type == null) {
            return true;
        }
        return switch (type) {
            case PLATE -> PlateNormalizer.normalize(plateNumber) != null;
            case FACE -> faceProfileId != null && !faceProfileId.isBlank();
        };
    }

    public double confidenceOrZero() {
        return confidence == null ? 0.0 : confidence;
    }

    public String normalizedPlate() {
        return PlateNormalizer.normalize(plateNumber);
    }
}
