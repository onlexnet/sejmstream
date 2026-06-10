package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.restfb.DefaultFacebookClient;
import com.restfb.Version;
import com.restfb.exception.FacebookOAuthException;
import com.restfb.types.Page;

@Configuration
public class DemoConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DemoConfigurer.class);

    @Value("${FB_TOKEN}")
    String fbToken;
    
    @Value("${FB_PAGE_NAME:SejmStream2}")
    String fbPageName;

    @Bean
    DefaultFacebookClient fbClient() {
        var fbClient = new DefaultFacebookClient(fbToken, Version.LATEST);
        try {
            var pages = fbClient.fetchConnection("me/accounts", Page.class);
            var pageAccessToken = pages.getData().stream()
                    .filter(it -> fbPageName.equals(it.getName()))
                    .map(Page::getAccessToken)
                    .filter(it -> it != null && !it.isBlank())
                    .findFirst();

            if (pageAccessToken.isPresent()) {
                return new DefaultFacebookClient(pageAccessToken.get(), Version.LATEST);
            }

            // If page was not found, keep using the configured token to avoid startup failure.
            log.warn("Could not find page token for page '{}'; using configured FB token directly.", fbPageName);
            return fbClient;
        } catch (FacebookOAuthException ex) {
            // Page tokens don't support me/accounts; use token as-is in that case.
            if (ex.getErrorCode() == 100) {
                return fbClient;
            }
            throw ex;
        }
    }
}
