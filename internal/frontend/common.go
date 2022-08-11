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

package frontend

type DiagnosticType string

const (
	Error DiagnosticType = "Error"
)

type DiagnosticOrigin string

const (
	Phase1LexerOrigin DiagnosticOrigin = "Phase1LexerOrigin"
	Phase2LexerOrigin DiagnosticOrigin = "Phase2LexerOrigin"
	Phase3LexerOrigin DiagnosticOrigin = "Phase3LexerOrigin"
)

type Diagnostic struct {
	Type    DiagnosticType
	Origin  DiagnosticOrigin
	Message string
	Row     int
	Column  int
}

type TokenType string

const (
	Name               TokenType = "Name"
	Colon              TokenType = "Colon"
	Text               TokenType = "Text"
	Formulation        TokenType = "Formulation"
	TextBlock          TokenType = "TextBlock"
	Indent             TokenType = "Indent"
	UnIndent           TokenType = "Unindent"
	SameIndent         TokenType = "SameIndent"
	DotSpace           TokenType = "DotSpace"
	LineBreak          TokenType = "LineBreak"
	Id                 TokenType = "Id"
	Newline            TokenType = "Newline"
	ArgumentText       TokenType = "ArgumentText"
	Comma              TokenType = "Comma"
	Space              TokenType = "Space"
	BeginArgumentGroup TokenType = "BeginArgumentGroup"
	EndArgumentGroup   TokenType = "EndArgumentGroup"
	BeginTopLevelGroup TokenType = "BeginTopLevelGroup"
	EndTopLevelGroup   TokenType = "EndTopLevelGroup"
	BeginSection       TokenType = "BeginSection"
	EndSection         TokenType = "EndSection"
	BeginArgument      TokenType = "BeginArgument"
	EndArgument        TokenType = "EndArgument"
)

type Token struct {
	Type   TokenType
	Text   string
	Offset int
	Row    int
	Column int
}
