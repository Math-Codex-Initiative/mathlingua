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

package mathlingua.common.chalktalk.phase2.ast.group.clause.`if`

import mathlingua.common.support.MutableLocationTracker
import mathlingua.common.chalktalk.phase1.ast.Phase1Node
import mathlingua.common.chalktalk.phase2.CodeWriter
import mathlingua.common.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.common.chalktalk.phase2.ast.clause.Clause
import mathlingua.common.chalktalk.phase2.ast.clause.firstSectionMatchesName
import mathlingua.common.chalktalk.phase2.ast.clause.validateDoubleSectionGroup

data class IfGroup(
    val ifSection: IfSection,
    val thenSection: ThenSection
) : Clause {
    override fun forEach(fn: (node: Phase2Node) -> Unit) {
        fn(ifSection)
        fn(thenSection)
    }

    override fun toCode(isArg: Boolean, indent: Int, writer: CodeWriter): CodeWriter {
        val sections = mutableListOf(ifSection, thenSection)
        return mathlingua.common.chalktalk.phase2.ast.clause.toCode(writer, isArg, indent, *sections.toTypedArray())
    }

    override fun transform(chalkTransformer: (node: Phase2Node) -> Phase2Node) = chalkTransformer(
        IfGroup(
            ifSection = ifSection.transform(chalkTransformer) as IfSection,
            thenSection = thenSection.transform(chalkTransformer) as ThenSection
        )
    )
}

fun isIfGroup(node: Phase1Node) = firstSectionMatchesName(node, "if")

fun validateIfGroup(rawNode: Phase1Node, tracker: MutableLocationTracker) = validateDoubleSectionGroup(
    tracker,
    rawNode,
    "if",
    ::validateIfSection,
    "then",
    ::validateThenSection,
    ::IfGroup
)
