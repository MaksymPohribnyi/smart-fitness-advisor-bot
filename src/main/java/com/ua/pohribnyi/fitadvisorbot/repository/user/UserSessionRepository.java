package com.ua.pohribnyi.fitadvisorbot.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserSession;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

	Optional<UserSession> findByUser(User user);

}
