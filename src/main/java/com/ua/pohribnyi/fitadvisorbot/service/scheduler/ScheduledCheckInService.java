package com.ua.pohribnyi.fitadvisorbot.service.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import com.ua.pohribnyi.fitadvisorbot.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.repository.user.UserRepository;
import com.ua.pohribnyi.fitadvisorbot.service.analytics.diary.DiaryService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledCheckInService {

	private final UserRepository userRepository;
	private final UserSessionService sessionService;
	private final DiaryService diaryService;
	private final FitnessAdvisorBotService botService;

	// 08:00 AM Kyiv Time
	@Scheduled(cron = "0 0 8 * * *", zone = "Europe/Kyiv")
	public void triggerMorningCheckIn() {
		log.info("Triggering Morning Daily Check-in...");

		List<User> users = userRepository.findAll();

		for (User user : users) {
			UserState state = sessionService.getActiveState(user);
			// Send only if user is not stuck in onboarding
			if (state == UserState.DEFAULT || state == UserState.ONBOARDING_COMPLETED) {
				try {
					SendMessage msg = diaryService.startDailyCheckIn(user, false);
					botService.sendMessage(msg);
				} catch (Exception e) {
					log.error("Failed to start check-in for user {}", user.getId(), e);
				}
			}
		}
	}
}