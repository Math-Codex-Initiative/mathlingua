/*
 * Copyright 2021 The MathLingua Authors
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

package mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.providing.equality

import mathlingua.frontend.chalktalk.phase1.ast.Phase1Node
import mathlingua.frontend.chalktalk.phase2.CodeWriter
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_BETWEEN_SECTION
import mathlingua.frontend.chalktalk.phase2.ast.clause.Target
import mathlingua.frontend.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.frontend.chalktalk.phase2.ast.section.appendTargetArgs
import mathlingua.frontend.chalktalk.phase2.ast.validateTargetSection
import mathlingua.frontend.support.ParseError

internal data class BetweenSection(
    val targets: List<Target>, override val row: Int, override val column: Int
) : Phase2Node {
    override fun forEach(fn: (node: Phase2Node) -> Unit) = targets.forEach(fn)

    override fun toCode(isArg: Boolean, indent: Int, writer: CodeWriter): CodeWriter {
        writer.writeIndent(isArg, indent)
        writer.writeHeader("between")
        appendTargetArgs(writer, targets, indent + 2)
        return writer
    }

    override fun transform(chalkTransformer: (node: Phase2Node) -> Phase2Node) =
        chalkTransformer(
            BetweenSection(
                targets = targets.map { it.transform(chalkTransformer) as Target }, row, column))
}

internal fun validateBetweenSection(
    node: Phase1Node, errors: MutableList<ParseError>
): BetweenSection {
    val result =
        validateTargetSection(
            node.resolve(),
            errors,
            "between",
            DEFAULT_BETWEEN_SECTION,
            node.row,
            node.column,
            ::BetweenSection)
    return if (result == DEFAULT_BETWEEN_SECTION) {
        result
    } else {
        if (result.targets.size != 2) {
            errors.add(
                ParseError(
                    message = "A between: section must have exactly two between: sections",
                    row = node.row,
                    column = node.column))
            DEFAULT_BETWEEN_SECTION
        } else {
            result
        }
    }
}
