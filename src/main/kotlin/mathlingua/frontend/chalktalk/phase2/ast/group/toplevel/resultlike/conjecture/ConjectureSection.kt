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

package mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.resultlike.conjecture

import mathlingua.frontend.chalktalk.phase1.ast.ChalkTalkTokenType
import mathlingua.frontend.chalktalk.phase1.ast.Phase1Node
import mathlingua.frontend.chalktalk.phase1.ast.Phase1Token
import mathlingua.frontend.chalktalk.phase1.ast.getColumn
import mathlingua.frontend.chalktalk.phase1.ast.getRow
import mathlingua.frontend.chalktalk.phase2.CodeWriter
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_CONJECTURE_SECTION
import mathlingua.frontend.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.frontend.chalktalk.phase2.ast.track
import mathlingua.frontend.chalktalk.phase2.ast.validateSection
import mathlingua.frontend.support.MutableLocationTracker
import mathlingua.frontend.support.ParseError

data class ConjectureSection(val names: List<String>) : Phase2Node {
    override fun forEach(fn: (node: Phase2Node) -> Unit) {}

    override fun toCode(isArg: Boolean, indent: Int, writer: CodeWriter): CodeWriter {
        writer.writeIndent(isArg, indent)
        writer.writeHeader("Conjecture")
        if (names.size == 1) {
            writer.writeSpace()
            writer.writeText(names[0])
        } else if (names.size > 1) {
            writer.writeNewline()
            for (name in names) {
                writer.writeIndent(true, indent + 2)
                writer.writeText(name)
            }
        }
        return writer
    }

    override fun transform(chalkTransformer: (node: Phase2Node) -> Phase2Node) =
        chalkTransformer(this)
}

fun validateConjectureSection(
    node: Phase1Node, errors: MutableList<ParseError>, tracker: MutableLocationTracker
) =
    track(node, tracker) {
        validateSection(node.resolve(), errors, "Conjecture", DEFAULT_CONJECTURE_SECTION) {
        section ->
            if (section.args.isNotEmpty() &&
                !section.args.all {
                    it.chalkTalkTarget is Phase1Token &&
                        it.chalkTalkTarget.type == ChalkTalkTokenType.String
                }) {
                errors.add(
                    ParseError(
                        message = "Expected a list of strings",
                        row = getRow(section),
                        column = getColumn(section)))
                DEFAULT_CONJECTURE_SECTION
            } else {
                ConjectureSection(
                    names = section.args.map { (it.chalkTalkTarget as Phase1Token).text })
            }
        }
    }
