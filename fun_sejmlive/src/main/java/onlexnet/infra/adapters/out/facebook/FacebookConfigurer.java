package onlexnet.infra.adapters.out.facebook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Version;
import com.restfb.exception.FacebookOAuthException;
import com.restfb.types.Page;

import lombok.extern.slf4j.Slf4j;


@Configuration
@Slf4j
class FacebookConfigurer {

    @Bean
    FacebookClient createClient(@Value("${FB_TOKEN}") String token) {
        var fbClient = new DefaultFacebookClient(token, Version.LATEST);
        try {
            var pages = fbClient.fetchConnection("me/accounts", Page.class);
            var pageAccessToken = pages.getData().stream()
                    .filter(page -> System.getenv().getOrDefault("FB_PAGE_NAME", "SejmStream2")
                            .equals(page.getName()))
                    .map(Page::getAccessToken)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst();

            if (pageAccessToken.isPresent()) {
                return new DefaultFacebookClient(pageAccessToken.get(), Version.LATEST);
            }

            log.warn("Could not resolve a page access token; using the configured token directly.");
            return fbClient;
        } catch (FacebookOAuthException exception) {
            if (exception.getErrorCode() == 100) {
                return fbClient;
            }
            throw exception;
        }
    }    
}
