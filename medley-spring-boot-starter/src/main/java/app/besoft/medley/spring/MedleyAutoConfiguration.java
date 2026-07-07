package app.besoft.medley.spring;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration entry point. Registers the registries, the SSR controller, the patch
 * encoder, the WebSocket handler and interceptor. Importing the starter on the classpath is
 * enough; everything is conditional so an application can override any bean.
 *
 * <p>The actual WebSocket endpoint registration lives in {@link MedleyWebSocketConfig}, a
 * separate {@code WebSocketConfigurer} so the handler/interceptor beans inject cleanly.</p>
 */
@Configuration
@EnableConfigurationProperties(MedleyProperties.class)
public class MedleyAutoConfiguration {

    private final MedleyProperties properties;

    public MedleyAutoConfiguration(MedleyProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateRegistry medleyTemplateRegistry() {
        return new TemplateRegistry(properties.getTemplateLocation());
    }

    @Bean
    @ConditionalOnMissingBean
    public RouteRegistry medleyRouteRegistry(ApplicationContext context) {
        return new RouteRegistry(context);
    }

    @Bean
    @ConditionalOnMissingBean
    public PatchEncoder medleyPatchEncoder(ObjectMapper mapper) {
        return new PatchEncoder(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MedleyController medleyController(RouteRegistry routes, TemplateRegistry templates) {
        return new MedleyController(routes, templates, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MedleyWebSocketHandler medleyWebSocketHandler(ObjectMapper mapper, PatchEncoder encoder) {
        return new MedleyWebSocketHandler(mapper, encoder);
    }

    @Bean
    @ConditionalOnMissingBean
    public MedleyHandshakeInterceptor medleyHandshakeInterceptor() {
        return new MedleyHandshakeInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public MedleyWebSocketConfig medleyWebSocketConfig(MedleyWebSocketHandler handler,
                                                       MedleyHandshakeInterceptor interceptor) {
        return new MedleyWebSocketConfig(handler, interceptor, properties);
    }
}
