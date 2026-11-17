package parking.core.service.session;

public interface GateCommandPublisher {
    void open(String gateCode, long sessionId, String plateNumber);

    void reject(String gateCode, AccessDecision.Reason reason, String plateNumber);
}
