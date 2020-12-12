/*
 * Copyright 2020 Google LLC
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

package mathlingua.chalktalk.phase2.ast.group.clause.mapping

import mathlingua.chalktalk.phase1.ast.Phase1Node
import mathlingua.chalktalk.phase1.ast.Section
import mathlingua.chalktalk.phase2.CodeWriter
import mathlingua.chalktalk.phase2.ast.DEFAULT_TO_SECTION
import mathlingua.chalktalk.phase2.ast.clause.Statement
import mathlingua.chalktalk.phase2.ast.clause.neoValidateStatement
import mathlingua.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.chalktalk.phase2.ast.neoValidateSection
import mathlingua.chalktalk.phase2.ast.section.validateStatementListSection
import mathlingua.support.MutableLocationTracker
import mathlingua.support.ParseError

data class ToSection(val statements: List<Statement>) : Phase2Node {
    override fun forEach(fn: (node: Phase2Node) -> Unit) {
        statements.forEach(fn)
    }

    override fun toCode(isArg: Boolean, indent: Int, writer: CodeWriter): CodeWriter {
        writer.writeIndent(isArg, indent)
        writer.writeHeader("to")
        if (statements.size > 1) {
            for (stmt in statements) {
                writer.writeNewline()
                writer.append(stmt, true, indent + 2)
            }
        } else {
            writer.append(statements[0], false, 1)
        }
        return writer
    }

    override fun transform(chalkTransformer: (node: Phase2Node) -> Phase2Node) =
        chalkTransformer(
            ToSection(statements = statements.map { chalkTransformer(it) as Statement }))
}

fun isToSection(sec: Section) = sec.name.text == "to"

fun validateToSection(node: Phase1Node, tracker: MutableLocationTracker) =
    validateStatementListSection(node, tracker, "to", ::ToSection)

fun neoValidateToSection(node: Phase1Node, errors: MutableList<ParseError>) =
    neoValidateSection(node, errors, "to", DEFAULT_TO_SECTION) {
        ToSection(
            statements = it.args.map { arg -> neoValidateStatement(arg, errors) }
        )
    }
