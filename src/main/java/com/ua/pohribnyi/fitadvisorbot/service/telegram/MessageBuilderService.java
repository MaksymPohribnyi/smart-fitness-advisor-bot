package com.ua.pohribnyi.fitadvisorbot.service.telegram;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MessageBuilderService {

	/**
	 * Creates a simple text message.
	 */
	public SendMessage createMessage(Long chatId, String text) {
		return SendMessage.builder()
				.chatId(chatId)
				.text(text)
				.parseMode(ParseMode.MARKDOWNV2)
				.build();
	}

	/**
	 * Creates a message with a keyboard.
	 */
	public SendMessage createMessageWithKeyboard(Long chatId, String text, ReplyKeyboard keyboard) {
		return SendMessage.builder()
				.chatId(chatId)
				.text(text)
				.replyMarkup(keyboard)
				.parseMode(ParseMode.MARKDOWNV2)
				.build();
	}

	/**
	 * Creates an edit message request.
	 */
	public EditMessageText createEditMessage(Long chatId, Integer messageId, String text) {
		return EditMessageText.builder()
				.chatId(chatId)
				.messageId(messageId)
				.text(text)
				.parseMode(ParseMode.MARKDOWNV2)
				.build();
	}

	/**
	 * Creates an edit message request with inline keyboard.
	 */
	public EditMessageText createEditMessageWithKeyboard(Long chatId, Integer messageId, String text,
			InlineKeyboardMarkup keyboard) {
		return EditMessageText.builder()
				.chatId(chatId)
				.messageId(messageId)
				.text(text)
				.replyMarkup(keyboard)
				.parseMode(ParseMode.MARKDOWNV2)
				.build();
	}

}