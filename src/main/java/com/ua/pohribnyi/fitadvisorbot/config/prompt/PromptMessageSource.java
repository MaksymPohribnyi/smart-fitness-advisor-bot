package com.ua.pohribnyi.fitadvisorbot.config.prompt;

import java.io.IOException;
import java.util.Properties;

import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.ua.pohribnyi.fitadvisorbot.config.i18n.YamlPropertiesLoader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PromptMessageSource extends ReloadableResourceBundleMessageSource {

    private final YamlPropertiesLoader yamlLoader = new YamlPropertiesLoader();
    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        super.setResourceLoader(resourceLoader);
    }

    @Override
    protected PropertiesHolder refreshProperties(String filename, PropertiesHolder holder) {

        Resource resource = resourceLoader.getResource(filename + ".yml");
        if (!resource.exists()) {
            resource = resourceLoader.getResource(filename + ".yaml");
        }

        if (resource.exists()) {
            try {
                long fileTimestamp = resource.lastModified();

                if (holder == null || holder.getFileTimestamp() != fileTimestamp) {
                    Properties props = yamlLoader.load(resource);
                    holder = new PropertiesHolder(props, fileTimestamp);
                    log.info("Loaded AI prompts from: {}", resource.getDescription());
                }
            } catch (IOException ex) {
                log.error("Failed to load AI prompts from: {}", resource.getDescription(), ex);
                throw new IllegalStateException("Critical: AI prompts cannot be loaded", ex);
            }
        } else {
            log.error("AI prompts file not found: {}.yml or {}.yaml", filename, filename);
        }

        return holder;
    }

    @Override
    protected Properties loadProperties(Resource resource, String filename) throws IOException {
        return null; 
    }
}