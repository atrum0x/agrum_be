package com.atrum.agrum.workType;

import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.security.EstateSecuredEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "work_types")
public class WorkType extends EstateSecuredEntity {

    // The code is the key and cannot be changed once created.
    // May move to a generated id later (same as collection points) so it becomes editable.
    @Id
    @Column(name = "work_type", length = 50)
    private String workType;

    // Written through EstateSecuredEntity.estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    @Column(name = "weighing_required", nullable = false)
    private boolean weighingRequired;
}
