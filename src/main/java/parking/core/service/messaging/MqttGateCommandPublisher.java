package parking.core.service.messaging;

import parking.core.service.session.AccessDecision;
import parking.core.service.session.GateCommandPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MqttGateCommandPublisher implements GateCommandPublisher {
    private static final Logger log = LoggerFactory.getLogger(MqttGateCommandPublisher.class);

    private final MqttConnection mqtt;
    private final MqttProperties props;
    private final JsonMapper json;
    private final Clock clock;

    public MqttGateCommandPublisher(MqttConnection mqtt, MqttProperties props, JsonMapper json, Clock clock) {
        this.mqtt = mqtt;
        this.props = props;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void open(String gateCode, long sessionId, String plateNumber) {
        Map<String, Object> cmd = base("OPEN", plateNumber);
        cmd.put("sessionId", sessionId);
        send(gateCode, cmd);
    }

    @Override
    public void reject(String gateCode, AccessDecision.Reason reason, String plateNumber) {
        Map<String, Object> cmd = base("SHOW_REJECTION", plateNumber);
        cmd.put("reason", reason.name());
        send(gateCode, cmd);
    }

    private Map<String, Object> base(String command, String plate) {
        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("command", command);
        cmd.put("plate", plate);
        cmd.put("issuedAt", clock.instant().toString());
        return cmd;
    }

    private void send(String gateCode, Map<String, Object> cmd) {
        try {
            mqtt.publish(props.commandTopic(gateCode), json.writeValueAsString(cmd));
        } catch (IllegalStateException e) {
            log.error("Could not deliver {} to gate {}: {}", cmd.get("command"), gateCode, e.getMessage());
        }
    }
}
