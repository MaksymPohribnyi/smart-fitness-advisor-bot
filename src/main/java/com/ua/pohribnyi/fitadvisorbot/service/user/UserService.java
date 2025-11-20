package com.ua.pohribnyi.fitadvisorbot.service.user;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ua.pohribnyi.fitadvisorbot.model.dto.UserDto;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.UserProfile;
import com.ua.pohribnyi.fitadvisorbot.repository.strava.StravaTokenRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserProfileRepository;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserRepository;
import com.ua.pohribnyi.fitadvisorbot.service.ai.SyntheticDataService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository; 
    private final SyntheticDataService syntheticDataService;
	private final StravaTokenRepository stravaTokenRepository;
	private final UserSessionService userSessionService;

	@Transactional
	public User findOrCreateUser(org.telegram.telegrambots.meta.api.objects.User telegramUser) {
		return userRepository.findByTelegramUserId(telegramUser.getId()).orElseGet(() -> createUser(telegramUser));
	}

	private User createUser(org.telegram.telegrambots.meta.api.objects.User telegramUser) {
		log.info("Creating new user for telegram_id: {}", telegramUser.getId());

		User newUser = User.builder()
				.telegramUserId(telegramUser.getId())
				.telegramUsername(telegramUser.getUserName())
				.firstName(telegramUser.getFirstName())
				.lastName(telegramUser.getLastName())
				.languageCode(telegramUser.getLanguageCode())
				.build();

		User savedUser = userRepository.save(newUser);

		// Create the associated session
		// The service handles setting the initial state (ONBOARDING_START)
		userSessionService.createSessionForUser(savedUser);

		return savedUser;
	}

	@Transactional(readOnly = true)
	public Optional<UserDto> findByTelegramUserId(Long telegramUserId) {
		return userRepository.findByTelegramUserId(telegramUserId).map(this::convertToDto);
	}

	public Optional<User> findEntityByTelegramUserId(Long telegramUserId) {
		return userRepository.findByTelegramUserId(telegramUserId);
	}

	@Transactional(readOnly = true)
	public boolean isStravaConnected(Long telegramUserId) {
		boolean isConnected = stravaTokenRepository.existsByUser_TelegramUserId(telegramUserId);

		log.debug("Checking Strava connection for user {}: {}", telegramUserId, isConnected);
		return isConnected;
	}

	private UserDto convertToDto(User user) {
		return UserDto.fromEntity(user);
	}

	public String getUserLanguageCode(Long chatId) {
		return findByTelegramUserId(chatId).map(UserDto::languageCode).orElse("uk");
	}
}
