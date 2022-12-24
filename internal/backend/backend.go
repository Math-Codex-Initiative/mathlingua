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

package backend

type OperationCall interface {
	OperationCall()
}

func (fc FunctionCall) OperationCall()         {}
func (cc CommandCall) OperationCall()          {}
func (poc PrefixOperatorCall) OperationCall()  {}
func (poc PostfixOperatorCall) OperationCall() {}
func (ioc InfixOperatorCall) OperationCall()   {}

type FunctionCall struct {
}

type CommandCall struct {
}

type PrefixOperatorCall struct {
}

type PostfixOperatorCall struct {
}

type InfixOperatorCall struct {
}

type Operation struct {
	Lhs OperationCall
	Rhs OperationCall
}

//////////////////////////////////////////////////////////////////////

type EntityCollection interface {
}
