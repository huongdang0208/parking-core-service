package parking.core.service.correlation;

public class LockUnavailableException extends RuntimeException {
    public LockUnavailableException(String key) {
        super("Could not acquire correlation lock " + key);
    }
}
