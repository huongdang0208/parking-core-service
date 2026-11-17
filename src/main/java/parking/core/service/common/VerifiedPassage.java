package parking.core.service.common;

public record VerifiedPassage(
        String gateCode,
        Direction direction,
        String plateNumber,
        String faceProfileId,
        long timestampMs) {
}
