package onlexnet.infra.starters;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "onlexnet.sejmapi")
public class Program {

    public static void main(final String[] args) {
        SpringApplication.run(Program.class, args);
    }
}