package parking.core.service.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicle")
public class VehicleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plate_number", nullable = false, unique = true, length = 20)
    private String plateNumber;

    protected VehicleEntity() {
    }

    public VehicleEntity(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public Long getId() { return id; }
    public String getPlateNumber() { return plateNumber; }
}
