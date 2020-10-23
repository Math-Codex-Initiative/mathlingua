/*
 * Copyright 2019 Google LLC
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

package mathlingua.common.transform

import mathlingua.common.support.ValidationFailure
import mathlingua.common.support.ValidationSuccess
import mathlingua.common.chalktalk.phase2.*
import mathlingua.common.chalktalk.phase2.ast.Document
import mathlingua.common.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.common.chalktalk.phase2.ast.clause.*
import mathlingua.common.chalktalk.phase2.ast.group.clause.`for`.ForGroup
import mathlingua.common.chalktalk.phase2.ast.group.clause.`for`.ForSection
import mathlingua.common.chalktalk.phase2.ast.group.clause.`if`.IfGroup
import mathlingua.common.chalktalk.phase2.ast.group.clause.`if`.IfSection
import mathlingua.common.chalktalk.phase2.ast.group.clause.`if`.ThenSection
import mathlingua.common.chalktalk.phase2.ast.group.clause.exists.ExistsGroup
import mathlingua.common.chalktalk.phase2.ast.group.toplevel.defineslike.defines.DefinesGroup
import mathlingua.common.chalktalk.phase2.ast.group.toplevel.defineslike.defines.MeansSection
import mathlingua.common.chalktalk.phase2.ast.group.toplevel.defineslike.states.StatesGroup
import mathlingua.common.chalktalk.phase2.ast.group.toplevel.shared.WhereSection
import mathlingua.common.textalk.Command
import mathlingua.common.textalk.ExpressionTexTalkNode
import mathlingua.common.textalk.IsTexTalkNode
import mathlingua.common.textalk.ParametersTexTalkNode
import mathlingua.common.textalk.TexTalkNode
import mathlingua.common.textalk.TexTalkNodeType
import mathlingua.common.textalk.TexTalkTokenType
import mathlingua.common.textalk.TextTexTalkNode
import mathlingua.common.textalk.getTexTalkAncestry
import mathlingua.common.support.validationSuccess

internal fun getKey(node: Phase2Node): String {
    val str = node.toString()
    return str.replace(Regex("row=-?\\d+"), "ROW")
            .replace(Regex("column=-?\\d+"), "COLUMN")
}

internal fun moveInlineCommandsToIsNode(
    defs: List<DefinesGroup>,
    root: Phase2Node,
    target: Phase2Node // non-null targets a specific node
                       // and null targets all ClauseListNodes
): RootTarget<Phase2Node, Phase2Node> {
    val knownDefSigs = defs.map { it.signature }.filterNotNull().toSet()
    fun realShouldProcessTex(root: TexTalkNode, node: TexTalkNode): Boolean {
        if (node is Command && !knownDefSigs.contains(node.signature())) {
            return false
        }

        val parents = getTexTalkAncestry(root, node)
        for (p in parents) {
            if (p is IsTexTalkNode) {
                return false
            }
        }

        return true
    }

    var seed = 0
    var newTarget: Phase2Node? = null
    val newRoot = root.transform {
        val result = if (it is ClauseListNode && (hasChild(it, target) || hasChild(target, it))) {
            val newClauses = mutableListOf<Clause>()
            for (c in it.clauses) {
                if (c is Statement) {
                    val transformed = moveStatementInlineCommandsToIsNode(
                        seed++,
                        c,
                        { true },
                        ::realShouldProcessTex
                    )
                    newClauses.add(transformed)
                } else {
                    newClauses.add(c)
                }
            }
            val result = ClauseListNode(clauses = newClauses)
            if (newTarget == null && hasChild(it, target)) {
                newTarget = result
            }
            result
        } else {
            it
        }
        result
    }
    return RootTarget(
            root = newRoot,
            target = newTarget ?: target
    )
}

internal fun moveStatementInlineCommandsToIsNode(
    seed: Int,
    stmt: Statement,
    shouldProcessChalk: (node: Phase2Node) -> Boolean,
    shouldProcessTex: (root: TexTalkNode, node: TexTalkNode) -> Boolean
): Clause {
    val validation = stmt.texTalkRoot
    if (validation is ValidationFailure) {
        return stmt
    }
    val root = (validation as ValidationSuccess).value

    if (!shouldProcessChalk(stmt)) {
        return stmt
    }

    fun shouldProcessTexNodes(root: TexTalkNode, node: TexTalkNode): Boolean {
        if (!shouldProcessTex(root, node)) {
            return false
        }

        return !getTexTalkAncestry(root, node).any { it is IsTexTalkNode }
    }

    val commandsFound = findCommands(root)
    val cmdToReplacement = mutableMapOf<Command, String>()
    var count = seed
    for (cmd in commandsFound) {
        if (shouldProcessTex(root, cmd)) {
            cmdToReplacement[cmd] = "\$${count++}"
        }
    }

    val cmdsToProcess = cmdToReplacement.keys
    val newNode = replaceCommands(stmt, cmdToReplacement, shouldProcessChalk, ::shouldProcessTexNodes) as Clause

    if (commandsFound.isEmpty()) {
        return stmt
    }

    if (cmdsToProcess.isEmpty()) {
        return stmt
    }

    return ForGroup(
            forSection = ForSection(
                    targets = cmdsToProcess.map {
                        Identifier(
                                name = cmdToReplacement[it]!!,
                                isVarArgs = false)
                    }
            ),
            whereSection = WhereSection(
                    clauses = ClauseListNode(
                            clauses = cmdsToProcess.map {
                                val isNode = IsTexTalkNode(
                                        lhs = ParametersTexTalkNode(
                                                items = listOf(
                                                        ExpressionTexTalkNode(
                                                                children = listOf(
                                                                        TextTexTalkNode(
                                                                                type = TexTalkNodeType.Identifier,
                                                                                tokenType = TexTalkTokenType.Identifier,
                                                                                text = cmdToReplacement[it]!!,
                                                                                isVarArg = false
                                                                        )
                                                                )
                                                        )
                                                )
                                        ),
                                        rhs = ParametersTexTalkNode(
                                                items = listOf(
                                                        ExpressionTexTalkNode(
                                                                children = listOf(it)
                                                        )
                                                )
                                        )
                                )

                                Statement(
                                        text = isNode.toCode(),
                                        texTalkRoot = validationSuccess(
                                                ExpressionTexTalkNode(
                                                        children = listOf(isNode)
                                                )
                                        )
                                )
                            }
                    )
            ),
            thenSection = ThenSection(
                    clauses = ClauseListNode(
                            clauses = listOf(newNode)
                    )
            )
    )
}

internal fun replaceRepresents(
    root: Phase2Node,
    represents: List<StatesGroup>,
    target: Phase2Node
): RootTarget<Phase2Node, Phase2Node> {
    val repMap = mutableMapOf<String, StatesGroup>()
    for (rep in represents) {
        val sig = rep.signature
        if (sig != null) {
            repMap[sig] = rep
        }
    }

    var newTarget: Phase2Node? = null
    fun chalkTransformer(node: Phase2Node): Phase2Node {
        if (node !is ClauseListNode || (!hasChild(node, target) && !hasChild(target, node))) {
            return node
        }

        val newClauses = mutableListOf<Clause>()

        for (clause in node.clauses) {
            if (clause !is Statement) {
                newClauses.add(clause)
                continue
            }

            if (clause.texTalkRoot is ValidationSuccess &&
                clause.texTalkRoot.value.children.size == 1 &&
                clause.texTalkRoot.value.children[0] is Command
            ) {
                // a prefix command
                val command = clause.texTalkRoot.value.children[0] as Command
                val sig = command.signature()

                if (!repMap.containsKey(sig)) {
                    return node
                }

                val rep = repMap[sig]!!
                val cmdVars = getVars(command)
                val defIndirectVars = getRepresentsIdVars(rep)

                val map = mutableMapOf<String, String>()
                for (i in cmdVars.indices) {
                    map[defIndirectVars[i]] = cmdVars[i]
                }

                val ifThen = buildIfThens(rep)[0]
                if (ifThen.ifSection.clauses.clauses.isEmpty()) {
                    for (c in ifThen.thenSection.clauses.clauses) {
                        newClauses.add(renameVars(c, map) as Clause)
                    }
                } else {
                    newClauses.add(renameVars(ifThen, map) as Clause)
                }
            } else if (clause.texTalkRoot is ValidationSuccess &&
                clause.texTalkRoot.value.children.size == 3 &&
                clause.texTalkRoot.value.children[0] is TextTexTalkNode &&
                clause.texTalkRoot.value.children[1] is Command &&
                clause.texTalkRoot.value.children[2] is TextTexTalkNode) {
                // and infix command
                val left = clause.texTalkRoot.value.children[0] as TextTexTalkNode
                val op = clause.texTalkRoot.value.children[1] as Command
                val right = clause.texTalkRoot.value.children[2] as TextTexTalkNode

                val sig = op.signature()

                if (!repMap.containsKey(sig)) {
                    return node
                }

                val rep = repMap[sig]!!
                val cmdVars = listOf(left.text, right.text)

                if (rep.id.texTalkRoot is ValidationFailure) {
                    return node
                }

                val validation = rep.id.texTalkRoot as ValidationSuccess
                if (validation.value.children.size != 3 ||
                    validation.value.children[0] !is TextTexTalkNode ||
                    validation.value.children[1] !is Command ||
                    validation.value.children[2] !is TextTexTalkNode) {
                    return node
                }

                val repLeftOpRight = validation.value.children
                val repLeft = (repLeftOpRight[0] as TextTexTalkNode).text
                val repRight = (repLeftOpRight[2] as TextTexTalkNode).text

                val defIndirectVars = listOf(repLeft, repRight)

                val map = mutableMapOf<String, String>()
                for (i in cmdVars.indices) {
                    map[defIndirectVars[i]] = cmdVars[i]
                }

                val ifThen = buildIfThens(rep)[0]
                if (ifThen.ifSection.clauses.clauses.isEmpty()) {
                    for (c in ifThen.thenSection.clauses.clauses) {
                        newClauses.add(renameVars(c, map) as Clause)
                    }
                } else {
                    newClauses.add(renameVars(ifThen, map) as Clause)
                }
            } else {
                newClauses.add(clause)
            }
        }
        val result = ClauseListNode(clauses = newClauses)
        if (newTarget == null && hasChild(node, target)) {
            newTarget = result
        }
        return result
    }

    val newRoot = root.transform(::chalkTransformer)
    return RootTarget(
            root = newRoot,
            target = newTarget ?: target
    )
}

internal fun replaceIsNodes(
    root: Phase2Node,
    defs: List<DefinesGroup>,
    target: Phase2Node
): RootTarget<Phase2Node, Phase2Node> {
    val defMap = mutableMapOf<String, DefinesGroup>()
    for (def in defs) {
        val sig = def.signature
        if (sig != null) {
            defMap[sig] = def
        }
    }

    var newTarget: Phase2Node? = null
    fun chalkTransformer(node: Phase2Node): Phase2Node {
        if (node !is ClauseListNode || (!hasChild(node, target) && !hasChild(target, node))) {
            return node
        }

        val newClauses = mutableListOf<Clause>()
        for (c in node.clauses) {
            if (c !is Statement) {
                newClauses.add(c)
                continue
            }

            if (c.texTalkRoot is ValidationFailure ||
                (c.texTalkRoot as ValidationSuccess).value.children.size != 1 ||
                c.texTalkRoot.value.children[0] !is IsTexTalkNode
            ) {
                newClauses.add(c)
                continue
            }

            val isNode = c.texTalkRoot.value.children[0] as IsTexTalkNode
            if (isNode.rhs.items.size != 1 ||
                isNode.rhs.items[0].children.size != 1 ||
                isNode.rhs.items[0].children[0] !is Command
            ) {
                newClauses.add(c)
                continue
            }

            val command = isNode.rhs.items[0].children[0] as Command
            val sig = command.signature()

            if (!defMap.containsKey(sig)) {
                newClauses.add(c)
                continue
            }

            val def = defMap[sig]!!
            val cmdVars = getVars(command)

            val defDirectVars = getDefinesDirectVars(def)
            val defIndirectVars = getDefinesIdVars(def)

            if (cmdVars.size != defIndirectVars.size) {
                newClauses.add(c)
                continue
            }

            val map = mutableMapOf<String, String>()
            for (i in cmdVars.indices) {
                map[defIndirectVars[i]] = cmdVars[i]
            }

            val stmtLhsVars = getVars(isNode.lhs)
            val lhsAncestry = getChalkTalkAncestry(root, c)

            val forVarMap = mutableMapOf<String, Phase2Node>()
            fun addVarToMap(v: Phase2Node) {
                when (v) {
                    is AssignmentNode -> {
                        val name = v.assignment.lhs.text
                        if (!forVarMap.containsKey(name)) {
                            forVarMap[name] = v
                        }
                    }
                    is AbstractionNode -> {
                        for (part in v.abstraction.parts) {
                            val name = part.name.text
                            if (!forVarMap.containsKey(name)) {
                                forVarMap[name] = v
                            }
                        }
                    }
                    is Identifier -> {
                        val name = v.name
                        if (!forVarMap.containsKey(name)) {
                            forVarMap[name] = v
                        }
                    }
                }
            }

            for (parent in lhsAncestry) {
                when (parent) {
                    is ForGroup -> {
                        for (v in parent.forSection.targets) {
                            addVarToMap(v)
                        }
                    }
                    is ExistsGroup -> {
                        for (v in parent.existsSection.identifiers) {
                            addVarToMap(v)
                        }
                    }
                }
            }

            val lhsVars = mutableListOf<String>()
            for (v in stmtLhsVars) {
                if (forVarMap.containsKey(v)) {
                    lhsVars.addAll(getVars(forVarMap[v]!!))
                } else {
                    lhsVars.add(v)
                }
            }

            if (lhsVars.size > defDirectVars.size) {
                newClauses.add(c)
                continue
            }

            for (i in lhsVars.indices) {
                map[defDirectVars[i]] = lhsVars[i]
            }

            val ifThen = buildIfThens(def)
            if (ifThen.ifSection.clauses.clauses.isEmpty()) {
                for (thenClause in ifThen.thenSection.clauses.clauses) {
                    newClauses.add(renameVars(thenClause, map) as Clause)
                }
            } else {
                newClauses.add(renameVars(ifThen, map) as Clause)
            }
        }
        val result = ClauseListNode(clauses = newClauses)
        if (newTarget == null && hasChild(node, target)) {
            newTarget = result
        }
        return result
    }

    val newRoot = root.transform(::chalkTransformer)
    return RootTarget(
            root = newRoot,
            target = newTarget ?: target
    )
}

internal fun toCanonicalForm(def: DefinesGroup) = DefinesGroup(
        signature = def.signature,
        id = def.id,
        definesSection = def.definesSection,
        whenSection = null,
        meansSection = MeansSection(
                clauses = ClauseListNode(
                        clauses = listOf(buildIfThens(def))
                )
        ),
        usingSection = def.usingSection,
        metaDataSection = def.metaDataSection,
        evaluatedSection = def.evaluatedSection,
        writtenSection = def.writtenSection
)

internal fun buildIfThens(def: DefinesGroup) =
    IfGroup(
            ifSection = IfSection(
                    clauses = def.whenSection?.clauses
                            ?: ClauseListNode(clauses = emptyList())
            ),
            thenSection = ThenSection(
                    clauses = def.meansSection?.clauses ?: ClauseListNode(
                        clauses = emptyList()
                    )
            ),
            elseIfSections = emptyList(),
            elseSection = null
    )

internal fun buildIfThens(rep: StatesGroup) = rep.thatSections.map {
    IfGroup(
            ifSection = IfSection(
                    clauses = rep.whenSection?.clauses
                            ?: ClauseListNode(
                                    clauses = emptyList()
                            )
            ),
            thenSection = ThenSection(
                    clauses = it.clauses
            ),
            elseIfSections = emptyList(),
            elseSection = null
    )
}

internal fun getDefinesDirectVars(def: DefinesGroup): List<String> {
    val vars = mutableListOf<String>()
    for (target in def.definesSection.targets) {
        vars.addAll(getVars(target))
    }
    return vars
}

internal fun getDefinesIdVars(def: DefinesGroup): List<String> {
    val vars = mutableListOf<String>()
    if (def.id.texTalkRoot is ValidationSuccess) {
        vars.addAll(getVars(def.id.texTalkRoot.value))
    }
    return vars
}

internal fun getRepresentsIdVars(rep: StatesGroup): List<String> {
    val vars = mutableListOf<String>()
    if (rep.id.texTalkRoot is ValidationSuccess) {
        vars.addAll(getVars(rep.id.texTalkRoot.value))
    }
    return vars
}

internal fun expandAtNode(
    root: Phase2Node,
    target: Phase2Node,
    defines: List<DefinesGroup>,
    represents: List<StatesGroup>
): Phase2Node {
    var transformed = root
    var realTarget = target

    val sepIsPair = separateIsStatements(transformed, realTarget)
    transformed = sepIsPair.root
    realTarget = sepIsPair.target

    val sepInfixPair = separateInfixOperatorStatements(transformed, realTarget)
    transformed = sepInfixPair.root
    realTarget = sepInfixPair.target

    val gluePair = glueCommands(transformed, realTarget)
    transformed = gluePair.root
    realTarget = gluePair.target

    val mvInlineCmdsPair = moveInlineCommandsToIsNode(defines, transformed, realTarget)
    transformed = mvInlineCmdsPair.root
    realTarget = mvInlineCmdsPair.target

    val replaceRepsPair = replaceRepresents(transformed, represents, realTarget)
    transformed = replaceRepsPair.root
    realTarget = replaceRepsPair.target

    val replaceIsPair = replaceIsNodes(transformed, defines, realTarget)
    transformed = replaceIsPair.root

    return transformed
}

internal fun fullExpandOnce(doc: Document) = expandAtNode(doc, doc, doc.defines(), doc.states()) as Document

internal fun fullExpandComplete(doc: Document, maxSteps: Int = 10): Document {
    val snapshots = mutableSetOf<String>()

    var transformed = doc
    var previousCode = transformed.toCode(false, 0).getCode()
    snapshots.add(previousCode)

    for (i in 0 until maxSteps) {
        transformed = fullExpandOnce(transformed)
        val code = transformed.toCode(false, 0).getCode()
        if (snapshots.contains(code) || previousCode == code) {
            break
        }
        previousCode = code
        snapshots.add(previousCode)
    }

    return transformed
}

internal fun separateInfixOperatorStatements(root: Phase2Node, follow: Phase2Node): RootTarget<Phase2Node, Phase2Node> {
    var newFollow: Phase2Node? = null
    val newRoot = root.transform {
        val result = if (it is ClauseListNode) {
            val newClauses = mutableListOf<Clause>()
            for (c in it.clauses) {
                if (c is Statement) {
                    when (val validation = c.texTalkRoot) {
                        is ValidationSuccess -> {
                            val expRoot = validation.value
                            for (expanded in getExpandedInfixOperators(expRoot)) {
                                newClauses.add(Statement(
                                        text = expanded.toCode(),
                                        texTalkRoot = validationSuccess(expanded)
                                ))
                            }
                        }
                        is ValidationFailure -> newClauses.add(c)
                    }
                } else {
                    newClauses.add(c)
                }
            }
            val result = ClauseListNode(
                    clauses = newClauses
            )
            if (newFollow == null && hasChild(it, follow)) {
                newFollow = result
            }
            result
        } else {
            it
        }
        result
    }
    return RootTarget(
            root = newRoot,
            target = newFollow ?: follow
    )
}

private fun getSingleInfixOperatorIndex(exp: ExpressionTexTalkNode): Int {
    for (i in 1 until exp.children.size - 1) {
        val prev = exp.children[i - 1]
        val cur = exp.children[i]
        val next = exp.children[i + 1]
        if (!isOperator(prev) && cur is Command && !isOperator(next)) {
            return i
        }
    }

    return -1
}

private fun isComma(node: TexTalkNode) = node is TextTexTalkNode && node.text == ","

private fun isOperator(node: TexTalkNode): Boolean {
    if (node !is TextTexTalkNode) {
        return false
    }

    if (node.text.isBlank()) {
        return false
    }

    for (c in node.text) {
        if (!isOpChar(c)) {
            return false
        }
    }

    return true
}

private fun isOpChar(c: Char) = (c == '!' || c == '@' || c == '%' || c == '&' ||
        c == '*' || c == '-' || c == '+' ||
        c == '=' || c == '|' || c == '/' ||
        c == '<' || c == '>')

private fun getArguments(exp: ExpressionTexTalkNode, start: Int, end: Int): List<TexTalkNode> {
    val result = mutableListOf<TexTalkNode>()
    var i = start
    while (i < end) {
        val argChildren = mutableListOf<TexTalkNode>()
        while (i < end && !isComma(exp.children[i])) {
            argChildren.add(exp.children[i++])
        }

        if (i < end && isComma(exp.children[i])) {
            i++ // skip the comma
        }

        if (argChildren.size == 1) {
            result.add(argChildren[0])
        } else {
            result.add(ExpressionTexTalkNode(children = argChildren))
        }
    }
    return result
}

private fun getExpandedInfixOperators(exp: ExpressionTexTalkNode): List<ExpressionTexTalkNode> {
    val opIndex = getSingleInfixOperatorIndex(exp)
    if (opIndex < 0) {
        return listOf(exp)
    }

    val leftArgs = getArguments(exp, 0, opIndex)
    val rightArgs = getArguments(exp, opIndex + 1, exp.children.size)

    val result = mutableListOf<ExpressionTexTalkNode>()

    val op = exp.children[opIndex]
    for (left in leftArgs) {
        for (right in rightArgs) {
            result.add(
                ExpressionTexTalkNode(
                    children = listOf(
                        left,
                        op,
                        right
                    )
                )
            )
        }
    }

    return result
}
