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

type DescribesInfo struct {
	Input       StaticPattern
	Output      StaticPattern
	Usings      []StaticPattern
	When        []Constraint
	Extends     []Constraint
	ExpAliases  []ExpAliasInfo
	SpecAliases []SpecAliasInfo
	Written     []WrittenInfo
	Called      []CalledInfo
}

type DefinesInfo struct {
	Input       StaticPattern
	Output      StaticPattern
	Usings      []StaticPattern
	When        []Constraint
	Means       []Constraint
	ExpAliases  []ExpAliasInfo
	SpecAliases []SpecAliasInfo
	Written     []WrittenInfo
	Called      []CalledInfo
}

type StatesInfo struct {
	Input       StaticPattern
	Output      StaticPattern
	Usings      []StaticPattern
	ExpAliases  []ExpAliasInfo
	SpecAliases []SpecAliasInfo
	Written     []WrittenInfo
	Called      []CalledInfo
}
