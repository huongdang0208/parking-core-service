package parking.core.service.messaging;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("mqtt")
public class MqttHealthIndicator implements HealthIndicator {
    private final MqttConnection mqtt;
    private final MqttProperties props;

    public MqttHealthIndicator(MqttConnection mqtt, MqttProperties props) {
        this.mqtt = mqtt;
        this.props = props;
    }

    @Override
    public Health health() {
        Health.Builder builder = mqtt.isConnected() ? Health.up() : Health.down();
        return builder.withDetail("broker", props.brokerUri()).build();
    }
}
