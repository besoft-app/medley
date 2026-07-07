package app.besoft.medley.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Counter demo application.
 *
 * <p>Run with {@code ./gradlew :examples:counter-demo:bootRun} and open
 * {@code http://localhost:8080/counter}.</p>
 */
@SpringBootApplication
public class CounterDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CounterDemoApplication.class, args);
    }
}
