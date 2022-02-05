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

package mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike

import mathlingua.frontend.chalktalk.phase1.ast.Phase1Node
import mathlingua.frontend.chalktalk.phase1.ast.getColumn
import mathlingua.frontend.chalktalk.phase1.ast.getRow
import mathlingua.frontend.chalktalk.phase2.CodeWriter
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_EXTENDING_SECTION
import mathlingua.frontend.chalktalk.phase2.ast.DEFAULT_STATEMENT
import mathlingua.frontend.chalktalk.phase2.ast.clause.Statement
import mathlingua.frontend.chalktalk.phase2.ast.clause.validateStatement
import mathlingua.frontend.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.frontend.chalktalk.phase2.ast.track
import mathlingua.frontend.chalktalk.phase2.ast.validateSection
import mathlingua.frontend.support.Location
import mathlingua.frontend.support.LocationTracker
import mathlingua.frontend.support.MutableLocationTracker
import mathlingua.frontend.support.ParseError
import mathlingua.frontend.support.ValidationSuccess
import mathlingua.frontend.textalk.ColonEqualsTexTalkNode
import mathlingua.frontend.textalk.InTexTalkNode
import mathlingua.frontend.textalk.IsTexTalkNode
import mathlingua.frontend.textalk.TupleNode

internal data class ExtendingSection(val statements: List<Statement>) : Phase2Node {
    override fun forEach(fn: (node: Phase2Node) -> Unit) = statements.forEach(fn)

    override fun toCode(isArg: Boolean, indent: Int, writer: CodeWriter): CodeWriter {
        writer.writeIndent(isArg, indent)
        writer.writeHeader("extending")
        if (statements.size == 1) {
            val stmt = statements.first()
            writer.writeIndent(false, 1)
            writer.writeStatement(stmt.text, stmt.texTalkRoot)
        } else {
            for (stmt in statements) {
                if (statements.isNotEmpty()) {
                    writer.writeNewline()
                }
                writer.writeIndent(true, 2)
                writer.writeStatement(stmt.text, stmt.texTalkRoot)
            }
        }
        return writer
    }

    override fun transform(chalkTransformer: (node: Phase2Node) -> Phase2Node) =
        chalkTransformer(
            ExtendingSection(
                statements = statements.map { it.transform(chalkTransformer) as Statement }))
}

internal fun validateExtendingSection(
    node: Phase1Node, errors: MutableList<ParseError>, tracker: MutableLocationTracker
) =
    track(node, tracker) {
        validateSection(node.resolve(), errors, DEFAULT_EXTENDING_SECTION) {
            if (it.args.isEmpty()) {
                errors.add(
                    ParseError(
                        message = "Expected at least 1 statements but found 0",
                        row = getRow(node),
                        column = getColumn(node)))
                DEFAULT_EXTENDING_SECTION
            } else {
                val errorsBefore = errors.size
                val statements =
                    it.args.map { arg ->
                        val statement = validateStatement(arg, errors, tracker)
                        if (statement.texTalkRoot !is ValidationSuccess ||
                            statement.texTalkRoot.value.children.size != 1 ||
                            (statement.texTalkRoot.value.children[0] !is IsTexTalkNode &&
                                statement.texTalkRoot.value.children[0] !is InTexTalkNode &&
                                statement.texTalkRoot.value.children[
                                    0] !is ColonEqualsTexTalkNode)) {
                            errors.add(
                                ParseError(
                                    message = "Expected an 'is', 'in', or ':=' statement",
                                    row = getRow(arg),
                                    column = getColumn(arg)))
                            DEFAULT_STATEMENT
                        } else {
                            statement
                        }
                    }

                if (errors.size != errorsBefore) {
                    DEFAULT_EXTENDING_SECTION
                } else {
                    val validationErrors =
                        verifyStatementsForms(
                            statements,
                            tracker,
                            Location(row = getRow(node), column = getColumn(node)))
                    if (validationErrors.isNotEmpty()) {
                        errors.addAll(validationErrors)
                        DEFAULT_EXTENDING_SECTION
                    } else {
                        ExtendingSection(statements = statements)
                    }
                }
            }
        }
    }

// thus function assumes each statement is of the form `... is ...` or
// `... in ...`, or `... := ...`
private fun verifyStatementsForms(
    statements: List<Statement>, tracker: LocationTracker, fallbackLocation: Location
): List<ParseError> {
    if (statements.isEmpty()) {
        return listOf(
            ParseError(
                message = "Expected at least one statement",
                row = fallbackLocation.row,
                column = fallbackLocation.column))
    }

    if (statements.size == 1) {
        return emptyList()
    }

    // There are more than one statement in the list.
    // Return an error if any of the items is an `:=` or an `in`
    // statement because only multiple `is` statements of the form
    // `(...) is ...` are allowed.
    val errors = mutableListOf<ParseError>()
    for (stmt in statements) {
        val first = (stmt.texTalkRoot as ValidationSuccess).value.children[0]
        if (first !is IsTexTalkNode) {
            val location = tracker.getLocationOf(stmt) ?: fallbackLocation
            errors.add(
                ParseError(
                    message =
                        "If multiple statements are provided they all must be `is` statements",
                    row = location.row,
                    column = location.column))
        }
    }

    if (errors.isNotEmpty()) {
        return errors
    }

    // Verify that all of the statements are of the form `(...) is ...`.
    return statements.mapNotNull {
        val first = (it.texTalkRoot as ValidationSuccess).value.children[0]
        if (first is IsTexTalkNode &&
            first.lhs.items.size == 1 &&
            first.lhs.items[0].children.size == 1 &&
            first.lhs.items[0].children[0] is TupleNode) {
            null
        } else {
            val location = tracker.getLocationOf(it) ?: fallbackLocation
            ParseError(
                message =
                    "If multiple `is` statements are provided, they must be of the form `(...) is ...`",
                row = location.row,
                column = location.column)
        }
    }
}
