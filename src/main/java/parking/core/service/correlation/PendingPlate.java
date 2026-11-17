package parking.core.service.correlation;

public record PendingPlate(
        String plateNumber,
        double confidence,
        long firstSeenMs,
        long lastSeenMs,
        int hits) {
    public static PendingPlate first(String plate, double confidence, long ts) {
        return new PendingPlate(plate, confidence, ts, ts, 1);
    }

    public PendingPlate merge(String plate, double newConfidence, long ts) {
        boolean better = newConfidence > confidence;
        return new PendingPlate(
                better ? plate : plateNumber,
                Math.max(confidence, newConfidence),
                firstSeenMs,
                Math.max(lastSeenMs, ts),
                hits + 1);
    }
}
