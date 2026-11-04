package se.comerit.resurs.config;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Component
public class SpaFallbackResourceResolver extends PathResourceResolver {

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource resource = location.createRelative(resourcePath);
        if (resource.exists() && resource.isReadable()) {
            return resource;
        }
        // For any missing resource, fall back to index.html
        Resource indexHtml = location.createRelative("index.html");
        return indexHtml.exists() && indexHtml.isReadable() ? indexHtml : null;
    }
}
