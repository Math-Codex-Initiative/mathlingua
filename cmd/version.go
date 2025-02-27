/*
 * Copyright 2022 Dominic Kramer
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

package cmd

import (
	"mathlingua/internal/logger"
	"mathlingua/internal/mlg"
	"os"

	"github.com/spf13/cobra"
)

var versionCommand = &cobra.Command{
	Use:   "version",
	Short: "Print version information and quit",
	Long:  "Prints version information and quits",
	Run: func(cmd *cobra.Command, args []string) {
		logger := logger.NewLogger(os.Stdout)
		version := mlg.NewMlg(logger).Version()
		logger.Log(version)
	},
}

func init() {
	rootCmd.AddCommand(versionCommand)
}
