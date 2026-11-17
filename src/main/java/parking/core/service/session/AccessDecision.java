package parking.core.service.session;

public record AccessDecision(boolean granted, Reason reason, Long sessionId) {
    public enum Reason {
        ENTRY_GRANTED,
        EXIT_GRANTED,
        UNKNOWN_GATE,
        WRONG_DIRECTION,
        VEHICLE_NOT_REGISTERED,
        ALREADY_INSIDE,
        NO_OPEN_SESSION,
        FACE_MISMATCH
    }

    static AccessDecision granted(Reason reason, Long sessionId) {
        return new AccessDecision(true, reason, sessionId);
    }

    static AccessDecision rejected(Reason reason) {
        return new AccessDecision(false, reason, null);
    }
}
