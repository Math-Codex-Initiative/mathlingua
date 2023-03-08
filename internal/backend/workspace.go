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

package backend

import (
	"mathlingua/internal/ast"
	"mathlingua/internal/frontend"
	"mathlingua/internal/server"
)

type ViewResult struct {
	Diagnostics map[ast.Path][]frontend.Diagnostic
	Pages       []WorkspacePageResponse
}

type WorkspacePageResponse struct {
	Path ast.Path
	Page server.PageResponse
}

type CheckResult struct {
	Diagnostics map[ast.Path][]frontend.Diagnostic
}

type Workspace interface {
	AddDocument(path ast.Path, content string)
	DocumentCount() int
	Check() CheckResult
	View() ViewResult
}

func NewWorkspace() Workspace {
	return &workspace{
		diagnosticsAtAdd: make(map[ast.Path][]frontend.Diagnostic),
		documents:        make(map[ast.Path]*ast.Document),
		context:          nil,
		scope:            nil,
	}
}

////////////////////////////////////////////////////////////////////////////////////////////////////

type workspace struct {
	diagnosticsAtAdd map[ast.Path][]frontend.Diagnostic
	documents        map[ast.Path]*ast.Document
	context          *ast.Context
	scope            *ast.Scope
}

func (w *workspace) AddDocument(path ast.Path, content string) {
	doc, diagnostics := ParseDocument(content)
	w.documents[path] = &doc
	w.diagnosticsAtAdd[path] = diagnostics
}

func (w *workspace) DocumentCount() int {
	return len(w.documents)
}

func (w *workspace) Check() CheckResult {
	diagnostics := make(map[ast.Path][]frontend.Diagnostic, 0)

	for p, diags := range w.diagnosticsAtAdd {
		diagnostics[p] = diags
	}

	return CheckResult{
		Diagnostics: diagnostics,
	}
}

func (w *workspace) View() ViewResult {
	return ViewResult{}
}
