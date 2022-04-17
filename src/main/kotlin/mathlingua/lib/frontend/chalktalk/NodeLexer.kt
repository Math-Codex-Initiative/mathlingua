package mathlingua.lib.frontend.chalktalk

import mathlingua.lib.frontend.Diagnostic
import mathlingua.lib.frontend.DiagnosticType
import mathlingua.lib.frontend.MetaData
import mathlingua.lib.frontend.ast.Argument
import mathlingua.lib.frontend.ast.BeginArgument
import mathlingua.lib.frontend.ast.BeginGroup
import mathlingua.lib.frontend.ast.BeginSection
import mathlingua.lib.frontend.ast.ChalkTalkNode
import mathlingua.lib.frontend.ast.EndArgument
import mathlingua.lib.frontend.ast.EndGroup
import mathlingua.lib.frontend.ast.EndSection
import mathlingua.lib.frontend.ast.Function
import mathlingua.lib.frontend.ast.FunctionAssignment
import mathlingua.lib.frontend.ast.Id
import mathlingua.lib.frontend.ast.Name
import mathlingua.lib.frontend.ast.NameAssignment
import mathlingua.lib.frontend.ast.NameAssignmentItem
import mathlingua.lib.frontend.ast.NameOrNameAssignment
import mathlingua.lib.frontend.ast.NameParam
import mathlingua.lib.frontend.ast.NodeLexerToken
import mathlingua.lib.frontend.ast.OperatorName
import mathlingua.lib.frontend.ast.RegularFunction
import mathlingua.lib.frontend.ast.Set
import mathlingua.lib.frontend.ast.Statement
import mathlingua.lib.frontend.ast.SubAndRegularParamFunction
import mathlingua.lib.frontend.ast.SubAndRegularParamFunctionSequence
import mathlingua.lib.frontend.ast.SubParamFunction
import mathlingua.lib.frontend.ast.SubParamFunctionSequence
import mathlingua.lib.frontend.ast.Target
import mathlingua.lib.frontend.ast.Text
import mathlingua.lib.frontend.ast.TextBlock
import mathlingua.lib.frontend.ast.Tuple

internal interface NodeLexer {
    fun hasNext(): Boolean
    fun peek(): NodeLexerToken
    fun next(): NodeLexerToken

    fun hasNextNext(): Boolean
    fun peekPeek(): NodeLexerToken
    fun nextNext(): NodeLexerToken

    fun diagnostics(): List<Diagnostic>
}

internal fun newNodeLexer(lexer: TokenLexer): NodeLexer {
    return NodeLexerImpl(lexer)
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private class NodeLexerImpl(private val lexer: TokenLexer) : NodeLexer {
    private val tokens = mutableListOf<NodeLexerToken>()
    private var index = 0
    private val diagnostics = mutableListOf<Diagnostic>()

    init {
        while (lexer.hasNext()) {
            val peek = lexer.peek()
            processTopLevelItem()
            if (lexer.hasNext() && lexer.next() === peek) {
                throw Exception("NodeLexer caught in an infinite loop")
            }
        }
    }

    override fun hasNext() = index < tokens.size

    override fun peek() = tokens[index]

    override fun next() = tokens[index++]

    override fun hasNextNext() = index + 1 < tokens.size

    override fun peekPeek() = tokens[index + 1]

    override fun nextNext(): NodeLexerToken {
        val result = peekPeek()
        index += 2
        return result
    }

    override fun diagnostics(): List<Diagnostic> = diagnostics

    private fun expect(type: TokenType): Token? {
        if (!lexer.hasNext()) {
            diagnostics.add(
                Diagnostic(
                    type = DiagnosticType.Error,
                    message = "Expected a $type token but found the end of text",
                    row = -1,
                    column = -1))
            return null
        }
        val next = lexer.next()
        if (next.type != type) {
            diagnostics.add(
                Diagnostic(
                    type = DiagnosticType.Error,
                    message = "Expected a $type but found ${next.type}",
                    row = next.row,
                    column = next.column))
        }
        return next
    }

    private fun statement(isInline: Boolean): Statement? {
        if (!lexer.has(TokenType.Statement)) {
            return null
        }
        val next = lexer.next()
        return Statement(
            text = next.text,
            metadata = MetaData(row = next.row, column = next.column, isInline = isInline))
    }

    private fun text(isInline: Boolean): Text? {
        if (!lexer.has(TokenType.Text)) {
            return null
        }
        val next = lexer.next()
        return Text(
            text = next.text,
            metadata = MetaData(row = next.row, column = next.column, isInline = isInline))
    }

    private fun argument(isInline: Boolean): Argument? {
        val statement = statement(isInline)
        if (statement != null) {
            return statement
        }

        val text = text(isInline)
        if (text != null) {
            return text
        }

        return target(isInline)
    }

    private fun name(isInline: Boolean): Name? {
        if (!lexer.has(TokenType.Name)) {
            return null
        }
        val next = lexer.next()
        return Name(
            text = next.text,
            metadata = MetaData(row = next.row, column = next.column, isInline = isInline))
    }

    private fun nameParam(isInline: Boolean): NameParam? {
        val name =
            name(isInline)
                ?: if (lexer.has(TokenType.Underscore)) {
                    val underscore = lexer.next()
                    Name(
                        text = underscore.text,
                        metadata =
                            MetaData(
                                row = underscore.row,
                                column = underscore.column,
                                isInline = isInline))
                } else {
                    null
                }
                    ?: return null
        val hasDotDotDot =
            if (lexer.has(TokenType.DotDotDot)) {
                expect(TokenType.DotDotDot)
                true
            } else {
                false
            }
        return NameParam(name = name, isVarArgs = hasDotDotDot)
    }

    private fun nameParamList(isInline: Boolean, expectedEnd: TokenType): List<NameParam> {
        val result = mutableListOf<NameParam>()
        while (lexer.hasNext() && !lexer.has(expectedEnd)) {
            val nameParam = nameParam(isInline) ?: break
            result.add(nameParam)
            if (!lexer.has(expectedEnd)) {
                expect(TokenType.Comma)
            }
        }
        while (lexer.hasNext() && lexer.peek().type != expectedEnd) {
            val next = lexer.next()
            diagnostics.add(
                Diagnostic(
                    type = DiagnosticType.Error,
                    message = "Unexpected token ${next.text}",
                    row = next.row,
                    column = next.column))
        }
        return result
    }

    private fun subParams(isInline: Boolean): List<NameParam>? {
        if (!lexer.hasHas(TokenType.Underscore, TokenType.LCurly)) {
            return null
        }
        expect(TokenType.Underscore)
        expect(TokenType.LCurly)
        val result = nameParamList(isInline, TokenType.RCurly)
        expect(TokenType.RCurly)
        return result
    }

    private fun regularParams(isInline: Boolean): List<NameParam>? {
        if (!lexer.has(TokenType.LParen)) {
            return null
        }
        expect(TokenType.LParen)
        val result = nameParamList(isInline, TokenType.RParen)
        expect(TokenType.RParen)
        return result
    }

    private fun operator(isInline: Boolean): OperatorName? {
        if (!lexer.has(TokenType.Operator)) {
            return null
        }
        val next = lexer.next()
        return OperatorName(
            text = next.text,
            metadata = MetaData(row = next.row, column = next.column, isInline = isInline))
    }

    private fun function(isInline: Boolean): Function? {
        // to be a function, the next tokens must be either `<name> "("` or `<name> "_"`
        if (!lexer.hasHas(TokenType.Name, TokenType.LParen) &&
            !lexer.hasHas(TokenType.Name, TokenType.Underscore)) {
            return null
        }

        val name = name(isInline)!!
        val subParams = subParams(isInline)
        val regularParams = regularParams(isInline)
        return if (subParams != null && regularParams != null) {
            SubAndRegularParamFunction(
                name = name,
                subParams = subParams,
                params = regularParams,
                metadata =
                    MetaData(
                        row = name.metadata.row,
                        column = name.metadata.column,
                        isInline = name.metadata.isInline))
        } else if (subParams != null && regularParams == null) {
            SubParamFunction(
                name = name,
                subParams = subParams,
                metadata =
                    MetaData(
                        row = name.metadata.row,
                        column = name.metadata.column,
                        isInline = name.metadata.isInline))
        } else if (subParams == null && regularParams != null) {
            RegularFunction(
                name = name,
                params = regularParams,
                metadata =
                    MetaData(
                        row = name.metadata.row,
                        column = name.metadata.column,
                        isInline = name.metadata.isInline))
        } else {
            diagnostics.add(
                Diagnostic(
                    type = DiagnosticType.Error,
                    message = "Expected a function",
                    row = name.metadata.row,
                    column = name.metadata.column))
            null
        }
    }

    private fun nameOrAssignmentItem(isInline: Boolean): NameAssignmentItem? {
        val func = function(isInline)
        if (func != null) {
            return func
        }

        val name = name(isInline)
        if (name != null) {
            return name
        }

        val op = operator(isInline)
        if (op != null) {
            return op
        }

        val tuple = tuple(isInline)
        if (tuple != null) {
            return tuple
        }

        val setOrSequence = setOrSequence(isInline)
        if (setOrSequence != null) {
            return setOrSequence as NameAssignmentItem
        }

        val nextText =
            if (lexer.hasNext()) {
                lexer.next().text
            } else {
                "end of text"
            }

        diagnostics.add(
            Diagnostic(
                type = DiagnosticType.Error,
                message = "Expected a name assignment item but found $nextText",
                row = -1,
                column = -1))

        return null
    }

    private fun target(isInline: Boolean): Target? {
        val func = function(isInline)
        if (func != null) {
            return if (lexer.has(TokenType.ColonEqual)) {
                expect(TokenType.ColonEqual)
                val rhs = function(isInline)
                if (rhs == null) {
                    diagnostics.add(
                        Diagnostic(
                            type = DiagnosticType.Error,
                            message =
                                "The right hand side of a := must be a function if the left hand side is a function",
                            row = func.metadata.row,
                            column = func.metadata.column))
                    null
                } else {
                    FunctionAssignment(
                        lhs = func,
                        rhs = rhs,
                        metadata =
                            MetaData(
                                row = func.metadata.row,
                                column = func.metadata.column,
                                isInline = isInline))
                }
            } else {
                func
            }
        }

        val name = name(isInline)
        if (name != null) {
            return if (lexer.has(TokenType.ColonEqual)) {
                expect(TokenType.ColonEqual)
                val rhs = nameOrAssignmentItem(isInline)
                if (rhs == null) {
                    null
                } else {
                    NameAssignment(
                        lhs = name,
                        rhs = rhs,
                        metadata =
                            MetaData(
                                row = name.metadata.row,
                                column = name.metadata.column,
                                isInline = isInline))
                }
            } else {
                name
            }
        }

        return nameOrAssignmentItem(isInline) as Target?
    }

    private fun targets(isInline: Boolean, expectedEnd: TokenType): List<Target> {
        val targets = mutableListOf<Target>()
        while (lexer.hasNext() && !lexer.has(expectedEnd)) {
            val target = target(isInline) ?: break
            targets.add(target)
            if (!lexer.has(expectedEnd)) {
                expect(TokenType.Comma)
            }
        }
        while (lexer.hasNext() && !lexer.has(expectedEnd)) {
            val next = lexer.next()
            diagnostics.add(
                Diagnostic(
                    type = DiagnosticType.Error,
                    message = "Expected a target",
                    row = next.row,
                    column = next.column))
        }
        return targets
    }

    private fun tuple(isInline: Boolean): Tuple? {
        if (!lexer.has(TokenType.LParen)) {
            return null
        }
        val lParen = expect(TokenType.LParen)!!
        val targets = targets(isInline, TokenType.RParen)
        expect(TokenType.RParen)
        return Tuple(
            targets = targets,
            metadata = MetaData(row = lParen.row, column = lParen.column, isInline = isInline))
    }

    private fun setOrSequence(isInline: Boolean): ChalkTalkNode? {
        if (!lexer.has(TokenType.LCurly)) {
            return null
        }
        val lCurly = expect(TokenType.LCurly)!!
        val targets = targets(isInline, TokenType.RCurly)
        expect(TokenType.RCurly)
        val subParams = subParams(isInline)
        return if (subParams != null) {
            // it is a sequence
            if (targets.isEmpty()) {
                diagnostics.add(
                    Diagnostic(
                        type = DiagnosticType.Error,
                        message = "Expected a function with sub params",
                        row = lCurly.row,
                        column = lCurly.column))
                null
            } else if (targets.size != 1) {
                diagnostics.add(
                    Diagnostic(
                        type = DiagnosticType.Error,
                        message =
                            "Expected exactly one function with sub params but found ${targets.size}",
                        row = lCurly.row,
                        column = lCurly.column))
                null
            } else {
                when (val first = targets.first()
                ) {
                    is SubParamFunction -> {
                        SubParamFunctionSequence(
                            func = first,
                            metadata =
                                MetaData(
                                    row = lCurly.row, column = lCurly.column, isInline = isInline))
                    }
                    is SubAndRegularParamFunction -> {
                        SubAndRegularParamFunctionSequence(
                            func = first,
                            metadata =
                                MetaData(
                                    row = lCurly.row, column = lCurly.column, isInline = isInline))
                    }
                    else -> {
                        diagnostics.add(
                            Diagnostic(
                                type = DiagnosticType.Error,
                                message = "The given function must have sub params",
                                row = lCurly.row,
                                column = lCurly.column))
                        null
                    }
                }
            }
        } else {
            // it is a set
            for (t in targets) {
                if (t !is NameOrNameAssignment) {
                    diagnostics.add(
                        Diagnostic(
                            type = DiagnosticType.Error,
                            message = "Expected a name or name assignment",
                            row = t.metadata.row,
                            column = t.metadata.column))
                }
            }
            Set(
                items = targets.filterIsInstance<NameOrNameAssignment>(),
                metadata = MetaData(row = lCurly.row, column = lCurly.column, isInline = isInline))
        }
    }

    private fun processTopLevelItem() {
        if (!lexer.hasNext()) {
            return
        }

        while (lexer.has(TokenType.TextBlock)) {
            val next = lexer.next()
            tokens.add(
                TextBlock(
                    text = next.text,
                    metadata = MetaData(row = next.row, column = next.column, isInline = false)))
            return
        }

        processGroup()
    }

    private fun processGroup() {
        while (lexer.has(TokenType.LineBreak)) {
            lexer.next()
        }
        tokens.add(BeginGroup)
        if (lexer.has(TokenType.Id)) {
            val id = lexer.next()
            tokens.add(
                Id(
                    text = id.text,
                    metadata = MetaData(row = id.row, column = id.column, isInline = false)))
            expect(TokenType.Newline)
        }
        while (lexer.hasNext()) {
            if (!lexer.hasHas(TokenType.Name, TokenType.Colon)) {
                break
            }
            processSection()
        }
        while (lexer.has(TokenType.LineBreak)) {
            lexer.next()
        }
        tokens.add(EndGroup)
    }

    private fun processSection() {
        val name = expect(TokenType.Name) ?: return
        expect(TokenType.Colon) ?: return
        tokens.add(BeginSection(name = name.text))
        while (lexer.hasNext()) {
            val peek = lexer.peek()
            val newlineButNotDotSpace =
                peek.type == TokenType.Newline &&
                    !(lexer.hasNextNext() && lexer.peekPeek().type == TokenType.DotSpace)
            if (newlineButNotDotSpace) {
                lexer.next() // move past newline
                break
            }
            // either the next tokens are <newline><dot-space> or tokens for an argument
            if (peek.type == TokenType.Newline) {
                lexer.next() // move past the newline
            }
            val isInline =
                if (lexer.has(TokenType.DotSpace)) {
                    lexer.next() // move past the '. '
                    false
                } else {
                    true
                }
            processArgument(isInline)
            if (lexer.has(TokenType.UnIndent)) {
                lexer.next() // move past the un-indent to end the section
                break
            }
        }
        tokens.add(EndSection)
    }

    private fun processArgument(startsInline: Boolean) {
        var isInline = startsInline
        if (lexer.hasNextNext() &&
            lexer.peek().type == TokenType.Name &&
            lexer.peekPeek().type == TokenType.Colon) {
            tokens.add(BeginArgument)
            processGroup()
            tokens.add(EndArgument)
        } else {
            while (lexer.hasNext() && lexer.peek().type != TokenType.Newline) {
                val peek = lexer.peek()
                val arg = argument(isInline)
                if (arg != null) {
                    tokens.add(BeginArgument)
                    tokens.add(arg)
                    tokens.add(EndArgument)
                } else {
                    diagnostics.add(
                        Diagnostic(
                            type = DiagnosticType.Error,
                            message = "Expected an argument but found '${peek.text}'",
                            row = peek.row,
                            column = peek.column))
                }
                if (lexer.has(TokenType.Comma)) {
                    lexer.next() // move past the comma
                    isInline = true
                } else {
                    break
                }
            }
            while (lexer.hasNext()) {
                val peek = lexer.peek()
                if (peek.type == TokenType.Newline || peek.type == TokenType.UnIndent) {
                    lexer.next() // move past the newline or un-indent
                    break
                }

                val next = lexer.next()
                diagnostics.add(
                    Diagnostic(
                        type = DiagnosticType.Error,
                        message = "Unexpected token '${next.text}'",
                        row = next.row,
                        column = next.column))
            }
        }
    }
}

private fun TokenLexer.has(type: TokenType) = this.hasNext() && this.peek().type == type

private fun TokenLexer.hasHas(type1: TokenType, type2: TokenType) =
    this.hasNext() &&
        this.hasNextNext() &&
        this.peek().type == type1 &&
        this.peekPeek().type == type2
