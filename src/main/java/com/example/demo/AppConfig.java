package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public SejmApi sejmApi() {
        return new SejmApiImpl();
    }

    @Bean
    public FaceApi faceApi() {
        return new FaceApiImpl();
    }
}
