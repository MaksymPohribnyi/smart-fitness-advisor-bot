package com.ua.pohribnyi.fitadvisorbot.config.i18n;

import java.io.IOException;
import java.util.Properties;

import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

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
	protected PropertiesHolder refreshProperties(String filename, PropertiesHolder holder) {

		long refreshTimestamp = (getCacheMillis() < 0 ? -1 : System.currentTimeMillis());

		Resource resource = resourceLoader.getResource(filename + ".yml");
		if (!resource.exists()) {
			resource = resourceLoader.getResource(filename + ".yaml");
		}

		if (resource.exists()) {
			try {
				long fileTimestamp = resource.lastModified();

				// Upload only if file has changed
				if (holder == null || holder.getRefreshTimestamp() < fileTimestamp) {
					Properties props = yamlLoader.load(resource);
					holder = new PropertiesHolder(props, fileTimestamp);
					log.debug("Loaded YAML properties from: {}", resource.getDescription());
				}
			} catch (IOException ex) {
				log.warn("Could not load YAML properties from: {}", resource.getDescription(), ex);
			}
		} else {
			log.trace("YAML resource not found: {}.yml or {}.yaml", filename, filename);
			if (holder == null) {
				holder = new PropertiesHolder();
			}
			holder.setRefreshTimestamp(refreshTimestamp);
		}

		return holder;
	}

	@Override
	protected Properties loadProperties(Resource resource, String filename) throws IOException {
		return null;
	}

}