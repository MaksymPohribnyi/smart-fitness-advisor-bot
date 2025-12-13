package com.ua.pohribnyi.fitadvisorbot.config.i18n;

import java.util.Properties;

import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YamlMessageSource extends ReloadableResourceBundleMessageSource {

	private final YamlPropertiesLoader yamlLoader = new YamlPropertiesLoader();
	private ResourceLoader resourceLoader;

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		super.setResourceLoader(resourceLoader);
	}

	@Override
	protected PropertiesHolder refreshProperties(String filename, @Nullable PropertiesHolder holder) {
		log.info(">>> refreshProperties CALLED: filename={}, holder={}", filename, holder != null ? "EXISTS" : "NULL");
	    
		Resource resource = resourceLoader.getResource(filename + ".yml");
		if (!resource.exists()) {
			resource = this.resourceLoader.getResource(filename + ".yaml");
		}
		if (!resource.exists()) {
			return holder != null ? holder : new PropertiesHolder();
		}
		try {
			long fileTimestamp = resource.lastModified();
			if (holder != null) {
	            long holderTimestamp = holder.getFileTimestamp();
	            log.info(">>> TIMESTAMPS: holder={}, file={}, equal={}", 
	                holderTimestamp, fileTimestamp, holderTimestamp == fileTimestamp);
	        }
			if (holder != null && holder.getFileTimestamp() == fileTimestamp) {
				log.info(">>> RETURNING SAME HOLDER");
	            return holder;
			}
			log.debug("Loading YAML properties from: {}", resource.getDescription());
			Properties props = yamlLoader.load(resource);
			PropertiesHolder newHolder = new PropertiesHolder(props, fileTimestamp);
			log.info(">>> CREATED NEW HOLDER: timestamp={}", newHolder.getFileTimestamp());
			return newHolder;
		} catch (Exception ex) {
			log.warn("Failed to load YAML properties from {}: {}", filename, ex.getMessage());
			return holder != null ? holder : new PropertiesHolder();
		}
	}
	
}