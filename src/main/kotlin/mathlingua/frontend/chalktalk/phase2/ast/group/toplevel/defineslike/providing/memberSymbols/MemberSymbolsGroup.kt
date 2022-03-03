/*
 * Copyright 2022 The MathLingua Authors
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

package mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.providing.memberSymbols

import mathlingua.frontend.chalktalk.phase1.ast.Phase1Node
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_MEMBER_SYMBOLS_GROUP
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_MEMBER_SYMBOLS_SECTION
import mathlingua.frontend.chalktalk.phase2.ast.clause.Clause
import mathlingua.frontend.chalktalk.phase2.ast.clause.firstSectionMatchesName
import mathlingua.frontend.chalktalk.phase2.ast.common.TwoPartNode
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.providing.symbols.WhereSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.providing.symbols.validateWhereSection
import mathlingua.frontend.chalktalk.phase2.ast.section.ensureNonNull
import mathlingua.frontend.chalktalk.phase2.ast.section.identifySections
import mathlingua.frontend.chalktalk.phase2.ast.section.ifNonNull
import mathlingua.frontend.chalktalk.phase2.ast.validateGroup
import mathlingua.frontend.support.ParseError

internal data class MemberSymbolsGroup(
    val memberSymbolsSection: MemberSymbolsSection,
    val whereSection: WhereSection?,
    override val row: Int,
    override val column: Int
) :
    TwoPartNode<MemberSymbolsSection, WhereSection?>(
        memberSymbolsSection, whereSection, row, column, ::MemberSymbolsGroup),
    Clause

internal fun isMemberSymbolsGroup(node: Phase1Node) = firstSectionMatchesName(node, "memberSymbols")

internal fun validateMemberSymbolsGroup(node: Phase1Node, errors: MutableList<ParseError>) =
    validateGroup(node.resolve(), errors, "memberSymbols", DEFAULT_MEMBER_SYMBOLS_GROUP) { group ->
        identifySections(
            group, errors, DEFAULT_MEMBER_SYMBOLS_GROUP, listOf("memberSymbols", "where?")) {
        sections ->
            MemberSymbolsGroup(
                memberSymbolsSection =
                    ensureNonNull(sections["memberSymbols"], DEFAULT_MEMBER_SYMBOLS_SECTION) {
                        validateMemberSymbolsSection(it, errors)
                    },
                whereSection = ifNonNull(sections["where"]) { validateWhereSection(it, errors) },
                row = node.row,
                column = node.column)
        }
    }
