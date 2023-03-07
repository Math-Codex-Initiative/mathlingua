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

import "mathlingua/internal/ast"

type WorkspaceContext interface {
	ast.Context
	AddDocument(doc *ast.Document)
}

func NewWorkspaceContext() WorkspaceContext {
	return &workspaceContext{}
}

////////////////////////////////////////////////////////////////////////////////////////////////////

type workspaceContext struct {
	parent *ast.Context
}

func (c *workspaceContext) GetParent() (*ast.Context, bool) {
	return c.parent, c.parent != nil
}

func (c *workspaceContext) SetParent(parent *ast.Context) {
	c.parent = parent
}

func (c *workspaceContext) IsSubTypeOf(from string, to string) bool {
	// TODO: implement this
	return false
}

func (c *workspaceContext) IsViewableAs(from string, to string) bool {
	// TODO: implement this
	return false
}

func (c *workspaceContext) PopulateFromSpecAlias(nameType ast.ResolvedType,
	specName string, target ast.ExpressionType,
	scope *ast.Scope) {
}

func (c *workspaceContext) PopulateFromIs(is *ast.IsExpression, scope *ast.Scope) {
}

func (c *workspaceContext) GetWrittenAs(exp *ast.ExpressionType, scope *ast.Scope) {
}

func (c *workspaceContext) AddDocument(doc *ast.Document) {
}
