package se.comerit.resurs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Text-template engine for transactional email. Templates live in
 * {@code email-templates/} (a sibling of {@code templates/} and {@code static/},
 * outside the web view prefix and the static-resource locations) so they are
 * consumed as Thymeleaf templates only, never served to browsers.
 *
 * <p>Each file is plain text with a subject line on the first row, a {@code ---}
 * delimiter, and the body below. {@code EmailService} renders both halves from
 * the same file. Template caching follows {@code spring.thymeleaf.cache}, so
 * copy edits are picked up without a restart during development.</p>
 */
@Configuration
public class EmailTemplateConfig {

    @Bean
    public SpringTemplateEngine emailTemplateEngine(
            @Value("${spring.thymeleaf.cache:false}") boolean cacheable) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("email-templates/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(cacheable);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}