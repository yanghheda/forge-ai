package ai.forge.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ForgeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeServerApplication.class, args);
    }
}
