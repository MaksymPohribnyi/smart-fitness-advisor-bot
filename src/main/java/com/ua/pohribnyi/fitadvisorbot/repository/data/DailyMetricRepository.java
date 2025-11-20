package com.ua.pohribnyi.fitadvisorbot.repository.data;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyMetric;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

public interface DailyMetricRepository extends JpaRepository<DailyMetric, Long> {

	/**
	 * Fetches daily metrics for trend analysis.
	 */
	@Query("SELECT d FROM DailyMetric d WHERE d.user = :user AND d.date >= :since ORDER BY d.date ASC")
	List<DailyMetric> findMetricsByUserAndDateAfter(@Param("user") User user, @Param("since") LocalDate since);

}