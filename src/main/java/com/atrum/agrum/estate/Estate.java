package com.atrum.agrum.estate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "estates")
public class Estate {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private String id; // e.g., "EST_NW_01" or "ESTATE_A"

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public Estate() {}

    public Estate(String id, String name) {
        this.id = id;
        this.name = name;
    }

}