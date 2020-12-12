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

package mathlingua.chalktalk.phase2.ast.group.clause.iff

import mathlingua.chalktalk.phase1.ast.Phase1Node
import mathlingua.chalktalk.phase2.CodeWriter
import mathlingua.chalktalk.phase2.ast.DEFAULT_IFF_SECTION
import mathlingua.chalktalk.phase2.ast.DEFAULT_SUCH_THAT_SECTION
import mathlingua.chalktalk.phase2.ast.clause.ClauseListNode
import mathlingua.chalktalk.phase2.ast.clause.neoValidateClauseListNode
import mathlingua.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.chalktalk.phase2.ast.group.clause.exists.SuchThatSection
import mathlingua.chalktalk.phase2.ast.neoValidateSection
import mathlingua.chalktalk.phase2.ast.validator.AtLeast
import mathlingua.chalktalk.phase2.ast.validator.validateClauseList
import mathlingua.support.MutableLocationTracker
import mathlingua.support.ParseError

data class IffSection(val clauses: ClauseListNode) : Phase2Node {
    override fun forEach(fn: (node: Phase2Node) -> Unit) = clauses.forEach(fn)

    override fun toCode(isArg: Boolean, indent: Int, writer: CodeWriter): CodeWriter {
        writer.writeIndent(isArg, indent)
        writer.writeHeader("iff")
        if (clauses.clauses.isNotEmpty()) {
            writer.writeNewline()
        }
        writer.append(clauses, true, indent + 2)
        return writer
    }

    override fun transform(chalkTransformer: (node: Phase2Node) -> Phase2Node) =
        chalkTransformer(
            IffSection(clauses = clauses.transform(chalkTransformer) as ClauseListNode))
}

fun validateIffSection(node: Phase1Node, tracker: MutableLocationTracker) =
    validateClauseList(AtLeast(1), tracker, node, "iff", ::IffSection)

fun neoValidateIffSection(node: Phase1Node, errors: MutableList<ParseError>) =
    neoValidateSection(node, errors, "iff", DEFAULT_IFF_SECTION) {
        IffSection(
            clauses = neoValidateClauseListNode(it, errors)
        )
    }
