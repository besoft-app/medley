package app.besoft.medley.spring;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Auto-configuration entry point. Registers the registries, the SSR controller, the patch
 * encoder, the WebSocket handler and interceptor. Importing the starter on the classpath is
 * enough; the individual beans are {@code @ConditionalOnMissingBean} so an application can
 * override them (the exception is {@code medleyRouterFunction}, which composes with any other
 * {@code RouterFunction} beans rather than replacing them).
 *
 * <p>The actual WebSocket endpoint registration lives in {@link MedleyWebSocketConfig}, a
 * separate {@code WebSocketConfigurer} so the handler/interceptor beans inject cleanly.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(MedleyProperties.class)
@Import(MedleyWebSocketConfig.class)
public class MedleyAutoConfiguration {

    private final MedleyProperties properties;

    public MedleyAutoConfiguration(MedleyProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateRegistry medleyTemplateRegistry(ComponentRegistry components) {
        return new TemplateRegistry(properties.getTemplateLocation(), components);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentRegistry medleyComponentRegistry(ConfigurableListableBeanFactory beanFactory) {
        return new ComponentRegistry(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public RouteRegistry medleyRouteRegistry(ConfigurableListableBeanFactory beanFactory) {
        return new RouteRegistry(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public IslandRegistry medleyIslandRegistry(ConfigurableListableBeanFactory beanFactory,
                                               ObjectMapper mapper) {
        return new IslandRegistry(beanFactory, mapper);
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

    /**
     * Binds each {@code @MedleyRoute} path to the SSR handler as an <em>exact</em> route. Exact
     * paths (not a {@code /**} catch-all) keep Medley's own assets and WebSocket handshake
     * reachable. Deliberately not {@code @ConditionalOnMissingBean}: Spring composes multiple
     * {@code RouterFunction} beans, so this coexists with any the application defines.
     */
    @Bean
    public RouterFunction<ServerResponse> medleyRouterFunction(RouteRegistry routes,
                                                               MedleyController controller) {
        RouterFunctions.Builder builder = RouterFunctions.route();
        for (String path : routes.routes().keySet()) {
            builder = builder.GET(path, controller::render);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public MedleyWebSocketHandler medleyWebSocketHandler(ObjectMapper mapper, PatchEncoder encoder,
                                                         IslandRegistry islands) {
        return new MedleyWebSocketHandler(mapper, encoder, islands,
                properties.getSecurity().getMaxMessageBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    public MedleyHandshakeInterceptor medleyHandshakeInterceptor() {
        return new MedleyHandshakeInterceptor(properties.getSecurity().isRequireAuthenticatedHandshake());
    }

    // MedleyWebSocketConfig is @Import-ed (not a @Bean here) so its @EnableWebSocket
    // infrastructure is processed and the endpoint is actually mapped.
}
