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

package mlg

import (
	"encoding/json"
	"fmt"
	"mathlingua/internal/backend"
	"mathlingua/internal/config"
	"mathlingua/internal/frontend"
	"mathlingua/internal/logger"
)

func NewMlg(logger *logger.Logger) *Mlg {
	m := Mlg{}
	m.initialize(logger)
	return &m
}

type Mlg struct {
	logger  *logger.Logger
	tracker *frontend.DiagnosticTracker
	conf    config.MlgConfig
}

func (m *Mlg) Check(paths []string, showJson bool, debug bool) {
	workspace, diagnostics := backend.NewWorkspaceFromPaths(paths, m.tracker)

	checkResult := workspace.Check()
	diagnostics = append(diagnostics, checkResult.Diagnostics...)

	numErrors := 0
	numWarnings := 0
	for _, diag := range diagnostics {
		if diag.Type == frontend.Error {
			numErrors++
		} else if diag.Type == frontend.Warning {
			numWarnings++
		}
	}

	numFilesProcessed := workspace.DocumentCount()

	if showJson {
		m.printAsJson(backend.CheckResult{
			Diagnostics: diagnostics,
		})
		return
	}

	m.printCheckStats(numErrors, numWarnings, numFilesProcessed, debug, diagnostics)
}

func (m *Mlg) View(port int) {
	backend.StartServer(port, m.conf)
}

func (m *Mlg) Version() string {
	return "v0.22.0"
}

func (m *Mlg) GetUsages() []string {
	workspace, _ := backend.NewWorkspaceFromPaths([]string{"."}, m.tracker)
	return workspace.GetUsages()
}

////////////////////////////////////////////////////////////////////////////////////////////////////

func (m *Mlg) initialize(logger *logger.Logger) {
	m.logger = logger
	m.tracker = frontend.NewDiagnosticTracker()
	m.conf = *config.LoadMlgConfig(m.tracker)
}

func (m *Mlg) printAsJson(checkResult backend.CheckResult) {
	if data, err := json.MarshalIndent(checkResult, "", "  "); err != nil {
		m.logger.Error(fmt.Sprintf(
			"{\"Diagnostics\": [{\"Type\": \"Error\", \"Message\": \"%s\"}]}", err))
	} else {
		m.logger.Log(string(data))
	}
}

func (m *Mlg) printCheckStats(numErrors int, numWarnings int, numFilesProcessed int,
	debug bool, diagnostics []frontend.Diagnostic) {
	for index, diag := range diagnostics {
		if index > 0 {
			// print a line between each error
			m.logger.Log("")
		}
		debugInfo := ""
		if debug {
			debugInfo = fmt.Sprintf(" [%s]", diag.Origin)
		}
		if diag.Type == frontend.Error {
			m.logger.Error(fmt.Sprintf("%s (%d, %d)%s\n%s",
				diag.Path, diag.Position.Row+1, diag.Position.Column+1,
				debugInfo, diag.Message))
		} else {
			m.logger.Warning(fmt.Sprintf("%s (%d, %d)%s\n%s",
				diag.Path, diag.Position.Row+1, diag.Position.Column+1,
				debugInfo, diag.Message))
		}
	}

	var errorText string
	if numErrors == 1 {
		errorText = "error"
	} else {
		errorText = "errors"
	}

	var warningText string
	if numWarnings == 1 {
		warningText = "warning"
	} else {
		warningText = "warnings"
	}

	var filesText string
	if numFilesProcessed == 1 {
		filesText = "file"
	} else {
		filesText = "files"
	}

	if numErrors > 0 {
		// if there are errors logged, then log a blank line before
		// logging the summary
		m.logger.Log("")
	}

	if numErrors > 0 {
		m.logger.Failure(fmt.Sprintf("Processed %d %s and found %d %s and %d %s",
			numFilesProcessed, filesText, numErrors, errorText, numWarnings, warningText))
	} else {
		m.logger.Success(fmt.Sprintf("Processed %d %s and found %d %s and %d %s",
			numFilesProcessed, filesText, numErrors, errorText, numWarnings, warningText))
	}
}
