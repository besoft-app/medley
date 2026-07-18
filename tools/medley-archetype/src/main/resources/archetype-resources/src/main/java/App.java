package ${package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A Medley application. Run with {@code mvn spring-boot:run} and open
 * {@code http://localhost:8080/} (also {@code /signup} and {@code /chart}).
 */
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
