package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.restfb.DefaultFacebookClient;
import com.restfb.Version;
import com.restfb.types.Page;

@Configuration
public class DemoConfigurer {

    @Value("${FB_TOKEN}")
    String fbToken;

    @Bean
    DefaultFacebookClient fbClient() {
        var fbClient = new DefaultFacebookClient(fbToken, Version.LATEST);
        var pages = fbClient.fetchConnection("me/accounts", Page.class);

        var pat = pages.getData().stream()
                .filter(it -> it.getName().equals("SejmStream2"))
                .findFirst()
                .get()
                .getAccessToken();
        return new DefaultFacebookClient(pat, Version.LATEST);
    }
}
