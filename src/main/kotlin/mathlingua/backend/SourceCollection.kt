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

package mathlingua.backend

import mathlingua.backend.transform.GroupScope
import mathlingua.backend.transform.Signature
import mathlingua.backend.transform.checkVarsPhase2Node
import mathlingua.backend.transform.expandAsWritten
import mathlingua.backend.transform.getVarsTexTalkNode
import mathlingua.backend.transform.locateAllSignatures
import mathlingua.backend.transform.normalize
import mathlingua.backend.transform.signature
import mathlingua.cli.EntityResult
import mathlingua.cli.FileResult
import mathlingua.cli.VirtualFile
import mathlingua.cli.VirtualFileSystem
import mathlingua.cli.getAllWords
import mathlingua.cli.newAutoComplete
import mathlingua.cli.newSearchIndex
import mathlingua.frontend.FrontEnd
import mathlingua.frontend.chalktalk.phase1.ast.Abstraction
import mathlingua.frontend.chalktalk.phase1.ast.Tuple
import mathlingua.frontend.chalktalk.phase1.ast.findAllPhase1Statements
import mathlingua.frontend.chalktalk.phase1.ast.isOperatorName
import mathlingua.frontend.chalktalk.phase1.newChalkTalkLexer
import mathlingua.frontend.chalktalk.phase1.newChalkTalkParser
import mathlingua.frontend.chalktalk.phase2.ast.Document
import mathlingua.frontend.chalktalk.phase2.ast.clause.AssignmentNode
import mathlingua.frontend.chalktalk.phase2.ast.clause.Clause
import mathlingua.frontend.chalktalk.phase2.ast.clause.ClauseListNode
import mathlingua.frontend.chalktalk.phase2.ast.clause.IdStatement
import mathlingua.frontend.chalktalk.phase2.ast.clause.Statement
import mathlingua.frontend.chalktalk.phase2.ast.clause.TupleNode
import mathlingua.frontend.chalktalk.phase2.ast.common.Phase2Node
import mathlingua.frontend.chalktalk.phase2.ast.findAllStatements
import mathlingua.frontend.chalktalk.phase2.ast.findAllTexTalkNodes
import mathlingua.frontend.chalktalk.phase2.ast.findColonEqualsLhsSymbols
import mathlingua.frontend.chalktalk.phase2.ast.findColonEqualsRhsSignatures
import mathlingua.frontend.chalktalk.phase2.ast.findInRhsSignatures
import mathlingua.frontend.chalktalk.phase2.ast.findIsLhsSymbols
import mathlingua.frontend.chalktalk.phase2.ast.findIsRhsSignatures
import mathlingua.frontend.chalktalk.phase2.ast.getNonIsNonInStatementsNonInAsSections
import mathlingua.frontend.chalktalk.phase2.ast.group.clause.If.ThenSection
import mathlingua.frontend.chalktalk.phase2.ast.group.clause.generated.GeneratedGroup
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.HasSignature
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.HasUsingSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.TopLevelGroup
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.CalledSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.WrittenSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.defines.DefinesGroup
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.defines.DefinesSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.defines.SatisfyingSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.defineslike.states.StatesGroup
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.getInputSymbols
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.getMeansExpressesSections
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.getOutputSymbols
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.getWhenSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.resultlike.axiom.AxiomGroup
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.resultlike.conjecture.ConjectureGroup
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.resultlike.theorem.TheoremGroup
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.resultlike.theorem.TheoremSection
import mathlingua.frontend.chalktalk.phase2.ast.group.toplevel.toEntityResult
import mathlingua.frontend.chalktalk.phase2.getInnerDefinedSignatures
import mathlingua.frontend.chalktalk.phase2.getPatternsToWrittenAs
import mathlingua.frontend.chalktalk.phase2.newHtmlCodeWriter
import mathlingua.frontend.chalktalk.phase2.newMathLinguaCodeWriter
import mathlingua.frontend.support.Location
import mathlingua.frontend.support.ParseError
import mathlingua.frontend.support.Validation
import mathlingua.frontend.support.ValidationFailure
import mathlingua.frontend.support.ValidationSuccess
import mathlingua.frontend.textalk.ColonEqualsTexTalkNode
import mathlingua.frontend.textalk.Command
import mathlingua.frontend.textalk.ExpressionTexTalkNode
import mathlingua.frontend.textalk.OperatorTexTalkNode
import mathlingua.frontend.textalk.TexTalkNode
import mathlingua.frontend.textalk.TexTalkNodeType
import mathlingua.frontend.textalk.TexTalkTokenType
import mathlingua.frontend.textalk.TextTexTalkNode
import mathlingua.frontend.textalk.getSignaturesWithin
import mathlingua.frontend.textalk.newTexTalkLexer
import mathlingua.frontend.textalk.newTexTalkParser

internal data class ValueAndSource<T>(val value: T, val source: SourceFile)

internal data class Page(val sourceFile: SourceFile, val fileResult: FileResult)

internal interface SourceCollection {
    fun size(): Int
    fun getDefinedSignatures(): Set<ValueAndSource<Signature>>
    fun getDuplicateDefinedSignatures(): List<ValueAndSource<Signature>>
    fun getUndefinedSignatures(): Set<ValueAndSource<Signature>>
    fun findInvalidTypes(): List<ValueAndSource<ParseError>>
    fun findMultipleIsStatementsWithoutMeansSection(): List<ValueAndSource<ParseError>>
    fun findInvalidMeansSection(): List<ValueAndSource<ParseError>>
    fun getParseErrors(): List<ValueAndSource<ParseError>>
    fun getDuplicateContent(): List<ValueAndSource<TopLevelGroup>>
    fun getSymbolErrors(): List<ValueAndSource<ParseError>>
    fun getIsRhsErrors(): List<ValueAndSource<ParseError>>
    fun getColonEqualsRhsErrors(): List<ValueAndSource<ParseError>>
    fun getInputOutputSymbolErrors(): List<ValueAndSource<ParseError>>
    fun getNonExpressesUsedInNonIsNonInStatementsErrors(): List<ValueAndSource<ParseError>>
    fun prettyPrint(
        file: VirtualFile, html: Boolean, literal: Boolean, doExpand: Boolean
    ): Pair<List<Pair<String, Phase2Node?>>, List<ParseError>>
    fun prettyPrint(node: Phase2Node, html: Boolean, literal: Boolean, doExpand: Boolean): String
    fun getAllPaths(): List<String>
    fun getFirstPath(): String
    fun getPage(path: String): Page?
    fun getWithSignature(signature: String): EntityResult?
    fun addSource(sf: SourceFile)
    fun removeSource(path: String)
    fun findWordSuffixesFor(word: String): List<String>
    fun findSignaturesSuffixesFor(prefix: String): List<String>
    fun search(query: String): List<SourceFile>
    fun getUsedSignaturesAtRow(path: String, row: Int): List<ValueAndSource<Signature>>
}

internal fun newSourceCollection(
    fs: VirtualFileSystem, filesOrDirs: List<VirtualFile>
): SourceCollection {
    val sources = findMathLinguaFiles(filesOrDirs).map { it.buildSourceFile() }
    return SourceCollectionImpl(fs, sources)
}

internal fun newSourceCollectionFromCwd(fs: VirtualFileSystem) =
    newSourceCollection(fs, listOf(fs.cwd()))

internal fun findMathLinguaFiles(files: List<VirtualFile>): List<VirtualFile> {
    val result = mutableListOf<VirtualFile>()
    for (file in files) {
        findMathLinguaFilesImpl(file, result)
    }
    return result
}

// -----------------------------------------------------------------------------

private class StringPartsComparator : Comparator<List<String>> {
    override fun compare(list1: List<String>?, list2: List<String>?): Int {
        if (list1 == null && list2 != null) {
            return -1
        }

        if (list1 != null && list2 == null) {
            return 1
        }

        if (list1 == null && list2 == null) {
            return 0
        }

        return compare(list1!!, 0, list2!!, 0)
    }

    private fun getNumberPrefix(part: String): Double? {
        val index = part.indexOf('_')
        if (index < 0) {
            return null
        }
        return part.substring(0, index).toDoubleOrNull()
    }

    private fun compare(parts1: List<String>, index1: Int, parts2: List<String>, index2: Int): Int {
        if (index1 >= parts1.size && index2 >= parts2.size) {
            return 0
        }

        if (index1 >= parts1.size && index2 < parts2.size) {
            return -1
        }

        if (index1 < parts1.size && index2 >= parts2.size) {
            return 1
        }

        val p1 = parts1[index1]
        val p2 = parts2[index2]

        val num1 = getNumberPrefix(p1)
        val num2 = getNumberPrefix(p2)

        if (num1 != null && num2 == null) {
            return -1
        }

        if (num1 == null && num2 != null) {
            return 1
        }

        val comp =
            if (num1 == null && num2 == null) {
                p1.compareTo(p2)
            } else {
                num1!!.compareTo(num2!!)
            }

        return if (comp == 0) {
            compare(parts1, index1 + 1, parts2, index2 + 1)
        } else {
            comp
        }
    }
}

private val STRING_PARTS_COMPARATOR = StringPartsComparator()

private class SourcePathComparator : Comparator<VirtualFile> {
    override fun compare(file1: VirtualFile?, file2: VirtualFile?): Int {
        if (file1 == null && file2 != null) {
            return -1
        }

        if (file1 != null && file2 == null) {
            return 1
        }

        if (file1 == null && file2 == null) {
            return 0
        }

        return STRING_PARTS_COMPARATOR.compare(
            file1!!.absolutePathParts(), file2!!.absolutePathParts())
    }
}

private val SOURCE_PATH_COMPARATOR = SourcePathComparator()

private fun findMathLinguaFilesImpl(file: VirtualFile, result: MutableList<VirtualFile>) {
    if (file.isMathLinguaFile()) {
        result.add(file)
    }
    for (child in file.listFiles().sortedWith(SOURCE_PATH_COMPARATOR)) {
        findMathLinguaFilesImpl(child, result)
    }
}

private data class Normalized<T>(val original: T, val normalized: T)

private class SourceCollectionImpl(val fs: VirtualFileSystem, val sources: List<SourceFile>) :
    SourceCollection {
    private val sourceFiles = mutableMapOf<String, SourceFile>()
    private val sourceFileToFileResult = mutableMapOf<SourceFile, FileResult>()

    private val wordAutoComplete = newAutoComplete(preserveCase = false)
    private val signatureAutoComplete = newAutoComplete(preserveCase = true)

    private val searchIndex = newSearchIndex(fs)

    private val signatureToTopLevelGroup = mutableMapOf<String, TopLevelGroup>()
    private val signatureToRelativePath = mutableMapOf<String, String>()

    private val allGroups = mutableListOf<ValueAndSource<Normalized<TopLevelGroup>>>()
    private val definesGroups = mutableListOf<ValueAndSource<Normalized<DefinesGroup>>>()
    private val statesGroups = mutableListOf<ValueAndSource<Normalized<StatesGroup>>>()
    private val axiomGroups = mutableListOf<ValueAndSource<Normalized<AxiomGroup>>>()
    private val theoremGroups = mutableListOf<ValueAndSource<Normalized<TheoremGroup>>>()
    private val conjectureGroups = mutableListOf<ValueAndSource<Normalized<ConjectureGroup>>>()

    init {
        // add all the sources
        for (sf in sources) {
            addSource(sf)
        }

        // pre-calculate the rendering of each page to speed up access later on
        for (path in getAllPaths()) {
            getPage(path)
        }
    }

    override fun search(query: String): List<SourceFile> {
        return searchIndex.search(query).mapNotNull {
            val path = it.joinToString("/")
            sourceFiles[path]
        }
    }

    override fun findWordSuffixesFor(word: String): List<String> {
        return wordAutoComplete.findSuffixes(word)
    }

    override fun findSignaturesSuffixesFor(prefix: String): List<String> {
        return signatureAutoComplete.findSuffixes(prefix).map { it }
    }

    override fun getAllPaths(): List<String> {
        return sourceFiles
            .keys
            .toList()
            .map { it.split("/") }
            .sortedWith(STRING_PARTS_COMPARATOR)
            .map { it.joinToString("/") }
    }

    override fun getFirstPath() = getAllPaths().first()

    override fun getPage(path: String): Page? {
        val sourceFile = sourceFiles[path] ?: return null
        val fileResult = sourceFileToFileResult[sourceFile]
        if (fileResult != null) {
            return Page(sourceFile = sourceFile, fileResult = fileResult)
        }

        var prev: SourceFile? = null
        var next: SourceFile? = null
        for (i in sources.indices) {
            if (sources[i].file.relativePath() == path) {
                prev = sources.getOrNull(i - 1)
                next = sources.getOrNull(i + 1)
                break
            }
        }

        val evalFileResult =
            sourceFile.toFileResult(
                previousRelativePath = prev?.file?.relativePath(),
                nextRelativePath = next?.file?.relativePath(),
                sourceCollection = this)
        sourceFileToFileResult[sourceFile] = evalFileResult
        return Page(sourceFile = sourceFile, fileResult = evalFileResult)
    }

    override fun getWithSignature(signature: String): EntityResult? {
        val relativePath = signatureToRelativePath[signature] ?: return null
        return signatureToTopLevelGroup[signature]?.toEntityResult(relativePath, this)
    }

    override fun removeSource(path: String) {
        sourceFileToFileResult.clear()
        val relPath = path.split("/")
        searchIndex.remove(relPath)
        val sf = sourceFiles[path] ?: return
        sourceFiles.remove(path)
        when (val validation = sf.validation
        ) {
            is ValidationSuccess -> {
                val doc = validation.value
                val docDefines = doc.defines().toSet()
                val docStates = doc.states().toSet()
                val docAxioms = doc.axioms().toSet()
                val docTheorems = doc.theorems().toSet()
                val docConjectures = doc.conjectures().toSet()
                val docAll = doc.groups.toSet()

                for (grp in docAll) {
                    if (grp is HasSignature && grp.signature != null) {
                        val key = grp.signature!!.form
                        signatureToTopLevelGroup.remove(key)
                        signatureToRelativePath.remove(key)
                        if (grp.id != null) {
                            signatureAutoComplete.remove(grp.id!!.text)
                        }
                    }
                }

                for (grp in validation.value.groups) {
                    for (word in getAllWords(grp)) {
                        wordAutoComplete.remove(word)
                    }
                }

                definesGroups.removeAll { docDefines.contains(it.value.original) }

                statesGroups.removeAll { docStates.contains(it.value.original) }

                axiomGroups.removeAll { docAxioms.contains(it.value.original) }

                theoremGroups.removeAll { docTheorems.contains(it.value.original) }

                conjectureGroups.removeAll { docConjectures.contains(it.value.original) }

                allGroups.removeAll { docAll.contains(it.value.original) }
            }
        }
    }

    override fun addSource(sf: SourceFile) {
        sourceFileToFileResult.clear()
        val relativePath = sf.file.relativePath()
        sourceFiles[relativePath] = sf
        searchIndex.add(sf)
        val validation = sf.validation
        if (validation is ValidationSuccess) {
            for (grp in validation.value.groups) {
                for (word in getAllWords(grp)) {
                    wordAutoComplete.add(word)
                }
            }

            for (grp in validation.value.groups) {
                if (grp is HasSignature && grp.signature != null) {
                    val key = grp.signature!!.form
                    signatureToTopLevelGroup[key] = grp
                    signatureToRelativePath[key] = relativePath
                    if (grp.id != null) {
                        signatureAutoComplete.add(grp.id!!.text)
                    }
                }
            }

            definesGroups.addAll(
                validation.value.defines().map {
                    ValueAndSource(
                        source = sf,
                        value =
                            Normalized(original = it, normalized = it.normalize() as DefinesGroup))
                })

            statesGroups.addAll(
                validation.value.states().map {
                    ValueAndSource(
                        source = sf,
                        value =
                            Normalized(original = it, normalized = it.normalize() as StatesGroup))
                })

            axiomGroups.addAll(
                validation.value.axioms().map {
                    ValueAndSource(
                        source = sf,
                        value =
                            Normalized(original = it, normalized = it.normalize() as AxiomGroup))
                })

            theoremGroups.addAll(
                validation.value.theorems().map {
                    ValueAndSource(
                        source = sf,
                        value =
                            Normalized(original = it, normalized = it.normalize() as TheoremGroup))
                })

            conjectureGroups.addAll(
                validation.value.conjectures().map {
                    ValueAndSource(
                        source = sf,
                        value =
                            Normalized(
                                original = it, normalized = it.normalize() as ConjectureGroup))
                })

            allGroups.addAll(
                validation.value.groups.map {
                    ValueAndSource(
                        source = sf,
                        value =
                            Normalized(original = it, normalized = it.normalize() as TopLevelGroup))
                })
        }
    }

    override fun size() = sourceFiles.size

    override fun getIsRhsErrors(): List<ValueAndSource<ParseError>> {
        val result = mutableListOf<ValueAndSource<ParseError>>()
        val sigsWithExpresses = getSignaturesWithExpressesSection().map { it.value.form }.toSet()
        val theoremSigs = theoremGroups.mapNotNull { it.value.original.signature?.form }.toSet()
        val axiomSigs = axiomGroups.mapNotNull { it.value.original.signature?.form }.toSet()
        val conjectureSigs = axiomGroups.mapNotNull { it.value.original.signature?.form }.toSet()
        val statesSigs = statesGroups.mapNotNull { it.value.original.signature?.form }.toSet()
        for (svt in allGroups) {
            val rhsIsSigs = svt.value.normalized.findIsRhsSignatures()
            for (sig in rhsIsSigs) {
                if (sigsWithExpresses.contains(sig.form)) {
                    result.add(
                        ValueAndSource(
                            value =
                                ParseError(
                                    message =
                                        "The right-hand-side of an `is` cannot reference a `Defines:` with an `expressing:` section but found '${sig.form}'",
                                    row = sig.location.row,
                                    column = sig.location.column),
                            source = svt.source))
                }

                if (theoremSigs.contains(sig.form)) {
                    result.add(
                        ValueAndSource(
                            value =
                                ParseError(
                                    message =
                                        "The right-hand-side of an `is` cannot reference a `Theorem:` but found '${sig.form}'",
                                    row = sig.location.row,
                                    column = sig.location.column),
                            source = svt.source))
                }

                if (axiomSigs.contains(sig.form)) {
                    result.add(
                        ValueAndSource(
                            value =
                                ParseError(
                                    message =
                                        "The right-hand-side of an `is` cannot reference a `Axiom:` but found '${sig.form}'",
                                    row = sig.location.row,
                                    column = sig.location.column),
                            source = svt.source))
                }

                if (conjectureSigs.contains(sig.form)) {
                    result.add(
                        ValueAndSource(
                            value =
                                ParseError(
                                    message =
                                        "The right-hand-side of an `is` cannot reference a `Conjecture:` but found '${sig.form}'",
                                    row = sig.location.row,
                                    column = sig.location.column),
                            source = svt.source))
                }

                if (statesSigs.contains(sig.form)) {
                    result.add(
                        ValueAndSource(
                            value =
                                ParseError(
                                    message =
                                        "The right-hand-side of an `is` cannot reference a `States:` but found '${sig.form}'",
                                    row = sig.location.row,
                                    column = sig.location.column),
                            source = svt.source))
                }
            }

            val sigsWithoutExpresses =
                getSignaturesWithoutExpressesSection().map { it.value.form }.toSet()
            val rhsInSigs = svt.value.normalized.findInRhsSignatures()
            for (sig in rhsInSigs) {
                if (sigsWithoutExpresses.contains(sig.form)) {
                    result.add(
                        ValueAndSource(
                            value =
                                ParseError(
                                    message =
                                        "The right-hand-side of an `in` cannot reference a `Defines:` without an `expressing:` section but found '${sig.form}'",
                                    row = sig.location.row,
                                    column = sig.location.column),
                            source = svt.source))
                }
            }
        }
        return result
    }

    override fun getColonEqualsRhsErrors(): List<ValueAndSource<ParseError>> {
        val result = mutableListOf<ValueAndSource<ParseError>>()
        val sigsWithoutExpresses =
            getSignaturesWithoutExpressesSection().map { it.value.form }.toSet()
        for (svt in allGroups) {
            val rhsSigs = svt.value.normalized.findColonEqualsRhsSignatures()
            for (sig in rhsSigs) {
                if (sigsWithoutExpresses.contains(sig.form)) {
                    result.add(
                        ValueAndSource(
                            value =
                                ParseError(
                                    message =
                                        "The right-hand-side of an `:=` cannot reference a `Defines:` without an `expressing:` section but found '${sig.form}'",
                                    row = sig.location.row,
                                    column = sig.location.column),
                            source = svt.source))
                }
            }
        }
        return result
    }

    private fun getSignaturesWithStates(): List<ValueAndSource<Signature>> {
        val result = mutableListOf<ValueAndSource<Signature>>()
        for (svt in statesGroups) {
            val def = svt.value.original
            if (def.signature != null) {
                result.add(ValueAndSource(value = def.signature, source = svt.source))
            }
        }
        return result
    }

    private fun getSignaturesWithoutExpressesSection(): List<ValueAndSource<Signature>> {
        val result = mutableListOf<ValueAndSource<Signature>>()
        for (svt in allGroups) {
            val def = svt.value.original
            if (def is HasSignature) {
                val sig = def.signature ?: continue
                if (def !is DefinesGroup || def.expressingSection == null) {
                    result.add(ValueAndSource(value = sig, source = svt.source))
                }
            }
        }
        return result
    }

    private fun getSignaturesWithExpressesSection(): List<ValueAndSource<Signature>> {
        val result = mutableListOf<ValueAndSource<Signature>>()
        for (svt in definesGroups) {
            val def = svt.value.original
            if (def.expressingSection != null && def.signature != null) {
                result.add(ValueAndSource(value = def.signature, source = svt.source))
            }
        }
        return result
    }

    override fun getDefinedSignatures(): Set<ValueAndSource<Signature>> {
        val result = mutableSetOf<ValueAndSource<Signature>>()
        result.addAll(getAllDefinedSignatures().map { it.first })
        return result
    }

    override fun getDuplicateDefinedSignatures(): List<ValueAndSource<Signature>> {
        val duplicates = mutableListOf<ValueAndSource<Signature>>()
        val found = mutableSetOf<String>()
        for (sig in getAllDefinedSignatures().map { it.first }) {
            if (found.contains(sig.value.form)) {
                duplicates.add(sig)
            }
            found.add(sig.value.form)
        }
        return duplicates
    }

    override fun getUndefinedSignatures(): Set<ValueAndSource<Signature>> {
        val result = mutableSetOf<ValueAndSource<Signature>>()
        val globalDefinedSigs = getDefinedSignatures().map { it.value.form }.toSet()
        for (vst in allGroups) {
            val innerSigs = getInnerDefinedSignatures(vst.value.normalized).map { it.form }.toSet()
            val usedSigs = vst.value.normalized.locateAllSignatures(ignoreLhsEqual = true)
            for (sig in usedSigs) {
                if (!globalDefinedSigs.contains(sig.form) && !innerSigs.contains(sig.form)) {
                    // TODO: Right now operators are not marked as undefined signatures because
                    //       they will (in the feature) be resolved using types.  However, that
                    //       resolution algorithm is not implemented.  Thus, until it is
                    //       implemented we do not report errors for unknown operators.
                    if (!isOperatorName(sig.form)) {
                        result.add(ValueAndSource(value = sig, source = vst.source))
                    }
                }
            }
        }
        return result
    }

    override fun findInvalidTypes(): List<ValueAndSource<ParseError>> {
        val result = mutableListOf<ValueAndSource<ParseError>>()
        val defs =
            getAllDefinedSignatures().filter { it.second is DefinesGroup }.map {
                Pair(it.first, it.second as DefinesGroup)
            }
        val analyzer = newSymbolAnalyzer(defs)
        for (sf in sourceFiles) {
            val lexer = newChalkTalkLexer(sf.value.content)
            val parse = newChalkTalkParser().parse(lexer)
            result.addAll(
                parse.errors.map {
                    ValueAndSource(
                        source = sf.value,
                        value =
                            ParseError(
                                message = it.message,
                                row = it.row.coerceAtLeast(0),
                                column = it.column.coerceAtLeast(0)))
                })

            val root = parse.root
            if (root != null) {
                for (stmtNode in root.findAllPhase1Statements()) {
                    val text = stmtNode.text.removeSurrounding("'", "'")
                    val texTalkLexer = newTexTalkLexer(text)
                    val texTalkResult = newTexTalkParser().parse(texTalkLexer)
                    result.addAll(
                        texTalkResult.errors.map {
                            ValueAndSource(
                                source = sf.value,
                                value =
                                    ParseError(
                                        message = it.message,
                                        row =
                                            stmtNode.row.coerceAtLeast(0) + it.row.coerceAtLeast(0),
                                        column =
                                            stmtNode.column.coerceAtLeast(0) +
                                                it.column.coerceAtLeast(0)))
                        })
                }
            }

            when (val validation = sf.value.validation
            ) {
                is ValidationSuccess -> {
                    val doc = validation.value
                    for (grp in doc.groups) {
                        val errors = analyzer.findInvalidTypes(grp)
                        result.addAll(
                            errors.map {
                                ValueAndSource(
                                    source = sf.value,
                                    value =
                                        ParseError(
                                            message = it.message,
                                            row = it.row.coerceAtLeast(0),
                                            column = it.column.coerceAtLeast(0)))
                            })
                    }

                    for (pair in doc.findAllStatements()) {
                        val stmt = pair.first
                        val aliasDefines = pair.second
                        val location = Location(stmt.row, stmt.column)
                        for (node in stmt.findAllTexTalkNodes()) {
                            val expansion =
                                expandAsWritten(
                                    target = null,
                                    node = node,
                                    operatorPatternToExpansion =
                                        getPatternsToWrittenAs(
                                            defines =
                                                definesGroups
                                                    .map { it.value.normalized }
                                                    .plus(aliasDefines),
                                            states = statesGroups.map { it.value.normalized },
                                            axioms = axiomGroups.map { it.value.normalized }))
                            result.addAll(
                                expansion.errors.map {
                                    ValueAndSource(
                                        source = sf.value,
                                        value =
                                            ParseError(
                                                message = it,
                                                row = location.row.coerceAtLeast(0),
                                                column = location.column.coerceAtLeast(0)))
                                })
                        }
                    }
                }
                else -> {
                    // if parsing fails, then no further checking is needed
                }
            }
        }
        return result
    }

    override fun findMultipleIsStatementsWithoutMeansSection(): List<ValueAndSource<ParseError>> {
        val result = mutableListOf<ValueAndSource<ParseError>>()
        val defSigs = getAllDefinedSignatures()
        val defs =
            defSigs.filter { it.second is DefinesGroup }.map {
                Pair(it.first, it.second as DefinesGroup)
            }
        val analyzer = newSymbolAnalyzer(defs)
        for (ds in defSigs) {
            val top = ds.second
            if (top is DefinesGroup) {
                result.addAll(
                    analyzer.findMultipleIsStatementsWithoutMeansSection(top).map {
                        ValueAndSource(value = it, source = ds.first.source)
                    })
            }
        }
        return result
    }

    override fun findInvalidMeansSection(): List<ValueAndSource<ParseError>> {
        val result = mutableListOf<ValueAndSource<ParseError>>()
        val defSigs = getAllDefinedSignatures()
        val defs =
            defSigs.filter { it.second is DefinesGroup }.map {
                Pair(it.first, it.second as DefinesGroup)
            }
        val analyzer = newSymbolAnalyzer(defs)
        for (ds in defSigs) {
            val top = ds.second
            if (top is DefinesGroup) {
                result.addAll(
                    analyzer.findInvalidMeansSection(top).map {
                        ValueAndSource(value = it, source = ds.first.source)
                    })
            }
        }
        return result
    }

    override fun getParseErrors(): List<ValueAndSource<ParseError>> {
        val result = mutableListOf<ValueAndSource<ParseError>>()
        for (sf in sourceFiles) {
            val validation = sf.value.validation
            when (validation) {
                is ValidationFailure -> {
                    result.addAll(
                        validation.errors.map { ValueAndSource(source = sf.value, value = it) })
                }
                is ValidationSuccess -> {
                    val doc = validation.value

                    val allDefines = mutableListOf<DefinesGroup>()
                    allDefines.addAll(doc.defines())

                    val allStates = mutableListOf<StatesGroup>()
                    allStates.addAll(doc.states())

                    val allSigRoot = mutableListOf<Validation<TexTalkNode>>()
                    allSigRoot.addAll(allDefines.map { it.id.texTalkRoot })
                    allSigRoot.addAll(allStates.map { it.id.texTalkRoot })

                    for (vald in allSigRoot) {
                        if (vald is ValidationFailure) {
                            result.addAll(
                                vald.errors.map { ValueAndSource(source = sf.value, value = it) })
                        }
                    }
                }
            }
        }
        return result
    }

    override fun getDuplicateContent(): List<ValueAndSource<TopLevelGroup>> {
        val result = mutableListOf<ValueAndSource<TopLevelGroup>>()
        val allContent = mutableSetOf<String>()
        for (group in allGroups) {
            val content = group.value.normalized.toCode(false, 0).getCode().trim()
            if (allContent.contains(content)) {
                result.add(ValueAndSource(source = group.source, value = group.value.normalized))
            }
            allContent.add(content)
        }
        return result
    }

    override fun prettyPrint(
        file: VirtualFile, html: Boolean, literal: Boolean, doExpand: Boolean
    ): Pair<List<Pair<String, Phase2Node?>>, List<ParseError>> {
        val sourceFile = sourceFiles[file.absolutePathParts().joinToString("/")]
        return if (sourceFile != null) {
            prettyPrint(sourceFile.validation, html, literal, doExpand)
        } else {
            prettyPrint(file.readText(), html, literal, doExpand)
        }
    }

    private fun prettyPrint(
        input: String, html: Boolean, literal: Boolean, doExpand: Boolean
    ): Pair<List<Pair<String, Phase2Node?>>, List<ParseError>> {
        return prettyPrint(FrontEnd.parse(input), html, literal, doExpand)
    }

    private fun prettyPrint(
        validation: Validation<Document>, html: Boolean, literal: Boolean, doExpand: Boolean
    ): Pair<List<Pair<String, Phase2Node?>>, List<ParseError>> {
        val content: List<Pair<String, Phase2Node?>> =
            when (validation) {
                is ValidationFailure -> {
                    listOf(
                        Pair(
                            if (html) {
                                val builder = StringBuilder()
                                builder.append(
                                    "<html><head><style>.content { font-size: 1em; }" +
                                        "</style></head><body class='content'><ul>")
                                for (err in validation.errors) {
                                    builder.append(
                                        "<li><span style='color: #e61919;'>ERROR:</span> " +
                                            "${err.message} (${err.row + 1}, ${err.column + 1})</li>")
                                }
                                builder.append("</ul></body></html>")
                                builder.toString()
                            } else {
                                val builder = StringBuilder()
                                for (err in validation.errors) {
                                    builder.append(
                                        "ERROR: ${err.message} (${err.row + 1}, ${err.column + 1})\n")
                                }
                                builder.toString()
                            },
                            null))
                }
                is ValidationSuccess ->
                    prettyPrint(
                        doc = validation.value, html = html, literal = literal, doExpand = doExpand)
            }

        return when (validation) {
            is ValidationFailure -> Pair(content, validation.errors)
            is ValidationSuccess -> Pair(content, emptyList())
        }
    }

    private fun prettyPrint(doc: Document, html: Boolean, literal: Boolean, doExpand: Boolean) =
        doc.groups.map { Pair(prettyPrint(it, html, literal, doExpand), it) }

    private fun getWriter(
        html: Boolean, literal: Boolean, doExpand: Boolean, aliasDefines: List<DefinesGroup>
    ) =
        if (html) {
            newHtmlCodeWriter(
                defines =
                    doExpand.thenUse {
                        definesGroups.map { it.value.normalized }.plus(aliasDefines)
                    },
                states = doExpand.thenUse { statesGroups.map { it.value.normalized } },
                axioms = doExpand.thenUse { axiomGroups.map { it.value.normalized } },
                literal = literal)
        } else {
            newMathLinguaCodeWriter(
                defines =
                    doExpand.thenUse {
                        definesGroups.map { it.value.normalized }.plus(aliasDefines)
                    },
                states = doExpand.thenUse { statesGroups.map { it.value.normalized } },
                axioms = doExpand.thenUse { axiomGroups.map { it.value.normalized } })
        }

    private fun getExpandedText(
        exp: ExpressionTexTalkNode, aliasDefines: List<DefinesGroup>
    ): String? {
        val writer =
            getWriter(html = false, literal = false, doExpand = true, aliasDefines = aliasDefines)
        val tmpTheorem =
            TheoremGroup(
                signature = null,
                id = null,
                theoremSection = TheoremSection(names = emptyList(), row = -1, column = -1),
                givenSection = null,
                whenSection = null,
                thenSection =
                    ThenSection(
                        clauses =
                            ClauseListNode(
                                clauses =
                                    listOf(
                                        Statement(
                                            text = exp.toCode(),
                                            texTalkRoot = ValidationSuccess(exp),
                                            row = -1,
                                            column = -1,
                                            isInline = false)),
                                row = -1,
                                column = -1),
                        row = -1,
                        column = -1),
                iffSection = null,
                proofSection = null,
                usingSection = null,
                metaDataSection = null,
                row = -1,
                column = -1)
        val expanded = tmpTheorem.toCode(false, 0, writer).getCode()
        return when (val validation = FrontEnd.parse(expanded)
        ) {
            is ValidationSuccess -> {
                val doc = validation.value
                val stmt = doc.theorems()[0].thenSection.clauses.clauses[0] as Statement
                stmt.text
            }
            else -> {
                null
            }
        }
    }

    private fun attemptAddAliasDefinesForColonEqualsTuple(
        colonEquals: ColonEqualsTexTalkNode, aliasDefines: MutableList<DefinesGroup>
    ) {
        val lhsItems = colonEquals.lhs.items
        val rhsItems = colonEquals.rhs.items
        if (lhsItems.size != 1 || rhsItems.size != 1) {
            println(
                "The left-hand-side and right-hand-side of a := must have exactly one expression")
            return
        }

        val lhsItem = lhsItems[0]
        if (lhsItem.children.size != 1 ||
            lhsItem.children[0] !is mathlingua.frontend.textalk.TupleNode) {
            return
        }

        val leftTuple = lhsItem.children[0] as mathlingua.frontend.textalk.TupleNode
        val lhsNames =
            leftTuple
                .params
                .items
                .filter { it.children.size == 1 && it.children[0] is TextTexTalkNode }
                .map { it.children[0] as TextTexTalkNode }
                .map { it.text }
        if (lhsNames.isEmpty()) {
            return
        }

        val rhsItem = rhsItems[0]
        if (rhsItem.children.size != 1 || rhsItem.children[0] !is Command) {
            return
        }
        val rhsCommand = rhsItem.children[0] as Command
        val rhsSignature = rhsCommand.signature()

        var rhsMatchedDefines: DefinesGroup? = null
        for (defVal in definesGroups) {
            val def = defVal.value.original
            if (def.signature?.form == rhsSignature) {
                rhsMatchedDefines = def
                break
            }
        }

        if (rhsMatchedDefines == null) {
            return
        }

        var rhsNames: List<String>? = null
        val rhsTargets = rhsMatchedDefines.definesSection.targets
        if (rhsTargets.size == 1) {
            val singleTarget = rhsTargets[0]
            var tupleTarget: Tuple? = null
            if (singleTarget is AssignmentNode && singleTarget.assignment.rhs is Tuple) {
                tupleTarget = singleTarget.assignment.rhs
            } else if (singleTarget is TupleNode) {
                tupleTarget = singleTarget.tuple
            }

            if (tupleTarget != null) {
                rhsNames =
                    tupleTarget.items
                        .filter {
                            it is Abstraction &&
                                !it.isEnclosed &&
                                !it.isVarArgs &&
                                it.parts.size == 1 &&
                                it.parts[0].params == null &&
                                it.parts[0].tail == null &&
                                it.parts[0].subParams == null
                        }
                        .map { (it as Abstraction).parts[0].name.text }
            }
        }

        if (rhsNames == null) {
            return
        }

        if (lhsNames.size != rhsNames.size) {
            return
        }

        val toFromNameMap = mutableMapOf<String, String>()
        for (i in lhsNames.indices) {
            toFromNameMap[rhsNames[i]] = lhsNames[i]
        }

        val fromNameToColonEqualsMap = mutableMapOf<String, ColonEqualsTexTalkNode>()

        fun maybeIdentifyFromNameToColonEquals(clause: Clause) {
            if (clause is Statement &&
                clause.texTalkRoot is ValidationSuccess &&
                clause.texTalkRoot.value.children.size == 1 &&
                clause.texTalkRoot.value.children[0] is ColonEqualsTexTalkNode) {
                val colonEqualsNode = clause.texTalkRoot.value.children[0] as ColonEqualsTexTalkNode
                if (colonEqualsNode.lhs.items.size == 1 &&
                    colonEqualsNode.lhs.items[0].children.size == 1 &&
                    colonEqualsNode.lhs.items[0].children[0] is OperatorTexTalkNode) {
                    val op = colonEqualsNode.lhs.items[0].children[0] as OperatorTexTalkNode
                    if (op.command is TextTexTalkNode) {
                        val opName = op.command.text
                        if (toFromNameMap.containsKey(opName)) {
                            val fromName = toFromNameMap[opName]!!
                            fromNameToColonEqualsMap[fromName] = colonEqualsNode
                        }
                    }
                }
            }
        }

        for (clause in rhsMatchedDefines.satisfyingSection?.clauses?.clauses ?: emptyList()) {
            maybeIdentifyFromNameToColonEquals(clause)
        }

        for (clause in rhsMatchedDefines.expressingSection?.clauses?.clauses ?: emptyList()) {
            maybeIdentifyFromNameToColonEquals(clause)
        }

        for ((fromName, colonEqualsNode) in fromNameToColonEqualsMap.entries) {
            val rhs = colonEqualsNode.rhs.items[0]
            val rhsSig =
                if (rhs.children.size == 1 && rhs.children[0] is Command) {
                    (rhs.children[0] as Command).signature()
                } else if (rhs.children.size == 1 &&
                    rhs.children[0] is OperatorTexTalkNode &&
                    (rhs.children[0] as OperatorTexTalkNode).command is Command) {
                    ((rhs.children[0] as OperatorTexTalkNode).command as Command).signature()
                } else if (rhs.children.size == 1 &&
                    rhs.children[0] is OperatorTexTalkNode &&
                    (rhs.children[0] as OperatorTexTalkNode).command is TextTexTalkNode) {
                    ((rhs.children[0] as OperatorTexTalkNode).command as TextTexTalkNode).text
                } else {
                    null
                }

            if (rhsSig == null) {
                continue
            }

            var rhsDef: DefinesGroup? = null
            for (grpVal in definesGroups) {
                if (grpVal.value.original.signature?.form == rhsSig) {
                    rhsDef = grpVal.value.original
                    break
                }
            }

            if (rhsDef == null) {
                continue
            }

            val lhs =
                colonEqualsNode.lhs.items[0].transform {
                    if (it is TextTexTalkNode) {
                        val thisFromName = toFromNameMap[it.text]
                        if (fromName == thisFromName) {
                            TextTexTalkNode(
                                type = TexTalkNodeType.Identifier,
                                tokenType = TexTalkTokenType.Identifier,
                                text = fromName,
                                isVarArg = false)
                        } else {
                            it
                        }
                    } else {
                        it
                    }
                } as ExpressionTexTalkNode
            val id =
                IdStatement(
                    text = lhs.toCode(),
                    texTalkRoot = ValidationSuccess(lhs),
                    row = -1,
                    column = -1)
            val syntheticDefines = rhsDef.copy(id = id)
            aliasDefines.add(syntheticDefines)
        }
    }

    private fun attemptAddAliasDefinesForColonEqualsOperator(
        colonEquals: ColonEqualsTexTalkNode, aliasDefines: MutableList<DefinesGroup>
    ) {
        val lhsItems = colonEquals.lhs.items
        val rhsItems = colonEquals.rhs.items
        if (lhsItems.size != 1 || rhsItems.size != 1) {
            println(
                "The left-hand-side and right-hand-side of a := must have exactly one expression")
            return
        }

        // Given the statment: '\f(x) := \g(x)'
        // then lhs is `\f(x)`
        // and lhsVars is the set containing only `x`
        val lhs = lhsItems[0]
        val lhsVars =
            lhs
                .getVarsTexTalkNode(
                    isInLhsOfColonEqualsIsOrIn = true,
                    groupScope = GroupScope.InNone,
                    isInIdStatement = false,
                    forceIsPlaceholder = false)
                .toSet()
                .map { it.name }

        // convert the right hand side from `\g(x)` to `\g(x?)`
        // to conform to the way "writtenAs" sections are written
        val rhs =
            rhsItems[0].transform {
                if (it is TextTexTalkNode &&
                    it.type == TexTalkNodeType.Identifier &&
                    it.tokenType == TexTalkTokenType.Identifier &&
                    lhsVars.contains(it.text)) {
                    it.copy(
                        // use toCode() so ... is printed if the identifier is
                        // vararg
                        text = "${it.toCode()}?")
                } else {
                    it
                }
            } as ExpressionTexTalkNode

        val stmtText = getExpandedText(rhs, aliasDefines)
        if (stmtText != null) {
            val id =
                IdStatement(
                    text = lhs.toCode(),
                    texTalkRoot = ValidationSuccess(lhs),
                    row = -1,
                    column = -1)
            val syntheticDefines =
                DefinesGroup(
                    signature = id.signature(),
                    id = id,
                    definesSection = DefinesSection(targets = emptyList(), row = -1, column = -1),
                    whereSection = null,
                    givenSection = null,
                    whenSection = null,
                    meansSection = null,
                    satisfyingSection =
                        SatisfyingSection(
                            clauses = ClauseListNode(clauses = emptyList(), row = -1, column = -1),
                            row = -1,
                            column = -1),
                    expressingSection = null,
                    providingSection = null,
                    usingSection = null,
                    writingSection = null,
                    writtenSection =
                        WrittenSection(forms = listOf("\"${stmtText}\""), row = -1, column = -1),
                    calledSection = CalledSection(forms = emptyList(), row = -1, column = -1),
                    metaDataSection = null,
                    row = -1,
                    column = -1)
            aliasDefines.add(syntheticDefines)
        }
    }

    override fun prettyPrint(
        node: Phase2Node, html: Boolean, literal: Boolean, doExpand: Boolean
    ): String {
        val aliasDefines = mutableListOf<DefinesGroup>()
        // any alias such as `x \op/ y := ...` or `\f(x) := ...`
        // needs to be handled so that when being pretty-printed, the pretty printer
        // acts as if there is a signature `x \op/ y` (for example) with a written as
        // section that is the expanded version of the right-hand-side of the :=
        // This allows alias in `using:` sections to have their usages expanded correctly
        if (node is HasUsingSection) {
            val usingSection = node.usingSection
            if (usingSection != null) {
                for (clause in usingSection.clauses.clauses) {
                    if (clause is Statement &&
                        clause.texTalkRoot is ValidationSuccess &&
                        clause.texTalkRoot.value.children.firstOrNull() is ColonEqualsTexTalkNode) {

                        val colonEquals =
                            clause.texTalkRoot.value.children.first() as ColonEqualsTexTalkNode
                        attemptAddAliasDefinesForColonEqualsOperator(colonEquals, aliasDefines)
                        attemptAddAliasDefinesForColonEqualsTuple(colonEquals, aliasDefines)
                    }
                }
            }
        }
        return getWriter(html, literal, doExpand, aliasDefines).generateCode(node)
    }

    override fun getSymbolErrors(): List<ValueAndSource<ParseError>> {
        val result = mutableListOf<ValueAndSource<ParseError>>()
        for (grp in allGroups) {
            val errs = grp.value.original.checkVarsPhase2Node(grp.value.normalized)
            result.addAll(errs.map { ValueAndSource(value = it, source = grp.source) })
        }
        return result
    }

    private fun getAllDefinedSignatures(): List<Pair<ValueAndSource<Signature>, TopLevelGroup>> {
        val result = mutableListOf<Pair<ValueAndSource<Signature>, Normalized<out TopLevelGroup>>>()

        fun processDefines(pair: ValueAndSource<Normalized<DefinesGroup>>) {
            val signature = pair.value.normalized.signature
            if (signature != null) {
                val vst = ValueAndSource(source = pair.source, value = signature)
                result.add(Pair(vst, pair.value))
            }
        }

        fun processStates(pair: ValueAndSource<Normalized<StatesGroup>>) {
            val signature = pair.value.normalized.signature
            if (signature != null) {
                result.add(
                    Pair(ValueAndSource(source = pair.source, value = signature), pair.value))
            }
        }

        fun processAxiom(pair: ValueAndSource<Normalized<AxiomGroup>>) {
            val signature = pair.value.normalized.signature
            if (signature != null) {
                result.add(
                    Pair(ValueAndSource(source = pair.source, value = signature), pair.value))
                result.add(
                    Pair(
                        ValueAndSource(
                            source = pair.source,
                            value = signature.copy(form = signature.form + ":given")),
                        pair.value))
            }
        }

        fun processTheorems(pair: ValueAndSource<Normalized<TheoremGroup>>) {
            val signature = pair.value.normalized.signature
            if (signature != null) {
                result.add(
                    Pair(ValueAndSource(source = pair.source, value = signature), pair.value))
                result.add(
                    Pair(
                        ValueAndSource(
                            source = pair.source,
                            value = signature.copy(form = signature.form + ":given")),
                        pair.value))
            }
        }

        fun processConjectures(pair: ValueAndSource<Normalized<ConjectureGroup>>) {
            val signature = pair.value.normalized.signature
            if (signature != null) {
                result.add(
                    Pair(ValueAndSource(source = pair.source, value = signature), pair.value))
                result.add(
                    Pair(
                        ValueAndSource(
                            source = pair.source,
                            value = signature.copy(form = signature.form + ":given")),
                        pair.value))
            }
        }

        for (pair in definesGroups) {
            processDefines(pair)
        }

        for (pair in statesGroups) {
            processStates(pair)
        }

        for (pair in axiomGroups) {
            processAxiom(pair)
        }

        for (pair in theoremGroups) {
            processTheorems(pair)
        }

        for (pair in conjectureGroups) {
            processConjectures(pair)
        }

        /*
         * For a Defines: with a generated: section, create synthetic
         * defines so that the constructors in the generated: section
         * can be resolved as valid signatures.
         */
        for (def in definesGroups) {
            val outerSig = def.value.original.signature ?: continue
            val satisfyingSection = def.value.original.satisfyingSection
            if (satisfyingSection != null) {
                for (clause in satisfyingSection.clauses.clauses) {
                    if (clause is GeneratedGroup) {
                        for (form in clause.generatedFromSection.forms) {
                            val innerSig =
                                "${outerSig.form}.${form.abstraction.toCode().replace(Regex("\\(.*?\\)"), "")}"
                            val vst =
                                ValueAndSource(
                                    source = def.source,
                                    value = Signature(innerSig, outerSig.location))
                            result.add(Pair(vst, def.value))
                        }
                    }
                }
            }
        }

        return result.map { Pair(first = it.first, second = it.second.normalized) }
    }

    override fun getInputOutputSymbolErrors(): List<ValueAndSource<ParseError>> {
        val errors = mutableListOf<ValueAndSource<ParseError>>()
        for (vst in allGroups) {
            val group = vst.value.normalized

            val inputs = group.getInputSymbols()
            val outputs = group.getOutputSymbols()

            val whenSection = group.getWhenSection()
            if (whenSection != null) {
                val usedSymbols =
                    whenSection
                        .findIsLhsSymbols()
                        .toMutableList()
                        .plus(whenSection.findColonEqualsLhsSymbols())
                for (pair in usedSymbols) {
                    val sym = pair.first
                    val location = pair.second
                    if (outputs.contains(sym)) {
                        errors.add(
                            ValueAndSource(
                                value =
                                    ParseError(
                                        message =
                                            "A `when:` section cannot describe a symbol introduced in a `Defines:` section but found '${sym}'",
                                        row = location.row,
                                        column = location.column),
                                source = vst.source))
                    }
                }
            }

            for (meansOrEval in group.getMeansExpressesSections()) {
                val usedSymbols =
                    meansOrEval
                        .findIsLhsSymbols()
                        .toMutableList()
                        .plus(meansOrEval.findColonEqualsLhsSymbols())
                for (pair in usedSymbols) {
                    val sym = pair.first
                    val location = pair.second
                    if (inputs.contains(sym) && !outputs.contains(sym)) {
                        errors.add(
                            ValueAndSource(
                                value =
                                    ParseError(
                                        message =
                                            "A `satisfying:` or `expressing:` section cannot describe a symbol introduced in a [...] or `given:` section but found '${sym}'",
                                        row = location.row,
                                        column = location.column),
                                source = vst.source))
                    }
                }
            }
        }
        return errors
    }

    private fun isSignatureTopLevel(exp: TexTalkNode, signature: String) =
        exp is ExpressionTexTalkNode &&
            exp.children.size == 1 &&
            exp.children[0] is Command &&
            (exp.children[0] as Command).signature() == signature

    override fun getNonExpressesUsedInNonIsNonInStatementsErrors():
        List<ValueAndSource<ParseError>> {
        val errors = mutableListOf<ValueAndSource<ParseError>>()
        val signaturesWithoutExpresses =
            getSignaturesWithoutExpressesSection().map { it.value.form }.toSet()
        val statesSigs = statesGroups.mapNotNull { it.value.original.signature?.form }.toSet()
        val axiomSigs = axiomGroups.mapNotNull { it.value.original.signature?.form }.toSet()
        for (vst in allGroups) {
            val group = vst.value.normalized

            for (stmtPair in group.getNonIsNonInStatementsNonInAsSections()) {
                val stmt = stmtPair.first
                val exp = stmtPair.second
                for (sig in exp.getSignaturesWithin()) {
                    if (signaturesWithoutExpresses.contains(sig) &&
                        !statesSigs.contains(sig) &&
                        // a top level axiom signature is allowed
                        !(axiomSigs.contains(sig) && isSignatureTopLevel(exp, sig))) {
                        val location = Location(stmt.row, stmt.column)
                        errors.add(
                            ValueAndSource(
                                value =
                                    ParseError(
                                        message =
                                            "Cannot use '$sig' in a non-`is` or non-`in` statement since its definition doesn't have an `expressing:` section",
                                        row = location.row,
                                        column = location.column),
                                source = vst.source))
                    }
                }
            }
        }
        return errors
    }

    override fun getUsedSignaturesAtRow(path: String, row: Int): List<ValueAndSource<Signature>> =
        when (val validation = sourceFiles[path]?.validation
        ) {
            is ValidationSuccess -> {
                val usedSignatures = validation.value.locateAllSignatures(ignoreLhsEqual = false)
                val signaturesAtRow =
                    usedSignatures.filter { it.location.row == row }.map { it.form }.toSet()

                getDefinedSignatures().filter { it.value.form in signaturesAtRow }
            }
            else -> emptyList()
        }
}

private fun <T> Boolean.thenUse(value: () -> List<T>) =
    if (this) {
        value()
    } else {
        emptyList()
    }
