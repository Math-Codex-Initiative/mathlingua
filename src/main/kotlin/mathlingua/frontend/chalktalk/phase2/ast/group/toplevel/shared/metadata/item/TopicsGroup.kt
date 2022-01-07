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

package mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.shared.metadata.item

import mathlingua.frontend.chalktalk.phase1.ast.Phase1Node
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_TOPICS_GROUP
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_TOPICS_SECTION
import mathlingua.frontend.chalktalk.phase2.ast.clause.firstSectionMatchesName
import mathlingua.frontend.chalktalk.phase2.ast.common.OnePartNode
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.shared.metadata.section.TopicsSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.shared.metadata.section.validateTopicsSection
import mathlingua.frontend.chalktalk.phase2.ast.section.ensureNonNull
import mathlingua.frontend.chalktalk.phase2.ast.section.identifySections
import mathlingua.frontend.chalktalk.phase2.ast.track
import mathlingua.frontend.chalktalk.phase2.ast.validateGroup
import mathlingua.frontend.support.MutableLocationTracker
import mathlingua.frontend.support.ParseError

internal data class TopicsGroup(val topicsSection: TopicsSection) :
    OnePartNode<TopicsSection>(topicsSection, ::TopicsGroup), MetaDataItem

internal fun isTopicsGroup(node: Phase1Node) = firstSectionMatchesName(node, "topics")

internal fun validateTopicsGroup(
    node: Phase1Node, errors: MutableList<ParseError>, tracker: MutableLocationTracker
) =
    track(node, tracker) {
        validateGroup(node.resolve(), errors, "topics", DEFAULT_TOPICS_GROUP) { group ->
            identifySections(group, errors, DEFAULT_TOPICS_GROUP, listOf("topics")) { sections ->
                TopicsGroup(
                    topicsSection =
                        ensureNonNull(sections["topics"], DEFAULT_TOPICS_SECTION) {
                            validateTopicsSection(it, errors, tracker)
                        })
            }
        }
    }
