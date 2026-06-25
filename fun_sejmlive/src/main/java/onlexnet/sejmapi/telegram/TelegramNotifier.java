package onlexnet.sejmapi.telegram;

/**
 * Sends messages to Telegram chats.
 */
public interface TelegramNotifier {

    /**
     * Sends a text message to a Telegram chat.
     *
     * @param chatId Telegram chat id
     * @param text   message text
     */
    void sendMessage(long chatId, String text);
}
