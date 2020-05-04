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

package mathlingua.common

import mathlingua.common.chalktalk.phase1.newChalkTalkLexer
import mathlingua.common.chalktalk.phase1.newChalkTalkParser
import mathlingua.common.chalktalk.phase2.*
import mathlingua.common.chalktalk.phase2.ast.Document
import mathlingua.common.chalktalk.phase2.ast.Phase2Node
import mathlingua.common.chalktalk.phase2.ast.toplevel.DefinesGroup
import mathlingua.common.chalktalk.phase2.ast.toplevel.RepresentsGroup
import mathlingua.common.chalktalk.phase2.ast.toplevel.TopLevelGroup
import mathlingua.common.chalktalk.phase2.ast.validateDocument
import mathlingua.common.textalk.Command
import mathlingua.common.transform.*

object MathLingua {
    fun parse(input: String): Validation<Document> {
        val lexer = newChalkTalkLexer(input)

        val allErrors = mutableListOf<ParseError>()
        allErrors.addAll(lexer.errors())

        val parser = newChalkTalkParser()
        val (root, errors) = parser.parse(lexer)
        allErrors.addAll(errors)

        if (root == null || allErrors.isNotEmpty()) {
            return ValidationFailure(allErrors)
        }

        return when (val documentValidation = validateDocument(root)) {
            is ValidationSuccess -> documentValidation
            is ValidationFailure -> {
                allErrors.addAll(documentValidation.errors)
                ValidationFailure(allErrors)
            }
        }
    }

    fun justify(text: String, width: Int) = mathlingua.common.justify(text, width)

    fun prettyPrintIdentifier(text: String) = mathlingua.common.chalktalk.phase2.prettyPrintIdentifier(text)

    fun signatureOf(group: TopLevelGroup) = getSignature(group)

    fun signatureOf(command: Command) = getCommandSignature(command)

    fun findAllSignatures(node: Phase2Node) = locateAllSignatures(node).toList()

    fun flattenSignature(signature: String) = mathlingua.common.transform.flattenSignature(signature)

    fun findAllCommands(node: Phase2Node) = locateAllCommands(node).toList()

    fun expandAtPosition(
        text: String,
        row: Int,
        column: Int,
        defines: List<DefinesGroup>,
        represents: List<RepresentsGroup>
    ) = when (val validation = parse(text)) {
        is ValidationFailure -> validation
        is ValidationSuccess -> {
            val doc = validation.value
            val target = findNode(doc, row, column)
            val newDoc = expandAtNode(doc, target, defines, represents) as Document
            ValidationSuccess(newDoc)
        }
    }

    fun expand(doc: Document) = fullExpandComplete(doc)
}
