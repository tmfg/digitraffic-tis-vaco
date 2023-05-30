package fi.digitraffic.tis.vaco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VacoApplication {

    public static void main(String[] args) {
        SpringApplication.run(VacoApplication.class, args);
    }

}
