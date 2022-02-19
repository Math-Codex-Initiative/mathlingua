/*
 * Copyright 2020 The MathLingua Authors
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

package mathlingua.frontend.chalktalk.phase2.ast.clause

import mathlingua.frontend.chalktalk.phase1.ast.Abstraction
import mathlingua.frontend.chalktalk.phase1.ast.Phase1Node
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_ABSTRACTION
import mathlingua.frontend.chalktalk.phase2.ast.common.ZeroPartNode
import mathlingua.frontend.chalktalk.phase2.ast.validateByTransform
import mathlingua.frontend.support.ParseError

internal data class AbstractionNode(
    val abstraction: Abstraction, override val row: Int, override val column: Int
) : ZeroPartNode(abstraction), Target

internal fun isAbstraction(node: Phase1Node) = node is Abstraction

internal fun validateAbstractionNode(node: Phase1Node, errors: MutableList<ParseError>) =
    validateByTransform(
        node = node.resolve(),
        errors = errors,
        default = DEFAULT_ABSTRACTION,
        message = "Expected an abstraction",
        transform = { it as? Abstraction },
        builder = ::AbstractionNode)
