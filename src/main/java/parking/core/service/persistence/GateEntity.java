package parking.core.service.persistence;

import parking.core.service.common.Direction;
import jakarta.persistence.*;

@Entity
@Table(name = "gate")
public class GateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id")
    private SiteEntity site;

    protected GateEntity() {
    }

    public GateEntity(String code, Direction direction, SiteEntity site) {
        this.code = code;
        this.direction = direction;
        this.site = site;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Direction getDirection() { return direction; }
    public SiteEntity getSite() { return site; }
}
