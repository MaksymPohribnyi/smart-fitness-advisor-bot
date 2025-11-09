package com.ua.pohribnyi.fitadvisorbot.service.telegram;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.ua.pohribnyi.fitadvisorbot.config.telegram.TelegramUpdateDispatcher;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FitnessAdvisorBotService extends TelegramLongPollingBot {

	private final TelegramUpdateDispatcher updateDispatcher;
	private final TelegramErrorHandler errorHandler;
	private final String botUsername;

	public FitnessAdvisorBotService(@Value("${telegram.bot.token}") String botToken,
			@Value("${telegram.bot.username}") String botUsername, TelegramUpdateDispatcher updateDispatcher,
			TelegramErrorHandler errorHandler, @Qualifier("yamlMessageSource") MessageSource messageSource) {
		super(botToken);
		this.botUsername = botUsername;
		this.updateDispatcher = updateDispatcher;
		this.errorHandler = errorHandler;
	}

	/**
	 * This is the single entry point for all updates. It delegates ALL logic to the
	 * dispatcher.
	 */
	@Override
	public void onUpdateReceived(Update update) {
		try {
			updateDispatcher.dispatch(update, this);
		} catch (Exception e) {
			errorHandler.handleGlobalError(e, update, this);
		}
	}

	public void sendMessage(SendMessage message) {
		try {
			execute(message);
		} catch (TelegramApiException e) {
			log.error("Failed to send message: {}", e.getMessage(), e);
		}
	}

	@Override
	public String getBotUsername() {
		return botUsername;
	}

}
