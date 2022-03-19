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

package mathlingua.backend.transform

import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.defines.DefinesGroup
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.providing.viewing.ViewGroup
import mathlingua.frontend.support.ValidationSuccess
import mathlingua.frontend.textalk.IsTexTalkNode
import mathlingua.frontend.textalk.getSignaturesWithin
import mathlingua.newQueue

internal interface TypeManager {
    fun add(defines: DefinesGroup)
    fun remove(defines: DefinesGroup)
    fun isSigDescendentOf(sig: String, targetSig: String): Boolean
    fun isSigViewableAs(sig: String, targetSig: String): Boolean
    fun isSigIs(sig: String, targetSig: String): Boolean
}

internal fun newTypeManager(): TypeManager {
    return TypeManagerImpl()
}

// ----------------------------------------------------------

private class TypeManagerImpl : TypeManager {
    private val sigToDefines = mutableMapOf<String, DefinesGroup>()
    private val sigToParentSig = mutableMapOf<String, String>()
    private val sigToViewableSigs = mutableMapOf<String, MutableSet<String>>()

    override fun add(defines: DefinesGroup) {
        val sig = defines.signature?.form ?: return
        sigToDefines[sig] = defines
        val meansStmt = defines.meansSection?.statements?.get(0)
        when (val root = meansStmt?.texTalkRoot
        ) {
            is ValidationSuccess -> {
                val child = root.value.children[0]
                if (child is IsTexTalkNode) {
                    val parentSig = child.rhs.items[0].getSignaturesWithin().first()
                    sigToParentSig[sig] = parentSig
                }
            }
            else -> {
                // ignore Defines: that have an invalid means: section
            }
        }

        val clauses = defines.providingSection?.clauses?.clauses
        if (clauses != null && clauses.isNotEmpty()) {
            clauses.forEach { clause ->
                if (clause is ViewGroup) {
                    when (val root = clause.viewAsSection.statement.texTalkRoot
                    ) {
                        is ValidationSuccess -> {
                            val sigsWithin = root.value.getSignaturesWithin()
                            if (sigsWithin.isNotEmpty()) {
                                val viewSig = sigsWithin.first()
                                if (sig !in sigToViewableSigs) {
                                    sigToViewableSigs[sig] = mutableSetOf()
                                }
                                sigToViewableSigs[sig]?.add(viewSig)
                            }
                        }
                        else -> {
                            // ignore as: sections that are invalid
                        }
                    }
                }
            }
        }
    }

    override fun remove(defines: DefinesGroup) {
        val sig = defines.signature?.form ?: return
        sigToDefines.remove(sig)
        sigToParentSig.remove(sig)
        sigToViewableSigs.remove(sig)
    }

    override fun isSigDescendentOf(sig: String, targetSig: String): Boolean {
        var cur: String? = sig
        while (cur != null) {
            if (cur == targetSig) {
                return true
            }
            cur = sigToParentSig[cur]
        }
        return false
    }

    override fun isSigViewableAs(sig: String, targetSig: String): Boolean {
        val visited = mutableSetOf<String>()
        val queue = newQueue<String>()
        queue.offer(sig)
        while (!queue.isEmpty()) {
            val size = queue.size()
            for (i in 0 until size) {
                val top = queue.poll()
                visited.add(top)
                if (top == targetSig || isSigDescendentOf(top, targetSig)) {
                    return true
                }
                sigToViewableSigs[top]?.forEach { viewSig ->
                    if (viewSig !in visited) {
                        queue.offer(viewSig)
                    }
                }
            }
        }
        return false
    }

    override fun isSigIs(sig: String, targetSig: String) =
        isSigDescendentOf(sig, targetSig) || isSigViewableAs(sig, targetSig)
}
