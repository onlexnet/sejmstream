package onlexnet.sejmapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.infra.adapters.out.AdaptersOutModuleConfigurer;

@Configuration
@Import(AdaptersOutModuleConfigurer.class)
public class SpringBoot4Configuration {

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	public ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}

	@Bean
	public SimpleMessageService simpleMessageService() {
		return new SimpleMessageService("sejmstream");
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
}
