package com.ua.pohribnyi.fitadvisorbot.repository.diary;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyAdviceJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

@Repository
public interface DailyAdviceJobRepository extends JpaRepository<DailyAdviceJob, Long> {

	Optional<DailyAdviceJob> findByUserAndDate(User user, LocalDate date);

}
