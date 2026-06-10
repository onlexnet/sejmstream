package onlexnet.sejmapi;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import onlexnet.infra.adapters.out.AdaptersOutModuleConfigurer;

@Configuration
@Import(AdaptersOutModuleConfigurer.class)
public class SpringBoot4Configuration {
}
