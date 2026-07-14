package app.besoft.medley.spring;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Optional development tools (Stage 5, increment 2). Registers the {@code /medley/devtools/tree}
 * snapshot endpoint <em>only</em> when {@code medley.devtools.enabled=true}. Kept as a separate
 * auto-configuration — like {@link MedleyMetricsAutoConfiguration} — so the core auto-config carries
 * no development-only beans and the whole feature is absent by default.
 *
 * <p>The other half of the feature (the in-page overlay) is the {@code /medley/devtools.js} script,
 * which {@link MedleyController} references from the SSR shell under the same flag. With the flag
 * off, the endpoint 404s and the shell never loads the script, so the inspector cannot be turned on
 * from the browser.</p>
 *
 * <p><b>Never enable this in production:</b> the snapshot exposes a session's {@code @State} and
 * {@code @Param} values. Enabling it logs a warning for exactly that reason.</p>
 */
@AutoConfiguration(after = MedleyAutoConfiguration.class)
@ConditionalOnProperty(prefix = "medley.devtools", name = "enabled", havingValue = "true")
public class MedleyDevToolsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MedleyDevToolsAutoConfiguration.class);

    public MedleyDevToolsAutoConfiguration() {
        log.warn("Medley dev-tools are ENABLED (medley.devtools.enabled=true): /medley/devtools/tree "
                + "exposes this session's @State and @Param values, and every page loads the inspector "
                + "overlay. Disable this outside development.");
    }

    @Bean
    @ConditionalOnMissingBean
    public MedleyDevToolsController medleyDevToolsController(ObjectMapper mapper) {
        return new MedleyDevToolsController(mapper);
    }

    /**
     * Binds the snapshot endpoint. Deliberately not {@code @ConditionalOnMissingBean}: Spring composes
     * multiple {@code RouterFunction} beans, so this coexists with the SSR routes (and any the
     * application defines) rather than replacing them.
     */
    @Bean
    public RouterFunction<ServerResponse> medleyDevToolsRouterFunction(MedleyDevToolsController controller) {
        return RouterFunctions.route()
                .GET("/medley/devtools/tree", controller::tree)
                .build();
    }
}
