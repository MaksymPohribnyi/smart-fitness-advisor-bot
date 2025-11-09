package com.ua.pohribnyi.fitadvisorbot.repository.data;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ua.pohribnyi.fitadvisorbot.model.entity.Activity;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

}
