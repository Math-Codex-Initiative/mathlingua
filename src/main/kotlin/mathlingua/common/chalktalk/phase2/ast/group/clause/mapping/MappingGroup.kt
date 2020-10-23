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

package mathlingua.common.chalktalk.phase2.ast.group.clause.mapping

import mathlingua.common.chalktalk.phase1.ast.*
import mathlingua.common.chalktalk.phase2.CodeWriter
import mathlingua.common.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.common.chalktalk.phase2.ast.clause.Clause
import mathlingua.common.chalktalk.phase2.ast.clause.firstSectionMatchesName
import mathlingua.common.chalktalk.phase2.ast.group.clause.expands.AsSection
import mathlingua.common.chalktalk.phase2.ast.group.clause.expands.validateAsSection
import mathlingua.common.chalktalk.phase2.ast.section.identifySections
import mathlingua.common.support.MutableLocationTracker
import mathlingua.common.support.ParseError
import mathlingua.common.support.Validation
import mathlingua.common.support.ValidationFailure
import mathlingua.common.support.ValidationSuccess
import mathlingua.common.support.validationFailure
import mathlingua.common.support.validationSuccess

data class MappingGroup(
    val mappingSection: MappingSection,
    val fromSection: FromSection,
    val toSection: ToSection,
    val asSection: AsSection
) : Clause {
    override fun forEach(fn: (node: Phase2Node) -> Unit) {
        fn(mappingSection)
        fn(fromSection)
        fn(toSection)
        fn(asSection)
    }

    override fun toCode(isArg: Boolean, indent: Int, writer: CodeWriter) =
        mathlingua.common.chalktalk.phase2.ast.clause.toCode(
            writer,
            isArg,
            indent,
            mappingSection,
            fromSection,
            toSection,
            asSection
        )

    override fun transform(chalkTransformer: (node: Phase2Node) -> Phase2Node) = chalkTransformer(
        MappingGroup(
        mappingSection = mappingSection.transform(chalkTransformer) as MappingSection,
        fromSection = fromSection.transform(chalkTransformer) as FromSection,
        toSection = toSection.transform(chalkTransformer) as ToSection,
        asSection = asSection.transform(chalkTransformer) as AsSection
    )
    )
}

fun isMappingGroup(node: Phase1Node) = firstSectionMatchesName(node, "mapping")

fun validateMappingGroup(rawNode: Phase1Node, tracker: MutableLocationTracker): Validation<MappingGroup> {
    val node = rawNode.resolve()

    val errors = ArrayList<ParseError>()
    if (node !is Group) {
        errors.add(
            ParseError(
                "Expected a Group",
                getRow(node), getColumn(node)
            )
        )
        return validationFailure(errors)
    }

    val (sections) = node

    val sectionMap: Map<String, Section>
    try {
        sectionMap = identifySections(
            sections,
            "mapping", "from", "to", "as"
        )
    } catch (e: ParseError) {
        errors.add(ParseError(e.message, e.row, e.column))
        return validationFailure(errors)
    }

    val mappingNode = sectionMap["mapping"]!!
    val fromNode = sectionMap["from"]!!
    val toNode = sectionMap["to"]!!
    val asNode = sectionMap["as"]!!

    var mappingSection: MappingSection? = null
    when (val evaluation = validateMappingSection(mappingNode, tracker)) {
        is ValidationSuccess -> mappingSection = evaluation.value
        is ValidationFailure -> errors.addAll(evaluation.errors)
    }

    var fromSection: FromSection? = null
    when (val evaluation = validateFromSection(fromNode, tracker)) {
        is ValidationSuccess -> fromSection = evaluation.value
        is ValidationFailure -> errors.addAll(evaluation.errors)
    }

    var toSection: ToSection? = null
    when (val evaluation = validateToSection(toNode, tracker)) {
        is ValidationSuccess -> toSection = evaluation.value
        is ValidationFailure -> errors.addAll(evaluation.errors)
    }

    var asSection: AsSection? = null
    when (val evaluation = validateAsSection(asNode, tracker)) {
        is ValidationSuccess -> asSection = evaluation.value
        is ValidationFailure -> errors.addAll(evaluation.errors)
    }

    return if (errors.isNotEmpty()) {
        validationFailure(errors)
    } else validationSuccess(
        tracker,
        rawNode,
        MappingGroup(
            mappingSection!!,
            fromSection!!,
            toSection!!,
            asSection!!
        )
    )
}
