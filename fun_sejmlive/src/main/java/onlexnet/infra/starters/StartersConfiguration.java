package onlexnet.infra.starters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.AppModuleConfigurer;
import onlexnet.app.ports.out.TelegramNotifier;
import onlexnet.infra.adapters.in.AdaptersInModuleConfigurer;
import onlexnet.infra.adapters.out.AdaptersOutModuleConfigurer;
import onlexnet.infra.adapters.out.telegram.DefaultTelegramNotifier;
import onlexnet.infra.config.DatabaseConfiguration;

@Configuration
@Import({
	DatabaseConfiguration.class,
	AdaptersOutModuleConfigurer.class,
	AdaptersInModuleConfigurer.class,
	AppModuleConfigurer.class
})
public class StartersConfiguration {

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}

	/**
	 * Creates the Telegram notifier using configured token.
	 */
	@Bean
	public TelegramNotifier telegramNotifier(@Value("${TELEGRAM_BOT_TOKEN}") String token) {
		return new DefaultTelegramNotifier(token);
	}
}