package com.ua.pohribnyi.fitadvisorbot.config.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.ua.pohribnyi.fitadvisorbot.service.telegram.FitnessAdvisorBotService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class TelegramBotConfig {

	@Value("${telegram.bot.token}")
	private String botToken;

	@Value("${telegram.bot.username}")
	private String botUsername;

	@Bean
	public TelegramBotsApi telegramBotsApi(FitnessAdvisorBotService fitnessAdvisorBot) {
		try {
			TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
			api.registerBot(fitnessAdvisorBot);
			log.info("Telegram bot registered successfully: {}", botUsername);
			return api;
		} catch (TelegramApiException e) {
			log.error("Failed to register Telegram bot", e);
			throw new RuntimeException("Failed to register Telegram bot", e);
		}
	}
}