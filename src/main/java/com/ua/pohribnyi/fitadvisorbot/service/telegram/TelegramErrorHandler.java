package com.ua.pohribnyi.fitadvisorbot.service.telegram;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramErrorHandler {

	private final TelegramViewService viewService;

	/**
	 * 
	 * 
	 * @param e
	 * @param update
	 * @param bot
	 */
	public void handleGlobalError(Exception e, Update update, FitnessAdvisorBotService bot) {
		log.error("Unhandled error processing update [{}]: {}", update.getUpdateId(), e.getMessage(), e);

		getChatIdFromUpdate(update).ifPresentOrElse(chatId -> {
			try {
				SendMessage errorMessage = viewService.getGeneralErrorMessage(chatId);
				bot.sendMessage(errorMessage);
			} catch (Exception ex) {
				log.error("Failed to send error message to user {}: {}", chatId, ex.getMessage(), ex);
			}
		}, () -> log.error("Could not determine chatId to send error message for update: {}", update));
	}

	private Optional<Long> getChatIdFromUpdate(Update update) {
		if (update.hasMessage()) {
			return Optional.of(update.getMessage().getChatId());
		} else if (update.hasCallbackQuery()) {
			return Optional.of(update.getCallbackQuery().getMessage().getChatId());
		}
		// add new types (editedMessage, inlineQuery...), if needed
		return Optional.empty();
	}
}
