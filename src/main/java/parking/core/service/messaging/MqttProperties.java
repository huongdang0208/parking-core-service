package parking.core.service.messaging;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("access-gate.mqtt")
public record MqttProperties(
        @NotBlank String brokerUri,
        @NotBlank String clientId,
        String username,
        String password,
        @NotBlank String detectionTopic,
        @NotBlank String commandTopicPrefix,
        @Min(0) @Max(2) int qos) {
    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    public String commandTopic(String gateCode) {
        return commandTopicPrefix + gateCode + "/commands";
    }
}
