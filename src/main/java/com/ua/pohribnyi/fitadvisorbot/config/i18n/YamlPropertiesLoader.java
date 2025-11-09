package com.ua.pohribnyi.fitadvisorbot.config.i18n;

import java.io.IOException;
import java.util.Properties;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;

public class YamlPropertiesLoader {

	/**
	 * Main method for loading YAML from resources.
	 * 
	 * @param resource Resource YAML file.
	 * @return Uploaded properties.
	 * @throws IOException.
	 */
	public Properties load(@NonNull Resource resource) throws IOException {
		if (!resource.exists()) {
			return new Properties();
		}

		YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
		factory.setResources(resource);
		factory.afterPropertiesSet();

		Properties properties = factory.getObject();
		return (properties != null) ? properties : new Properties();
	}

}