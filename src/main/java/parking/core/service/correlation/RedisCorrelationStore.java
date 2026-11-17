package parking.core.service.correlation;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

@Component
public class RedisCorrelationStore implements CorrelationStore {
    private static final Duration LOCK_LEASE = Duration.ofSeconds(5);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);
    private static final RedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    public RedisCorrelationStore(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public Optional<PendingPlate> find(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key)).map(this::read);
    }

    @Override
    public void save(String key, PendingPlate plate, Duration ttl) {
        redis.opsForValue().set(key, json.writeValueAsString(plate), ttl);
    }

    @Override
    public Optional<PendingPlate> remove(String key) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key)).map(this::read);
    }

    @Override
    public void startCooldown(String key, Duration ttl) {
        redis.opsForValue().set(key, "1", ttl);
    }

    @Override
    public boolean isCoolingDown(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    @Override
    public <T> T withLock(String key, Supplier<T> action) {
        String lockKey = key + ":lock";
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + LOCK_WAIT.toNanos();
        while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, LOCK_LEASE))) {
            if (System.nanoTime() > deadline) {
                throw new LockUnavailableException(lockKey);
            }
            LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
        }
        try {
            return action.get();
        } finally {
            redis.execute(RELEASE_IF_OWNER, List.of(lockKey), token);
        }
    }

    private PendingPlate read(String raw) {
        return json.readValue(raw, PendingPlate.class);
    }
}
