/*
 * Copyright 2022 Dominic Kramer
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

package mathlingua.lib.frontend.textalk

import java.util.LinkedList
import mathlingua.lib.frontend.Diagnostic
import mathlingua.lib.frontend.DiagnosticOrigin
import mathlingua.lib.frontend.DiagnosticType
import mathlingua.lib.frontend.MetaData
import mathlingua.lib.frontend.ast.AsExpression
import mathlingua.lib.frontend.ast.AssignmentIsFormItem
import mathlingua.lib.frontend.ast.CommandExpression
import mathlingua.lib.frontend.ast.CommandForm
import mathlingua.lib.frontend.ast.DEFAULT_NAME
import mathlingua.lib.frontend.ast.DefinitionIsFormItem
import mathlingua.lib.frontend.ast.EmptyTexTalkNode
import mathlingua.lib.frontend.ast.EqualsExpression
import mathlingua.lib.frontend.ast.Expression
import mathlingua.lib.frontend.ast.ExpressionIsFormItem
import mathlingua.lib.frontend.ast.Function
import mathlingua.lib.frontend.ast.FunctionCall
import mathlingua.lib.frontend.ast.InExpression
import mathlingua.lib.frontend.ast.IsExpression
import mathlingua.lib.frontend.ast.MetaIsForm
import mathlingua.lib.frontend.ast.MetaIsFormItem
import mathlingua.lib.frontend.ast.Name
import mathlingua.lib.frontend.ast.NameOrCommand
import mathlingua.lib.frontend.ast.NameOrVariadicName
import mathlingua.lib.frontend.ast.NamedParameterExpression
import mathlingua.lib.frontend.ast.NamedParameterForm
import mathlingua.lib.frontend.ast.NotEqualsExpression
import mathlingua.lib.frontend.ast.NotInExpression
import mathlingua.lib.frontend.ast.SpecificationIsFormItem
import mathlingua.lib.frontend.ast.SquareParams
import mathlingua.lib.frontend.ast.StatementIsFormItem
import mathlingua.lib.frontend.ast.SubAndRegularParamCall
import mathlingua.lib.frontend.ast.SubParamCall
import mathlingua.lib.frontend.ast.TexTalkNode
import mathlingua.lib.frontend.ast.TexTalkToken
import mathlingua.lib.frontend.ast.TexTalkTokenType
import mathlingua.lib.frontend.ast.VariadicIsRhs
import mathlingua.lib.frontend.ast.VariadicName

internal interface TexTalkParser {
    fun parse(): TexTalkNode
    fun diagnostics(): List<Diagnostic>
}

internal fun newTexTalkParser(lexer: TexTalkLexer): TexTalkParser = TexTalkParserImpl(lexer)

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private sealed interface TreeNode {
    fun toString(indent: String): String
    fun row(): Int?
    fun column(): Int?
    fun startsWith(type: TexTalkTokenType): Boolean
}

private data class AtomTreeNode(var token: TexTalkToken) : TreeNode {
    override fun toString(indent: String) = "$indent${token.text}"
    override fun row() = token.row
    override fun column() = token.column
    override fun startsWith(type: TexTalkTokenType) = token.type == type
}

private data class ParenTreeNode(
    var prefix: TexTalkToken?, var content: TreeNode?, var suffix: TexTalkToken?
) : TreeNode {
    override fun toString(indent: String) =
        "<$indent${prefix?.text} ${content?.toString("")} ${suffix?.text}>"
    override fun row() = prefix?.row
    override fun column() = prefix?.column
    override fun startsWith(type: TexTalkTokenType) = prefix?.type == type
}

private data class ListTreeNode(val nodes: MutableList<TreeNode>) : TreeNode {
    override fun toString(indent: String) = "$indent${nodes.map { it.toString("") }}"
    override fun row() = nodes.firstOrNull()?.row()
    override fun column() = nodes.firstOrNull()?.column()
    override fun startsWith(type: TexTalkTokenType) = nodes.firstOrNull()?.startsWith(type) ?: false
}

private data class SplitTreeNode(var lhs: TreeNode?, var center: TreeNode?, var rhs: TreeNode?) :
    TreeNode {
    override fun toString(indent: String): String {
        val builder = StringBuilder()
        builder.append("\n")
        builder.append(center?.toString(indent))
        builder.append("\n")
        builder.append(lhs?.toString("$indent  "))
        builder.append("\n")
        builder.append("$indent  _____\n")
        builder.append(rhs?.toString("$indent  "))
        builder.append("\n")
        return builder.toString()
    }
    override fun row() = lhs?.row()
    override fun column() = lhs?.column()
    override fun startsWith(type: TexTalkTokenType) = lhs?.startsWith(type) ?: false
}

private fun listToTreeNode(nodes: MutableList<TreeNode>): TreeNode? =
    if (nodes.isEmpty()) {
        null
    } else if (nodes.size == 1) {
        nodes.first()
    } else {
        ListTreeNode(nodes = LinkedList(nodes))
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun TreeNode.splitByNodesMatching(splitTypes: Set<TexTalkTokenType>): TreeNode =
    when (this) {
        is AtomTreeNode -> this
        is ParenTreeNode -> {
            ParenTreeNode(
                prefix = this.prefix,
                content = this.content?.splitByNodesMatching(splitTypes),
                suffix = this.suffix)
        }
        is ListTreeNode -> {
            val index =
                this.nodes.indexOfFirst { it is AtomTreeNode && it.token.type in splitTypes }
            if (index == -1) {
                this
            } else {
                val nodeList = this.nodes.toMutableList()
                SplitTreeNode(
                        lhs =
                            listToTreeNode(nodeList.subList(0, index))
                                ?.splitByNodesMatching(splitTypes),
                        center = nodeList[index].splitByNodesMatching(splitTypes),
                        rhs = listToTreeNode(nodeList.subList(index + 1, this.nodes.size)))
                    .splitByNodesMatching(splitTypes)
            }
        }
        is SplitTreeNode -> {
            SplitTreeNode(
                lhs = this.lhs?.splitByNodesMatching(splitTypes),
                center = this.center?.splitByNodesMatching(splitTypes),
                rhs = this.rhs?.splitByNodesMatching(splitTypes))
        }
    }

private fun groupByParens(lexer: TexTalkLexer): TreeNode? {
    val nodes = mutableListOf<TreeNode>()
    while (lexer.hasNext() && !lexer.peek().type.isRightParenType()) {
        val next = lexer.next()
        if (next.type.isLeftParenType()) {
            val endType = next.type.getExpectedEndType()
            val content = groupByParens(lexer)
            val suffix =
                if (lexer.hasNext() && lexer.peek().type == endType) {
                    lexer.next()
                } else {
                    null
                }
            nodes.add(ParenTreeNode(prefix = next, content = content, suffix = suffix))
        } else {
            nodes.add(AtomTreeNode(token = next))
        }
    }
    return listToTreeNode(nodes)
}

private fun lexerToTree(lexer: TexTalkLexer) =
    groupByParens(lexer)
        ?.splitByNodesMatching(
            setOf(TexTalkTokenType.Is, TexTalkTokenType.In, TexTalkTokenType.NotIn))
        ?.splitByNodesMatching(setOf(TexTalkTokenType.Equals, TexTalkTokenType.NotEqual))
        ?.splitByNodesMatching(setOf(TexTalkTokenType.As))

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun TexTalkTokenType.isLeftParenType() =
    this == TexTalkTokenType.LParen ||
        this == TexTalkTokenType.LCurly ||
        this == TexTalkTokenType.LSquare ||
        this == TexTalkTokenType.LSquareColon

private fun TexTalkTokenType.isRightParenType() =
    this == TexTalkTokenType.RParen ||
        this == TexTalkTokenType.RCurly ||
        this == TexTalkTokenType.RSquare ||
        this == TexTalkTokenType.ColonRSquare

private fun TexTalkTokenType.getExpectedEndType() =
    if (this == TexTalkTokenType.LParen) {
        TexTalkTokenType.RParen
    } else if (this == TexTalkTokenType.LCurly) {
        TexTalkTokenType.RCurly
    } else if (this == TexTalkTokenType.LSquare) {
        TexTalkTokenType.RSquare
    } else if (this == TexTalkTokenType.LSquareColon) {
        TexTalkTokenType.ColonRSquare
    } else {
        null
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun <T> mutableListOfNotNull(vararg args: T?): MutableList<T> {
    val result = mutableListOf<T>()
    for (a in args) {
        if (a != null) {
            result.add(a)
        }
    }
    return result
}

private fun <T> emptyMutableList(): MutableList<T> = mutableListOf()

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun MutableList<TreeNode>.has(type: TexTalkTokenType) =
    this.isNotEmpty() && this.first().isAtomOfType(type)

private fun MutableList<TreeNode>.hasNameText(text: String) =
    this.has(TexTalkTokenType.Name) && (this.get(0) as AtomTreeNode).token.text == text

private fun MutableList<TreeNode>.hasHas(type1: TexTalkTokenType, type2: TexTalkTokenType) =
    this.size >= 2 &&
        this.elementAtOrNull(0)?.isAtomOfType(type1) == true &&
        this.elementAtOrNull(1)?.startsWith(type2) == true

private fun MutableList<TreeNode>.hasHasHas(
    type1: TexTalkTokenType, type2: TexTalkTokenType, type3: TexTalkTokenType
) =
    this.size >= 3 &&
        this.elementAtOrNull(0)?.isAtomOfType(type1) == true &&
        this.elementAtOrNull(1)?.isAtomOfType(type2) == true &&
        this.elementAtOrNull(2)?.startsWith(type3) == true

private fun MutableList<TreeNode>.pollToken() = (this.removeAt(0) as AtomTreeNode).token

private fun MutableList<TreeNode>.peekParenPrefix() =
    if (this.isNotEmpty() && this.first() is ParenTreeNode) {
        (this.first() as ParenTreeNode).prefix
    } else {
        null
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun TreeNode.isAtomOfType(type: TexTalkTokenType) =
    this is AtomTreeNode && this.token.type == type

/*
 * Takes a TreeNode and converts it to a list of TexTalkNodes.  There will be a list of more than one element
 * if the TreeNode is a ListTreeNode or ParenTreeNode with content of the form `..., ..., ...` in which case there
 * will be an element for each comma separated element that was successfully parsed.  There will be an empty list
 * if the TreeNode doesn't parse to a TexTalkNode or if it is a comma-separated list, none parse correctly.
 */
private fun TreeNode.treeNodeToTexTalkNodeList(
    diagnostics: MutableList<Diagnostic>
): MutableList<TexTalkNode> =
    when (this) {
        is AtomTreeNode -> {
            mutableListOfNotNull(this.atomTreeNodeToTexTalkNode(diagnostics))
        }
        is ParenTreeNode -> {
            when (this.content) {
                null -> {
                    emptyMutableList()
                }
                is ListTreeNode -> {
                    toTexTalkNodeList(
                        diagnostics,
                        this.content as ListTreeNode,
                        this.prefix?.row ?: -1,
                        this.prefix?.column ?: -1)
                }
                else -> {
                    this.content!!.treeNodeToTexTalkNodeList(diagnostics)
                }
            }
        }
        is ListTreeNode -> {
            toTexTalkNodeList(diagnostics, this, -1, -1)
        }
        is SplitTreeNode -> {
            mutableListOfNotNull(this.toTexTalkNode(diagnostics))
        }
    }

private fun toTexTalkNodeList(
    diagnostics: MutableList<Diagnostic>,
    treeNode: ListTreeNode,
    fallbackRow: Int,
    fallbackColumn: Int
): MutableList<TexTalkNode> {
    val nodes = treeNode.nodes
    val result = mutableListOf<TexTalkNode>()
    while (nodes.isNotEmpty()) {
        val subQueue = mutableListOf<TreeNode>()
        while (nodes.isNotEmpty() && !nodes.has(TexTalkTokenType.Comma)) {
            subQueue.add(nodes.removeAt(0))
        }
        val comma =
            if (nodes.has(TexTalkTokenType.Comma)) {
                (nodes.removeAt(0) as AtomTreeNode).token
            } else {
                null
            }
        val param = subQueue.parseIntoSingleNode(diagnostics)
        if (param == null) {
            diagnostics.add(
                error(
                    message = "Unexpected empty parameter",
                    row = comma?.row ?: fallbackRow,
                    column = comma?.column ?: fallbackColumn))
        } else {
            result.add(param)
        }
    }
    return result
}

private fun MutableList<TreeNode>.parseIntoSingleNode(
    diagnostics: MutableList<Diagnostic>
): TexTalkNode? {
    // process the list one by one and then run shunting-yard on the result
    // this is where a list of tokens is constructed into a list of parsed nodes that only need to
    // be processed
    // into a tree based on operator usage using the shunting-yard algorithm
    val nodesToProcess = mutableListOf<TexTalkNode>()
    while (this.isNotEmpty()) {
        val node =
            this.commandExpression(diagnostics)
                ?: this.variadicName(diagnostics) ?: this.name() ?: break
        nodesToProcess.add(node)
    }
    // TODO: FINISH THIS
    return shuntingYard(diagnostics, nodesToProcess)
}

private fun shuntingYard(
    diagnostics: MutableList<Diagnostic>, nodes: MutableList<TexTalkNode>
): TexTalkNode? {
    // TODO: FINISH THIS
    return nodes.firstOrNull()
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/*
 * Given a list of TexTalkNodes returns the first node, and for any subsequent nodes reports a diagnostic that they
 * are not expected.  If the list is empty `null` is returned.  Calling code is responsible for recording a diagnostic
 * if required.
 */
private fun MutableList<TexTalkNode>.pollFirstAndError(
    diagnostics: MutableList<Diagnostic>
): TexTalkNode? =
    if (this.isEmpty()) {
        null
    } else {
        for (i in 1 until this.size) {
            diagnostics.add(
                error(
                    message = "Unexpected item",
                    row = this[i].metadata.row,
                    column = this[i].metadata.column))
        }
        this.first()
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun SplitTreeNode.toTexTalkNode(diagnostics: MutableList<Diagnostic>): TexTalkNode? =
    this.toIsExpression(diagnostics)
        ?: this.toInExpression(diagnostics) ?: this.toNotInExpression(diagnostics)
            ?: this.toAsExpression(diagnostics) ?: this.toEqualsExpression(diagnostics)
            ?: this.toNotEqualsExpression(diagnostics)

private fun SplitTreeNode.toIsExpression(diagnostics: MutableList<Diagnostic>): IsExpression? {
    return null
}

private fun SplitTreeNode.toInExpression(diagnostics: MutableList<Diagnostic>): InExpression? {
    return null
}

private fun SplitTreeNode.toNotInExpression(
    diagnostics: MutableList<Diagnostic>
): NotInExpression? {
    return null
}

private fun SplitTreeNode.toAsExpression(diagnostics: MutableList<Diagnostic>): AsExpression? {
    return null
}

private fun SplitTreeNode.toEqualsExpression(
    diagnostics: MutableList<Diagnostic>
): EqualsExpression? {
    return null
}

private fun SplitTreeNode.toNotEqualsExpression(
    diagnostics: MutableList<Diagnostic>
): NotEqualsExpression? {
    return null
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun AtomTreeNode.atomTreeNodeToTexTalkNode(
    diagnostics: MutableList<Diagnostic>
): TexTalkNode? {
    return if (this.token.type == TexTalkTokenType.Name) {
        Name(
            text = this.token.text,
            metadata = MetaData(row = this.token.row, column = this.token.column, isInline = null))
    } else {
        diagnostics.add(
            error(message = "Unexpected token", row = this.token.row, column = this.token.column))
        null
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private inline fun <reified T> List<TexTalkNode>.filterAndError(
    diagnostics: MutableList<Diagnostic>
): MutableList<T> {
    val result = mutableListOf<T>()
    for (item in this) {
        if (item is T) {
            result.add(item)
        } else {
            diagnostics.add(
                error(
                    message = "Expected a ${T::class.java.simpleName}",
                    row = item.metadata.row,
                    column = item.metadata.column))
        }
    }
    return result
}

private fun error(message: String, row: Int, column: Int) =
    Diagnostic(
        type = DiagnosticType.Error,
        origin = DiagnosticOrigin.ChalkTalkParser,
        message = message,
        row = row,
        column = column)

private inline fun <reified T> T?.orError(
    diagnostics: MutableList<Diagnostic>, message: String, row: Int, column: Int, default: T
): T =
    if (this != null) {
        this
    } else {
        diagnostics.add(error(message, row, column))
        default
    }

private inline fun <reified T> MutableList<T>?.errorIfNullOrEmpty(
    diagnostics: MutableList<Diagnostic>, row: Int, column: Int
): MutableList<T>? =
    if (this == null || this.isEmpty()) {
        diagnostics.add(
            error(
                message = "Expected at least one ${T::class.java.simpleName}",
                row = row,
                column = column))
        this
    } else {
        this
    }

private fun MutableList<TreeNode>.expect(
    diagnostics: MutableList<Diagnostic>, type: TexTalkTokenType
): TexTalkToken? =
    if (this.has(type)) {
        this.pollToken()
    } else {
        val peek = this.firstOrNull()
        diagnostics.add(
            error(
                message = "Expected a ${type.name}",
                row = peek?.row() ?: -1,
                column = peek?.column() ?: -1))
        null
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun MutableList<TreeNode>.token(type: TexTalkTokenType): TexTalkToken? =
    if (this.has(type)) {
        this.pollToken()
    } else {
        null
    }

private fun MutableList<TreeNode>.name(): Name? =
    if (this.has(TexTalkTokenType.Name)) {
        val token = this.pollToken()
        Name(
            text = token.text,
            metadata = MetaData(row = token.row, column = token.column, isInline = null))
    } else {
        null
    }

private fun MutableList<TreeNode>.variadicName(
    diagnostics: MutableList<Diagnostic>
): VariadicName? =
    if (this.hasHas(TexTalkTokenType.Name, TexTalkTokenType.DotDotDot)) {
        val name = name()!!
        this.expect(diagnostics, TexTalkTokenType.DotDotDot)
        this.pollToken() // move past the ...
        VariadicName(name = name, metadata = name.metadata.copy())
    } else {
        null
    }

private fun MutableList<TreeNode>.nameOrVariadicName(
    diagnostics: MutableList<Diagnostic>
): NameOrVariadicName? = this.name() ?: this.variadicName(diagnostics)

private fun MutableList<TreeNode>.function(diagnostics: MutableList<Diagnostic>): Function? =
    if (this.hasHas(TexTalkTokenType.Name, TexTalkTokenType.LParen)) {
        val name = name()!!
        Function(
            name = name,
            params =
                this.parenNameOrVariadicNameParameterList(diagnostics)
                    ?.errorIfNullOrEmpty(
                        diagnostics = diagnostics,
                        row = name.metadata.row,
                        column = name.metadata.column)
                    ?: emptyList(),
            metadata = name.metadata.copy())
    } else {
        null
    }

private fun MutableList<TreeNode>.subParamCallOrSubAndRegularParamCall(
    diagnostics: MutableList<Diagnostic>
): FunctionCall? =
    if (this.hasHasHas(
        TexTalkTokenType.Name, TexTalkTokenType.Underscore, TexTalkTokenType.LParen)) {
        val name = name()!!
        this.expect(diagnostics, TexTalkTokenType.Underscore)
        val subParams =
            this.parenNameOrVariadicNameParameterList(diagnostics)
                ?.errorIfNullOrEmpty(
                    diagnostics = diagnostics,
                    row = name.metadata.row,
                    column = name.metadata.column)
                ?: emptyList()
        val params = this.parenNameOrVariadicNameParameterList(diagnostics)
        if (params != null) {
            SubAndRegularParamCall(
                name = name,
                subParams = subParams,
                params = params,
                metadata = name.metadata.copy())
        } else {
            SubParamCall(name = name, subParams = subParams, metadata = name.metadata.copy())
        }
    } else {
        null
    }

private fun MutableList<TreeNode>.functionCall(
    diagnostics: MutableList<Diagnostic>
): FunctionCall? =
    this.function(diagnostics) ?: this.subParamCallOrSubAndRegularParamCall(diagnostics)

private fun MutableList<TreeNode>.squareParams(
    diagnostics: MutableList<Diagnostic>
): SquareParams? {
    val peek = this.firstOrNull()
    val row = peek?.row() ?: -1
    val column = peek?.column() ?: -1

    val nodes = this.squareNodeList(diagnostics) ?: return null
    if (nodes.isEmpty()) {
        diagnostics.add(
            error(message = "Expected at least one parameter", row = row, column = column))
        return SquareParams(emptyList())
    }

    return if (nodes.first() is VariadicName) {
        for (i in 1 until nodes.size) {
            diagnostics.add(
                error(
                    message = "At most one VariadicName can be specified in a [...]",
                    row = nodes[i].metadata.row,
                    column = nodes[i].metadata.column))
        }
        SquareParams(nodes.first() as VariadicName)
    } else {
        SquareParams(nodes.filterAndError(diagnostics))
    }
}

private fun MutableList<TreeNode>.namedParameterExpression(
    diagnostics: MutableList<Diagnostic>
): NamedParameterExpression? {
    if (!this.hasHas(TexTalkTokenType.Colon, TexTalkTokenType.Name)) {
        return null
    }
    val colon = this.token(TexTalkTokenType.Colon) ?: return null
    val name =
        this.name().orError(diagnostics, "Expected a Name", colon.row, colon.column, DEFAULT_NAME)
    val curlyExpression =
        this.curlyExpressionParameterList(diagnostics)
            ?.orError(
                diagnostics = diagnostics,
                message = "Expected a {...}",
                row = colon.row,
                column = colon.column,
                default = emptyList())
            ?: emptyList()
    return NamedParameterExpression(
        name = name,
        params = curlyExpression,
        metadata = MetaData(row = colon.row, column = colon.column, isInline = null))
}

private fun MutableList<TreeNode>.namedParameterForm(
    diagnostics: MutableList<Diagnostic>
): NamedParameterForm? {
    if (!this.hasHas(TexTalkTokenType.Colon, TexTalkTokenType.Name)) {
        return null
    }
    val colon = this.token(TexTalkTokenType.Colon) ?: return null
    val name =
        this.name().orError(diagnostics, "Expected a Name", colon.row, colon.column, DEFAULT_NAME)
    val params =
        this.curlyNameOrVariadicNameParameterList(diagnostics)
            ?.orError(
                diagnostics = diagnostics,
                message = "Expected a {...}",
                row = colon.row,
                column = colon.column,
                default = emptyList())
            ?: emptyList()
    return NamedParameterForm(
        name = name,
        params = params,
        metadata = MetaData(row = colon.row, column = colon.column, isInline = null))
}

private fun MutableList<TreeNode>.subExpressionParams(
    diagnostics: MutableList<Diagnostic>
): List<Expression>? =
    if (this.hasHas(TexTalkTokenType.Underscore, TexTalkTokenType.LCurly)) {
        val underscore = this.expect(diagnostics, TexTalkTokenType.Underscore)!!
        this.curlyExpressionParameterList(diagnostics)
            ?.errorIfNullOrEmpty(
                diagnostics = diagnostics, row = underscore.row, column = underscore.column)
            ?: emptyList()
    } else {
        null
    }

private fun MutableList<TreeNode>.subNameOrVariadicNameParams(
    diagnostics: MutableList<Diagnostic>
): List<NameOrVariadicName>? =
    if (this.hasHas(TexTalkTokenType.Underscore, TexTalkTokenType.LCurly)) {
        val underscore = this.expect(diagnostics, TexTalkTokenType.Underscore)!!
        this.curlyNameOrVariadicNameParameterList(diagnostics)
            ?.errorIfNullOrEmpty(
                diagnostics = diagnostics, row = underscore.row, column = underscore.column)
            ?: emptyList()
    } else {
        null
    }

private fun MutableList<TreeNode>.supExpressionParams(
    diagnostics: MutableList<Diagnostic>
): List<Expression>? =
    if (this.hasHas(TexTalkTokenType.Caret, TexTalkTokenType.LCurly)) {
        val caret = this.expect(diagnostics, TexTalkTokenType.Caret)!!
        this.curlyExpressionParameterList(diagnostics)
            ?.errorIfNullOrEmpty(diagnostics = diagnostics, row = caret.row, column = caret.column)
            ?: emptyList()
    } else {
        null
    }

private fun MutableList<TreeNode>.supNameOrVariadicNameParams(
    diagnostics: MutableList<Diagnostic>
): List<NameOrVariadicName>? =
    if (this.hasHas(TexTalkTokenType.Caret, TexTalkTokenType.LCurly)) {
        val caret = this.expect(diagnostics, TexTalkTokenType.Caret)!!
        this.curlyNameOrVariadicNameParameterList(diagnostics)
            ?.errorIfNullOrEmpty(diagnostics = diagnostics, row = caret.row, column = caret.column)
            ?: emptyList()
    } else {
        null
    }

private fun MutableList<TreeNode>.commandExpression(
    diagnostics: MutableList<Diagnostic>
): CommandExpression? =
    if (this.has(TexTalkTokenType.Backslash)) {
        val backslash = this.expect(diagnostics, TexTalkTokenType.Backslash)!!
        val names = mutableListOf<Name>()
        while (this.isNotEmpty()) {
            val name = name() ?: break
            names.add(name)
            if (this.has(TexTalkTokenType.Dot)) {
                this.expect(diagnostics, TexTalkTokenType.Dot)
            } else {
                break
            }
        }
        if (names.isEmpty()) {
            diagnostics.add(
                error(
                    message = "Expected at least one Name",
                    row = backslash.row,
                    column = backslash.column))
        }
        val squareParams = this.squareParams(diagnostics)
        val subParams = this.subExpressionParams(diagnostics)
        val supParams = this.supExpressionParams(diagnostics)
        val curlyParams = this.curlyExpressionParameterList(diagnostics)
        val namedParams = mutableListOf<NamedParameterExpression>()
        while (this.isNotEmpty()) {
            val namedParam = this.namedParameterExpression(diagnostics) ?: break
            namedParams.add(namedParam)
        }
        val parenParams = this.parenExpressionParameterList(diagnostics)
        CommandExpression(
            names = names,
            squareParams = squareParams,
            subParams = subParams,
            supParams = supParams,
            curlyParams = curlyParams,
            namedParams = namedParams,
            parenParams = parenParams,
            metadata = MetaData(row = backslash.row, column = backslash.column, isInline = null))
    } else {
        null
    }

private fun MutableList<TreeNode>.commandForm(diagnostics: MutableList<Diagnostic>): CommandForm? =
    if (this.has(TexTalkTokenType.Backslash)) {
        val backslash = this.expect(diagnostics, TexTalkTokenType.Backslash)!!
        val names = mutableListOf<Name>()
        while (this.isNotEmpty()) {
            val name = name() ?: break
            names.add(name)
            if (this.has(TexTalkTokenType.Dot)) {
                this.expect(diagnostics, TexTalkTokenType.Dot)
            } else {
                break
            }
        }
        if (names.isEmpty()) {
            diagnostics.add(
                error(
                    message = "Expected at least one Name",
                    row = backslash.row,
                    column = backslash.column))
        }
        val squareParams = this.squareParams(diagnostics)
        val subParams = this.subNameOrVariadicNameParams(diagnostics)
        val supParams = this.supNameOrVariadicNameParams(diagnostics)
        val curlyParams = this.curlyNameOrVariadicNameParameterList(diagnostics)
        val namedParams = mutableListOf<NamedParameterForm>()
        while (this.isNotEmpty()) {
            val namedParam = this.namedParameterForm(diagnostics) ?: break
            namedParams.add(namedParam)
        }
        val parenParams = this.parenNameOrVariadicNameParameterList(diagnostics)
        CommandForm(
            names = names,
            squareParams = squareParams,
            subParams = subParams,
            supParams = supParams,
            curlyParams = curlyParams,
            namedParams = namedParams,
            parenParams = parenParams,
            metadata = MetaData(row = backslash.row, column = backslash.column, isInline = null))
    } else {
        null
    }

private fun MutableList<TreeNode>.nameOrCommand(
    diagnostics: MutableList<Diagnostic>
): NameOrCommand? = this.name() ?: this.commandExpression(diagnostics)

// returns `null` if the next node in the list is not a ParenTreeNode
private fun MutableList<TreeNode>.bracketedNodeList(
    diagnostics: MutableList<Diagnostic>, bracketType: TexTalkTokenType
): List<TexTalkNode>? {
    if (this.isEmpty() || this.first() !is ParenTreeNode) {
        return null
    }
    val peek = this.first() as ParenTreeNode
    if (peek.prefix?.type != bracketType) {
        return null
    }
    if (peek.suffix == null) {
        diagnostics.add(
            error(
                message = "Expected a closing bracket",
                row = peek.prefix!!.row,
                column = peek.prefix!!.column))
    }
    val parenTreeNode = this.removeAt(0) as ParenTreeNode
    return parenTreeNode.treeNodeToTexTalkNodeList(diagnostics)
}

private fun MutableList<TreeNode>.parenNodeList(diagnostics: MutableList<Diagnostic>) =
    bracketedNodeList(diagnostics, TexTalkTokenType.LParen)

private fun MutableList<TreeNode>.squareNodeList(diagnostics: MutableList<Diagnostic>) =
    bracketedNodeList(diagnostics, TexTalkTokenType.LSquare)

private fun MutableList<TreeNode>.curlyNodeList(diagnostics: MutableList<Diagnostic>) =
    bracketedNodeList(diagnostics, TexTalkTokenType.LCurly)

private fun MutableList<TreeNode>.squareColonNodeList(diagnostics: MutableList<Diagnostic>) =
    bracketedNodeList(diagnostics, TexTalkTokenType.LSquareColon)

private fun MutableList<TreeNode>.parenExpressionParameterList(
    diagnostics: MutableList<Diagnostic>
): MutableList<Expression>? = this.parenNodeList(diagnostics)?.filterAndError(diagnostics)

private fun MutableList<TreeNode>.squareExpressionParameterList(
    diagnostics: MutableList<Diagnostic>
): MutableList<Expression>? = this.squareNodeList(diagnostics)?.filterAndError(diagnostics)

private fun MutableList<TreeNode>.curlyExpressionParameterList(
    diagnostics: MutableList<Diagnostic>
): MutableList<Expression>? = this.curlyNodeList(diagnostics)?.filterAndError(diagnostics)

private fun MutableList<TreeNode>.squareColonExpressionParameterList(
    diagnostics: MutableList<Diagnostic>
): MutableList<Expression>? = this.squareColonNodeList(diagnostics)?.filterAndError(diagnostics)

private fun MutableList<TreeNode>.parenNameOrVariadicNameParameterList(
    diagnostics: MutableList<Diagnostic>
): MutableList<NameOrVariadicName>? = this.parenNodeList(diagnostics)?.filterAndError(diagnostics)

private fun MutableList<TreeNode>.squareNameOrVariadicNameParameterList(
    diagnostics: MutableList<Diagnostic>
): MutableList<NameOrVariadicName>? = this.squareNodeList(diagnostics)?.filterAndError(diagnostics)

private fun MutableList<TreeNode>.curlyNameOrVariadicNameParameterList(
    diagnostics: MutableList<Diagnostic>
): MutableList<NameOrVariadicName>? = this.curlyNodeList(diagnostics)?.filterAndError(diagnostics)

private fun MutableList<TreeNode>.squareColonMetaIsFormItemParameterList(
    diagnostics: MutableList<Diagnostic>
): MutableList<MetaIsFormItem>? = this.squareColonNodeList(diagnostics)?.filterAndError(diagnostics)

private fun MutableList<TreeNode>.variadicIsRhs(
    diagnostics: MutableList<Diagnostic>
): VariadicIsRhs? = this.variadicName(diagnostics) ?: this.commandExpression(diagnostics)

private fun MutableList<TreeNode>.statementIsFormItem(): StatementIsFormItem? =
    if (this.hasNameText("statement")) {
        val next = pollToken()
        StatementIsFormItem(
            metadata = MetaData(row = next.row, column = next.column, isInline = null))
    } else {
        null
    }

private fun MutableList<TreeNode>.assignmentIsFormItem(): AssignmentIsFormItem? =
    if (this.hasNameText("assignment")) {
        val next = pollToken()
        AssignmentIsFormItem(
            metadata = MetaData(row = next.row, column = next.column, isInline = null))
    } else {
        null
    }

private fun MutableList<TreeNode>.specificationIsFormItem(): SpecificationIsFormItem? =
    if (this.hasNameText("specification")) {
        val next = pollToken()
        SpecificationIsFormItem(
            metadata = MetaData(row = next.row, column = next.column, isInline = null))
    } else {
        null
    }

private fun MutableList<TreeNode>.expressionIsFormItem(): ExpressionIsFormItem? =
    if (this.hasNameText("expression")) {
        val next = pollToken()
        ExpressionIsFormItem(
            metadata = MetaData(row = next.row, column = next.column, isInline = null))
    } else {
        null
    }

private fun MutableList<TreeNode>.definitionIsFormItem(): DefinitionIsFormItem? =
    if (this.hasNameText("definition")) {
        val next = pollToken()
        DefinitionIsFormItem(
            metadata = MetaData(row = next.row, column = next.column, isInline = null))
    } else {
        null
    }

private fun MutableList<TreeNode>.metaIsFormItem(): MetaIsFormItem? =
    this.statementIsFormItem()
        ?: this.assignmentIsFormItem() ?: this.specificationIsFormItem()
            ?: this.expressionIsFormItem() ?: this.definitionIsFormItem()

private fun MutableList<TreeNode>.metaIsForm(diagnostics: MutableList<Diagnostic>): MetaIsForm? =
    if (this.has(TexTalkTokenType.LSquareColon)) {
        val prefix = this.peekParenPrefix()
        MetaIsForm(
            items = this.squareColonMetaIsFormItemParameterList(diagnostics) ?: emptyList(),
            metadata =
                MetaData(row = prefix?.row ?: -1, column = prefix?.column ?: -1, isInline = null))
    } else {
        null
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private data class TexTalkParserImpl(private val lexer: TexTalkLexer) : TexTalkParser {
    private val diagnostics = mutableListOf<Diagnostic>()

    override fun parse() =
        lexerToTree(lexer)?.treeNodeToTexTalkNodeList(diagnostics)?.pollFirstAndError(diagnostics)
            ?: EmptyTexTalkNode

    override fun diagnostics() = diagnostics
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun main() {
    val text = """
        \abc{x, y}
    """.trimIndent()
    println(newTexTalkParser(newTexTalkLexer(text)).parse())
}
