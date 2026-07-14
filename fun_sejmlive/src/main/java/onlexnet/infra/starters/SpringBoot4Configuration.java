package onlexnet.infra.starters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.AppModuleConfigurer;
import onlexnet.app.ports.out.FacebookPublisher;
import onlexnet.app.ports.out.TelegramNotifier;
import onlexnet.infra.adapters.in.AdaptersInModuleConfigurer;
import onlexnet.infra.adapters.out.AdaptersOutModuleConfigurer;
import onlexnet.infra.adapters.out.facebook.DefaultFacebookPublisher;
import onlexnet.infra.adapters.out.telegram.DefaultTelegramNotifier;
import onlexnet.infra.config.DatabaseConfiguration;

@Configuration
@Import({
	DatabaseConfiguration.class,
	AdaptersOutModuleConfigurer.class,
	AdaptersInModuleConfigurer.class,
	AppModuleConfigurer.class
})
public class SpringBoot4Configuration {

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}

	@Bean
	public FacebookPublisher facebookPublisher(@Value("${FB_TOKEN}") final String token) {
		return new DefaultFacebookPublisher(token);
	}

	/**
	 * Creates the Telegram notifier using configured token.
	 */
	@Bean
	public TelegramNotifier telegramNotifier(@Value("${TELEGRAM_BOT_TOKEN}") final String token) {
		return new DefaultTelegramNotifier(token);
	}
}