package parking.core.service.correlation;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public interface CorrelationStore {
    Optional<PendingPlate> find(String key);

    void save(String key, PendingPlate plate, Duration ttl);

    Optional<PendingPlate> remove(String key);

    void startCooldown(String key, Duration ttl);

    boolean isCoolingDown(String key);

    <T> T withLock(String key, Supplier<T> action);
}
