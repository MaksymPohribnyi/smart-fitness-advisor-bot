package com.ua.pohribnyi.fitadvisorbot.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByTelegramUserId(Long telegramUserId);
	
	
}
