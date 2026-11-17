package parking.core.service.correlation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class InMemoryCorrelationStore implements CorrelationStore {
    private record Entry(Object value, Instant expiresAt) {
    }

    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryCorrelationStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<PendingPlate> find(String key) {
        return live(key).map(PendingPlate.class::cast);
    }

    @Override
    public void save(String key, PendingPlate plate, Duration ttl) {
        data.put(key, new Entry(plate, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<PendingPlate> remove(String key) {
        Optional<PendingPlate> value = find(key);
        data.remove(key);
        return value;
    }

    @Override
    public void startCooldown(String key, Duration ttl) {
        data.put(key, new Entry(Boolean.TRUE, clock.instant().plus(ttl)));
    }

    @Override
    public boolean isCoolingDown(String key) {
        return live(key).isPresent();
    }

    @Override
    public <T> T withLock(String key, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private Optional<Object> live(String key) {
        Entry e = data.get(key);
        if (e == null || !clock.instant().isBefore(e.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(e.value());
    }
}
