/*
 * Copyright 2015 ligaDATA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ligadata.kamanja.consumer.util;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.logging.log4j.{ Logger, LogManager };

public class ConfigManager {

	private static Logger logger = LogManager.getLogger(ConfigManager.class);

	public static XMLConfiguration getConfig(String configFile) {

		XMLConfiguration config = null;
		try {
			config = new XMLConfiguration(configFile);
		} catch (ConfigurationException e) {
			logger.error("Error while reading xonfiguration file", e);
		}

		return config;
	}

}
