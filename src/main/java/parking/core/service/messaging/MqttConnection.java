package parking.core.service.messaging;

import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MqttConnection implements SmartLifecycle, MqttCallbackExtended {
    private static final Logger log = LoggerFactory.getLogger(MqttConnection.class);

    private final MqttProperties props;
    private final DetectionMessageHandler handler;
    private final MqttClient client;
    private volatile boolean running;

    public MqttConnection(MqttProperties props, DetectionMessageHandler handler) throws MqttException {
        this.props = props;
        this.handler = handler;
        this.client = new MqttClient(props.brokerUri(), props.clientId(), null);
        this.client.setCallback(this);
    }

    @Override
    public void start() {
        running = true;
        tryConnect();
    }

    @Scheduled(fixedDelayString = "${access-gate.mqtt.retry-interval:PT10S}")
    void retryInitialConnect() {
        if (running && !client.isConnected()) {
            tryConnect();
        }
    }

    private synchronized void tryConnect() {
        if (client.isConnected()) {
            return;
        }
        try {
            client.connect(options());
        } catch (MqttException e) {
            log.warn("MQTT broker {} unreachable ({}), will retry", props.brokerUri(), e.getMessage());
        }
    }

    private MqttConnectOptions options() {
        MqttConnectOptions o = new MqttConnectOptions();
        o.setAutomaticReconnect(true);
        o.setCleanSession(false);
        o.setConnectionTimeout(10);
        o.setKeepAliveInterval(30);
        if (props.hasCredentials()) {
            o.setUserName(props.username());
            o.setPassword(props.password() == null ? new char[0] : props.password().toCharArray());
        }
        return o;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(props.detectionTopic(), props.qos());
            log.info("MQTT {} to {}, subscribed to {}", reconnect ? "reconnected" : "connected",
                    serverURI, props.detectionTopic());
        } catch (MqttException e) {
            log.error("MQTT subscribe to {} failed", props.detectionTopic(), e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            handler.handle(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            log.error("Unhandled error processing message on {}", topic, e);
        }
    }

    public void publish(String topic, String payload) {
        if (!client.isConnected()) {
            throw new IllegalStateException("MQTT not connected, cannot publish to " + topic);
        }
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(props.qos());
            client.publish(topic, msg);
        } catch (MqttException e) {
            throw new IllegalStateException("MQTT publish to " + topic + " failed", e);
        }
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            log.warn("MQTT shutdown: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
