package parking.core.service.persistence;

import parking.core.service.common.VerificationMode;
import jakarta.persistence.*;

@Entity
@Table(name = "site")
public class SiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_mode", nullable = false, length = 20)
    private VerificationMode verificationMode = VerificationMode.FACE_PLATE;

    protected SiteEntity() {
    }

    public SiteEntity(String name, VerificationMode verificationMode) {
        this.name = name;
        this.verificationMode = verificationMode;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public VerificationMode getVerificationMode() { return verificationMode; }

    public void changeVerificationMode(VerificationMode mode) {
        this.verificationMode = mode;
    }
}
