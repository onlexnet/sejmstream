package onlexnet.app.ports.in;

/**
 * Application use case for administrative commands.
 */
public interface AdminUseCase {

	/**
	 * Handles one Telegram command issued by a specific chat.
	 *
	 * @param chatId Telegram chat id
	 * @param text   raw message text
	 * @return use case result describing whether adapter should send a response
	 */
	TelegramCommandResult handleTelegramCommand(long chatId, String text);

	/**
	 * Result of command handling.
	 */
	sealed interface TelegramCommandResult permits TelegramCommandResult.NoReply, TelegramCommandResult.Reply {

		static TelegramCommandResult noReply() {
			return NoReply.NO_REPLY;
		}

		static TelegramCommandResult reply(String message) {
			return new Reply(message);
		}

		enum NoReply implements TelegramCommandResult {
			NO_REPLY
		}

		record Reply(String message) implements TelegramCommandResult {

			public Reply {
				if (message.isBlank()) {
					throw new IllegalArgumentException("message must not be blank when shouldReply is true");
				}
			}
		}
	}
}
