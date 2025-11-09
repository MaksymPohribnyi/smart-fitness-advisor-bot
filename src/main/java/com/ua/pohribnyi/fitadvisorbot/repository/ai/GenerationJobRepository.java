package com.ua.pohribnyi.fitadvisorbot.repository.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ua.pohribnyi.fitadvisorbot.model.entity.GenerationJob;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

}
