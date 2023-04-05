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
	"mathlingua/pkg/mlg"

	"github.com/spf13/cobra"
)

var viewCommand = &cobra.Command{
	Use:   "view",
	Short: "View rendered Mathlingua files",
	Long:  "Renders the Mathlingua (.math) files in the current directory.",
	Args:  cobra.MaximumNArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		mlg.NewMlg(mlg.NewLogger()).View()
	},
}

func init() {
	rootCmd.AddCommand(viewCommand)
}
