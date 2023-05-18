/*
 * Copyright 2023 Dominic Kramer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mlg

import (
	"fmt"
	"mathlingua/internal/config"
	"os"
	"path"
)

const mlg_conf_name = "mlg.conf"

func LoadMlgConfig(logger ILogger) config.MlgConfig {
	cwd, err := os.Getwd()
	if err != nil {
		logger.Warning(fmt.Sprintf("Could not determine if %s exists: "+
			"Failed to determine the current working directory.\n", mlg_conf_name))
		return config.MlgConfig{}
	}

	content, err := os.ReadFile(path.Join(cwd, mlg_conf_name))
	if err != nil && os.IsNotExist(err) {
		// if the config file doesn't exist, then use the default config
		return config.MlgConfig{}
	}

	if err != nil {
		logger.Error(fmt.Sprintf("An error occurred while reading %s: %s\n", mlg_conf_name, err))
		return config.MlgConfig{}
	}

	conf, err := config.ParseMlgConfig(string(content))
	if err != nil {
		logger.Error(fmt.Sprintf("An error occurred while parsing %s: %s\n", mlg_conf_name, err))
		return config.MlgConfig{}
	}

	return conf
}
