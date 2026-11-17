package parking.core.service.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "access_session")
public class AccessSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id")
    private VehicleEntity vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_gate_id")
    private GateEntity entryGate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_gate_id")
    private GateEntity exitGate;

    @Column(name = "entry_face_profile_id", length = 64)
    private String entryFaceProfileId;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    @Column(name = "exited_at")
    private Instant exitedAt;

    @Version
    private long version;

    protected AccessSessionEntity() {
    }

    public static AccessSessionEntity open(VehicleEntity vehicle, GateEntity entryGate,
                                           String faceProfileId, Instant at) {
        AccessSessionEntity s = new AccessSessionEntity();
        s.vehicle = vehicle;
        s.entryGate = entryGate;
        s.entryFaceProfileId = faceProfileId;
        s.enteredAt = at;
        return s;
    }

    public void close(GateEntity exitGate, Instant at) {
        if (exitedAt != null) {
            throw new IllegalStateException("Session " + id + " is already closed");
        }
        this.exitGate = exitGate;
        this.exitedAt = at;
    }

    public Long getId() { return id; }
    public VehicleEntity getVehicle() { return vehicle; }
    public GateEntity getEntryGate() { return entryGate; }
    public GateEntity getExitGate() { return exitGate; }
    public String getEntryFaceProfileId() { return entryFaceProfileId; }
    public Instant getEnteredAt() { return enteredAt; }
    public Instant getExitedAt() { return exitedAt; }
    public boolean isOpen() { return exitedAt == null; }
}
