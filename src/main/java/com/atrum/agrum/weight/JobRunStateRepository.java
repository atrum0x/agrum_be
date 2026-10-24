package com.atrum.agrum.weight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRunStateRepository extends JpaRepository<JobRunState, String> {
}
