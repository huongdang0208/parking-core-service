package parking.core.service.correlation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("access-gate.correlation")
public record CorrelationProperties(
        Duration burstGap,
        Duration matchWindow,
        Duration pendingTtl,
        Duration postMatchCooldown) {
    public CorrelationProperties {
        burstGap = burstGap != null ? burstGap : Duration.ofSeconds(8);
        matchWindow = matchWindow != null ? matchWindow : Duration.ofMinutes(3);
        pendingTtl = pendingTtl != null ? pendingTtl : matchWindow.plusSeconds(30);
        postMatchCooldown = postMatchCooldown != null ? postMatchCooldown : Duration.ofSeconds(15);
        if (pendingTtl.compareTo(matchWindow) <= 0) {
            throw new IllegalArgumentException("pendingTtl must be longer than matchWindow");
        }
    }
}
