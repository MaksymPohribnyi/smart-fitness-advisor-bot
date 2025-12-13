package com.ua.pohribnyi.fitadvisorbot.service.analytics.diary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.ua.pohribnyi.fitadvisorbot.enums.UserState;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyAdviceJob;
import com.ua.pohribnyi.fitadvisorbot.model.entity.DailyAdviceJob.Status;
import com.ua.pohribnyi.fitadvisorbot.model.entity.user.User;
import com.ua.pohribnyi.fitadvisorbot.repository.diary.DailyAdviceJobRepository;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;
import com.ua.pohribnyi.fitadvisorbot.service.telegram.TelegramViewService;
import com.ua.pohribnyi.fitadvisorbot.service.user.UserSessionService;
import com.ua.pohribnyi.fitadvisorbot.util.concurrency.event.DailyAdviceJobSubmittedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiaryService {

	private final DailyAdviceJobRepository jobRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final UserSessionService sessionService;

	private final TelegramViewService viewService;

	/**
	 * Starts the daily check-in flow. Returns SendMessage to be executed by
	 * CommandHandler or Scheduler.
	 */
	@Transactional
	public SendMessage startDailyCheckIn(User user, boolean isManual) {
		LocalDate today = LocalDate.now();
		// Idempotency: If job exists, reuse it or check status
		DailyAdviceJob job = jobRepository.findByUserAndDate(user, today).orElseGet(() -> createNewJob(user, today));
		
		// Reset if it was failed or stuck in filling
		job.setStatus(Status.FILLING);
		jobRepository.save(job);

		sessionService.setState(user, UserState.AWAITING_SLEEP);
		return viewService.getDiaryStartMessage(user.getTelegramUserId(), isManual);
	}

	public EditMessageText processSleepInput(User user, String data, Integer messageId) {
		double hours = parseSleepData(data);
		updateJob(user, job -> job.setSleepHours(hours));

		sessionService.setState(user, UserState.AWAITING_STRESS);
		return viewService.getDiaryStressQuestion(user.getTelegramUserId(), messageId);
	}

	@Transactional
	public EditMessageText processStressInput(User user, String data, Integer messageId) {
		int stress = Integer.parseInt(data.split(":")[2]);
		updateJob(user, job -> job.setStressLevel(stress));

		sessionService.setState(user, UserState.AWAITING_ACTIVITY_CONFIRMATION);
		return viewService.getDiaryActivityConfirmationQuestion(user.getTelegramUserId(), messageId);
	}

	/**
	 * Returns EditMessageText. If flow ends here (no activity), logic triggers
	 * Async Advice generation.
	 * @throws JsonProcessingException 
	 * @throws JsonMappingException 
	 */
	@Transactional
	public EditMessageText processActivityConfirmation(User user, String data, Integer messageId,
			FitnessAdvisorBotService bot) throws JsonMappingException, JsonProcessingException {
		boolean hadActivity = data.endsWith("yes");
		Long chatId = user.getTelegramUserId();
		updateJob(user, job -> job.setHadActivity(hadActivity));
		
		if (!hadActivity) {
			return finishCheckIn(user, messageId);
		} else {
			sessionService.setState(user, UserState.AWAITING_ACTIVITY_TYPE);
			return viewService.getDiaryActivityTypeQuestion(chatId, messageId);
		}
	}

	@Transactional
	public EditMessageText processActivityType(User user, String data, Integer messageId) {
		String type = data.split(":")[2];
		updateJob(user, job -> job.setActivityType(type));

		sessionService.setState(user, UserState.AWAITING_ACTIVITY_DURATION);
		return viewService.getDiaryDurationQuestion(user.getTelegramUserId(), messageId);
	}

	@Transactional
	public EditMessageText processActivityDuration(User user, String data, Integer messageId) {
		int minutes = Integer.parseInt(data.split(":")[2]);
		updateJob(user, job -> job.setDurationMinutes(minutes));

		sessionService.setState(user, UserState.AWAITING_ACTIVITY_INTENSITY);
		return viewService.getDiaryIntensityQuestion(user.getTelegramUserId(), messageId);
	}

	@Transactional
	public EditMessageText processActivityIntensity(User user, String data, Integer messageId,
			FitnessAdvisorBotService bot) throws JsonMappingException, JsonProcessingException {
		int rpe = Integer.parseInt(data.split(":")[2]);
		updateJob(user, job -> job.setRpe(rpe));
		return finishCheckIn(user, messageId);
	}

	/**
	 * Finalizes the flow: 1. Resets state. 2. Returns "Thinking..." message to UI.
	 * 3. Triggers Async AI Advice generation.
	 */
	/*
	 * private EditMessageText finishCheckIn(User user, Integer messageId,
	 * FitnessAdvisorBotService bot, boolean hadActivity) throws
	 * JsonMappingException, JsonProcessingException {
	 */
	private EditMessageText finishCheckIn(User user, Integer messageId) {
		// 1. Retrieve full draft
        DailyAdviceJob job = getJobOrThrow(user);
        job.setStatus(Status.PENDING_PROCESSING);
        job.setNotificationMessageId(messageId);
        job.setUserChatId(user.getTelegramUserId());
        jobRepository.save(job);

        // 2. Reset UI State
        sessionService.setState(user, UserState.DEFAULT);

        // 3. Fire Async Event (Decoupled execution)
        eventPublisher.publishEvent(new DailyAdviceJobSubmittedEvent(job.getId()));
		
		return viewService.getDiaryWaitMessage(user.getTelegramUserId(), messageId);
	}


	// --- Helpers ---
	private DailyAdviceJob createNewJob(User user, LocalDate date) {
		return jobRepository.save(DailyAdviceJob.builder()
				.user(user)
				.date(date)
				.status(Status.FILLING)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.build());
	}

	private void updateJob(User user, Consumer<DailyAdviceJob> updater) {
		DailyAdviceJob job = getJobOrThrow(user);
		updater.accept(job);
		job.setUpdatedAt(LocalDateTime.now());
		jobRepository.save(job);
	}

	private DailyAdviceJob getJobOrThrow(User user) {
		return jobRepository.findByUserAndDate(user, LocalDate.now())
				.orElseThrow(() -> new IllegalStateException("No active job found for user " + user.getId()));
	}
	
	private double parseSleepData(String data) {
		if (data.endsWith("bad"))
			return 5.0;
		if (data.endsWith("avg"))
			return 6.5;
		if (data.endsWith("good"))
			return 7.5;
		return 7.0;
	}

}