package onlexnet.sejmapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.AppModuleConfigurer;
import onlexnet.infra.adapters.in.AdaptersInModuleConfigurer;
import onlexnet.infra.adapters.out.AdaptersOutModuleConfigurer;
import onlexnet.sejmapi.telegram.DefaultTelegramNotifier;
import onlexnet.sejmapi.telegram.TelegramNotifier;

@Configuration
@Import({AdaptersOutModuleConfigurer.class, AdaptersInModuleConfigurer.class, AppModuleConfigurer.class})
public class SpringBoot4Configuration {

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	public ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}

	/**
	 * Creates the real {@link FacebookPublisher} when {@code FB_TOKEN} is configured.
	 * <p>
	 * The token is read from Spring's environment (which includes Azure Functions app
	 * settings exposed as environment variables). Bean creation validates the token
	 * is non-blank; the actual Facebook API connection is deferred to the first
	 * {@code publish()} call, so startup is not blocked by network latency.
	 */
	@Bean
	@ConditionalOnProperty("FB_TOKEN")
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
