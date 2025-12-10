package com.ua.pohribnyi.fitadvisorbot.service.analytics.diary;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ua.pohribnyi.fitadvisorbot.model.dto.analytics.DiaryDraft;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DiaryStateService {

	// K: Telegram UserId, V: Draft Data
	private final Map<Long, DiaryDraft> drafts = new ConcurrentHashMap<>();

	public DiaryDraft getOrCreate(Long userId) {
		return drafts.computeIfAbsent(userId, k -> new DiaryDraft());
	}

	public void updateSleep(Long userId, double hours) {
		DiaryDraft draft = getOrCreate(userId);
		draft.setSleepHours(hours);
		draft.touch();
	}

	public void updateStress(Long userId, int stress) {
		DiaryDraft draft = getOrCreate(userId);
		draft.setStressLevel(stress);
		draft.touch();
	}

	public void updateActivityPresence(Long userId, boolean present) {
		DiaryDraft draft = getOrCreate(userId);
		draft.setHadActivity(present);
		draft.touch();
	}

	public void updateActivityType(Long userId, String type) {
		DiaryDraft draft = getOrCreate(userId);
		draft.setActivityType(type);
		draft.touch();
	}

	public void updateDuration(Long userId, int minutes) {
		DiaryDraft draft = getOrCreate(userId);
		draft.setDurationMinutes(minutes);
		draft.touch();
	}

	public void updateRpe(Long userId, int rpe) {
		DiaryDraft draft = getOrCreate(userId);
		draft.setRpe(rpe);
		draft.touch();
	}

	public DiaryDraft getAndRemove(Long userId) {
		return drafts.remove(userId);
	}

	@Scheduled(fixedRate = 1800000) 
	public void cleanupOldDrafts() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        drafts.values().removeIf(draft -> draft.getLastUpdated().isBefore(threshold));
    }
	
}
