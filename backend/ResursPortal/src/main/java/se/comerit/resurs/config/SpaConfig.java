package se.comerit.resurs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private final SpaFallbackResourceResolver spaFallbackResolver;

    public SpaConfig(SpaFallbackResourceResolver spaFallbackResolver) {
        this.spaFallbackResolver = spaFallbackResolver;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/**")
                .addResourceLocations(
                        "file:target/frontend/",
                        "classpath:/static/",
                        "classpath:/META-INF/resources/static/")
                .resourceChain(true)
                .addResolver(spaFallbackResolver);
    }
}
