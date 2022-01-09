/*
 * Copyright 2019 The MathLingua Authors
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

package mathlingua.frontend.chalktalk.phase1

import mathlingua.frontend.chalktalk.phase1.ast.ChalkTalkTokenType
import mathlingua.frontend.chalktalk.phase1.ast.Phase1Token
import mathlingua.frontend.support.ParseError
import mathlingua.newStack

internal interface ChalkTalkLexer {
    fun hasNext(): Boolean
    fun hasNextNext(): Boolean
    fun peek(): Phase1Token
    fun peekPeek(): Phase1Token
    fun next(): Phase1Token
    fun errors(): List<ParseError>
}

internal fun newChalkTalkLexer(text: String): ChalkTalkLexer {
    return ChalkTalkLexerImpl(text)
}

// ------------------------------------------------------------------------------------------------------------------ //

private class ChalkTalkLexerImpl(private var text: String) : ChalkTalkLexer {
    private val errors = mutableListOf<ParseError>()
    private var chalkTalkTokens = mutableListOf<Phase1Token>()
    private var index = 0

    init {
        if (!this.text.endsWith("\n")) {
            this.text += "\n"
        }

        var i = 0
        var line = 0
        var column = -1

        val levStack = newStack<Int>()
        levStack.push(0)

        var numOpen = 0

        while (i < text.length) {
            if (text[i] == '-' && i + 1 < text.length && text[i + 1] == '-') {
                // it is a comment and should be ignored
                while (i < text.length && text[i] != '\n') {
                    i++
                }
                while (i < text.length && text[i] == '\n') {
                    i++
                    line++
                    // since the cursor is now at the next line, move it
                    // so that it is just before the next line so that when
                    // the next character is read, column is incremented
                    // and is at zero, the start of the line
                    column = -1
                }
                continue
            }

            val c = text[i++]
            column++
            if (c == '.' &&
                i < text.length &&
                text[i] == '.' &&
                i + 1 < text.length &&
                text[i + 1] == '.') {
                val startColumn = column
                // move past the ...
                i += 2
                column += 2
                this.chalkTalkTokens.add(
                    Phase1Token("...", ChalkTalkTokenType.DotDotDot, line, startColumn))
            } else if (c == '=') {
                this.chalkTalkTokens.add(Phase1Token("=", ChalkTalkTokenType.Equals, line, column))
            } else if (c == '_') {
                this.chalkTalkTokens.add(
                    Phase1Token("_", ChalkTalkTokenType.Underscore, line, column))
            } else if (c == '(') {
                this.chalkTalkTokens.add(Phase1Token("(", ChalkTalkTokenType.LParen, line, column))
            } else if (c == ')') {
                this.chalkTalkTokens.add(Phase1Token(")", ChalkTalkTokenType.RParen, line, column))
            } else if (c == '{') {
                this.chalkTalkTokens.add(Phase1Token("{", ChalkTalkTokenType.LCurly, line, column))
            } else if (c == '}') {
                this.chalkTalkTokens.add(Phase1Token("}", ChalkTalkTokenType.RCurly, line, column))
            } else if (c == ',') {
                this.chalkTalkTokens.add(Phase1Token(",", ChalkTalkTokenType.Comma, line, column))
            } else if (c == '.' && i < text.length && text[i] == ' ') {
                this.chalkTalkTokens.add(
                    Phase1Token(". ", ChalkTalkTokenType.DotSpace, line, column))
                i++ // move past space
                column++
            } else if (c == '\n') {
                line++
                // since the cursor is now at the next line, move it
                // so that it is just before the next line so that when
                // the next character is read, column is incremented
                // and is a zero, the start of the row
                column = -1

                // text[i-1] == c since i was incremented
                // text[i-2] is the character before c
                if (i - 2 < 0 || text[i - 2] == '\n') {
                    while (i < text.length && text[i] == '\n') {
                        i++
                        line++
                    }
                    this.chalkTalkTokens.add(
                        Phase1Token("-", ChalkTalkTokenType.Linebreak, line, column = 0))
                    continue
                }

                var indentCount = 0
                while (i < text.length &&
                    i + 1 < text.length &&
                    text[i] == ' ' &&
                    text[i + 1] == ' ') {
                    indentCount++
                    i += 2
                    column += 2
                }

                // treat '. ' like another indent
                if (i < text.length &&
                    text[i] == '.' &&
                    i + 1 < text.length &&
                    text[i + 1] == ' ') {
                    indentCount++
                }

                this.chalkTalkTokens.add(
                    Phase1Token("<Indent>", ChalkTalkTokenType.Begin, line, maxOf(column, 0)))
                numOpen++

                val level = levStack.peek()
                if (indentCount <= level) {
                    while (numOpen > 0 && !levStack.isEmpty() && indentCount <= levStack.peek()) {
                        this.chalkTalkTokens.add(
                            Phase1Token(
                                "<Unindent>", ChalkTalkTokenType.End, line, maxOf(column, 0)))
                        numOpen--
                        levStack.pop()
                    }
                    // if the level stack is empty re-initialize it
                    if (levStack.isEmpty()) {
                        levStack.push(0)
                    }
                }
                levStack.push(indentCount)
            } else if (isOperatorChar(c)) {
                val startLine = line
                val startColumn = column
                var name = "" + c
                while (i < text.length && isOperatorChar(text[i])) {
                    name += text[i++]
                    column++
                }
                if (i < text.length &&
                    text[i] == '_' &&
                    i + 1 < text.length &&
                    text[i + 1] != '{') {
                    name += text[i++] // absorb the _
                    column++

                    if (i >= text.length || !isNameChar(text[i])) {
                        errors.add(
                            ParseError(
                                message = "Expected a name or a { after an underscore",
                                row = startLine,
                                column = startColumn))
                    } else {
                        while (i < text.length && isNameChar(text[i])) {
                            name += text[i++]
                            column++
                        }
                    }
                }
                this.chalkTalkTokens.add(
                    Phase1Token(name, ChalkTalkTokenType.Name, startLine, startColumn))
            } else if (isNameChar(c) || c == '?') {
                // a name token can be of the form
                //   ?
                //   name
                //   name?
                //   name...
                //   name#123
                //   name...#other...
                // where name is either <text> or <text>_<text>
                val startLine = line
                val startColumn = column
                var name = "" + c
                var isComplete = false

                // get the text portion
                while (i < text.length && isNameChar(text[i])) {
                    name += text[i++]
                    column++
                }

                if (i < text.length &&
                    text[i] == '_' &&
                    i + 1 < text.length &&
                    text[i + 1] != '{') {
                    name += text[i++] // absorb the _
                    column++

                    if (i >= text.length || !isNameChar(text[i])) {
                        errors.add(
                            ParseError(
                                message = "Expected a name or a { after an underscore",
                                row = startLine,
                                column = startColumn))
                    } else {
                        while (i < text.length && isNameChar(text[i])) {
                            name += text[i++]
                            column++
                        }
                    }
                }

                // check if the name is of the form text_text
                if (i < text.length &&
                    text[i] == '_' &&
                    i + 1 < text.length &&
                    isNameChar(text[i + 1])) {
                    name += text[i++]
                    column++

                    while (i < text.length && isNameChar(text[i])) {
                        name += text[i++]
                        column++
                    }
                }

                var hasQuestionMark = false
                if (i < text.length && text[i] == '?') {
                    hasQuestionMark = true
                    name += text[i++]
                    column++
                }

                if (!hasQuestionMark) {
                    // process the name#123 case and if matching mark the match as complete
                    if (i < text.length &&
                        text[i] == '#' &&
                        i + 1 < text.length &&
                        Character.isDigit(text[i + 1])) {
                        name += text[i++] // append #
                        column++
                        while (i < text.length && Character.isDigit(text[i])) {
                            name += text[i++]
                            column++
                        }
                        isComplete = true
                    }

                    // if it is not complete, that means it is not of the form name#123
                    // so check if it is of the form name...
                    if (!isComplete &&
                        i < text.length &&
                        text[i] == '.' &&
                        i + 1 < text.length &&
                        text[i + 1] == '.' &&
                        i + 2 < text.length &&
                        text[i + 2] == '.') {
                        for (tmp in 0 until "...".length) {
                            name += text[i++]
                            column++
                        }
                        // it is not necessarily complete if it is of the form name...
                        // at this point because it could actually be of the form name...#other...
                    }

                    // check if it is of the form name...#other...
                    if (!isComplete && i < text.length && text[i] == '#') {
                        name += text[i++] // append the #
                        column++
                        // get the name portion
                        while (i < text.length && isNameChar(text[i])) {
                            name += text[i++]
                            column++
                        }
                        // error if a name after # wasn't specified
                        if (name.endsWith("#")) {
                            errors.add(
                                ParseError(
                                    "If a name contains a # is must be of the form " +
                                        "<identifier>...#<identifier>... but found '$name' " +
                                        " (missing the name after '#')",
                                    startLine,
                                    startColumn))
                        }
                        // get the ... portion
                        if (i < text.length &&
                            text[i] == '.' &&
                            i + 1 < text.length &&
                            text[i + 1] == '.' &&
                            i + 2 < text.length &&
                            text[i + 2] == '.') {
                            for (tmp in 0 until "...".length) {
                                name += text[i++]
                                column++
                            }
                        }
                        // error if it is of the form <name>...#<name>
                        // without the trailing ...
                        if (!name.endsWith("...")) {
                            errors.add(
                                ParseError(
                                    "If a name contains a # is must be of the form " +
                                        "<identifier>...#<identifier>... but found '$name' " +
                                        "(missing the trailing '...')",
                                    startLine,
                                    startColumn))
                        }
                    }
                }

                this.chalkTalkTokens.add(
                    Phase1Token(name, ChalkTalkTokenType.Name, startLine, startColumn))
            } else if (c == '"') {
                val startLine = line
                val startColumn = column
                var str = "" + c
                while (i < text.length && text[i] != '"') {
                    val cur = text[i++]
                    column++
                    str += cur
                    if (cur == '\n' || cur == '\r') {
                        line++
                        column = -1
                    }
                }
                if (i == text.length) {
                    errors.add(ParseError("Expected a terminating \"", line, column))
                    str += "\""
                } else {
                    // include the terminating "
                    str += text[i++]
                    column++
                }
                this.chalkTalkTokens.add(
                    Phase1Token(str, ChalkTalkTokenType.String, startLine, startColumn))
            } else if (c == '\'') {
                val startLine = line
                val startColumn = column
                var stmt = "" + c
                while (i < text.length && text[i] != '\'') {
                    val cur = text[i++]
                    column++
                    stmt += cur
                    if (cur == '\n' || cur == '\r') {
                        line++
                        column = -1
                    }
                }
                if (i == text.length) {
                    errors.add(ParseError("Expected a terminating '", line, column))
                    stmt += "'"
                } else {
                    // include the terminating '
                    stmt += text[i++]
                    column++
                }
                this.chalkTalkTokens.add(
                    Phase1Token(stmt, ChalkTalkTokenType.Statement, startLine, startColumn))
            } else if (c == '[') {
                val startLine = line
                val startColumn = column

                var id = "" + c
                var braceCount = 1
                while (i < text.length && text[i] != '\n') {
                    val next = text[i++]
                    id += next
                    column++

                    if (next == '[') {
                        braceCount++
                    } else if (next == ']') {
                        braceCount--
                    }

                    if (braceCount == 0) {
                        break
                    }
                }

                if (i == text.length) {
                    errors.add(ParseError("Expected a terminating ]", line, column))
                    id += "]"
                }
                this.chalkTalkTokens.add(
                    Phase1Token(id, ChalkTalkTokenType.Id, startLine, startColumn))
            } else if (c == ':' && i < text.length && text[i] == ':') {
                val startLine = line
                val startColumn = column
                // move past the ::
                i++
                column++
                val builder = StringBuilder("::")
                while (i < text.length) {
                    // Replace {::} with ::
                    // This allows users to write {::} to have :: in their comment blocks
                    if (text[i] == '{' &&
                        i + 1 < text.length &&
                        text[i + 1] == ':' &&
                        i + 2 < text.length &&
                        text[i + 2] == ':' &&
                        i + 3 < text.length &&
                        text[i + 3] == '}') {
                        builder.append("::")
                        i += 4
                        column += 4
                    }

                    if (i >= text.length ||
                        (text[i] == ':' && i + 1 < text.length && text[i + 1] == ':')) {
                        break
                    }
                    val tmp = text[i++]
                    builder.append(tmp)
                    if (tmp == '\n' || tmp == '\r') {
                        line++
                        column = -1
                    }
                }
                if (i < text.length &&
                    text[i] == ':' &&
                    i + 1 < text.length &&
                    text[i + 1] == ':') {
                    // move past the ::
                    i += 2
                    builder.append("::")
                }
                if (i < text.length && text[i] == '\n') {
                    // address the trailing newline
                    i++
                    line++
                    column = -1
                }
                this.chalkTalkTokens.add(
                    Phase1Token(
                        builder.toString(),
                        ChalkTalkTokenType.BlockComment,
                        startLine,
                        startColumn))
            } else if (c == ':') {
                if (i < text.length && text[i] == '=') {
                    this.chalkTalkTokens.add(
                        Phase1Token(":=", ChalkTalkTokenType.ColonEquals, line, column))
                    i++ // move past the =
                    column++
                } else {
                    this.chalkTalkTokens.add(
                        Phase1Token(":", ChalkTalkTokenType.Colon, line, column))
                }
            } else if (c != ' ') { // spaces are ignored
                errors.add(ParseError("Unrecognized character $c", line, column))
            }
        }

        // numOpen must be used to determine the number of open Begin chalkTalkTokens
        // (as apposed to checking if levStack.isNotEmpty()) since whenever
        // the levStack is empty in the above code, it is re-initialized to
        // contain a level of 0
        while (numOpen > 0) {
            this.chalkTalkTokens.add(
                Phase1Token("<Unindent>", ChalkTalkTokenType.End, line, column))
            numOpen--
        }
    }

    private fun isOperatorChar(c: Char) = "~!@%^&*-+<>\\/=".contains(c)

    private fun isNameChar(c: Char) = Regex("[a-zA-Z0-9]+").matches("$c")

    override fun hasNext() = this.index < this.chalkTalkTokens.size

    override fun hasNextNext() = this.index + 1 < this.chalkTalkTokens.size

    override fun peek() = this.chalkTalkTokens[this.index]

    override fun peekPeek() = this.chalkTalkTokens[this.index + 1]

    override fun next(): Phase1Token {
        val tok = peek()
        this.index++
        return tok
    }

    override fun errors() = this.errors
}
