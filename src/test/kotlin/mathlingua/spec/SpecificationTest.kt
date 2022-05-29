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

package mathlingua.spec

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isNotIn
import assertk.assertions.isTrue
import kotlin.test.Test
import org.reflections.Reflections
import org.reflections.scanners.Scanners

class SpecificationTest {
    @Test
    fun `verify spec has no duplicate definitions`() {
        val usedNames = mutableSetOf<String>()
        for (spec in MATHLINGUA_SPECIFICATION) {
            assertThat(spec.name).isNotIn(usedNames)
            usedNames.add(spec.name)
        }
    }

    @Test
    fun `verify all definitions in spec are in code`() {
        val typesInCode = getAllTypesInCode().toSet()
        val typesInSpec = getAllTypesInSpec()
        for (type in typesInSpec) {
            assertThat(type).isIn(typesInCode)
        }
    }

    @Test
    fun `verify all types in code are specified in the spec`() {
        val typesInSpec = getAllTypesInSpec().toSet()
        val typesInCode = getAllTypesInCode()
        for (type in typesInCode) {
            assertThat(type).isIn(typesInSpec)
        }
    }

    @Test
    fun `verify all any-of types in spec align with interfaces in code`() {
        val anyOfTypesInSpec = MATHLINGUA_SPECIFICATION.filter { it.of is AnyOf }
        for (type in anyOfTypesInSpec) {
            val classname = type.getClassname()
            assertThat(isInterface(classname), "$type is an interface").isTrue()
            val implementors = getDirectAstImplementorsOf(classname)
            val anyOf = type.of as AnyOf
            assertThat(implementors, "implementors of $classname")
                .isEqualTo(anyOf.of.map { it.toCode().addAstPackagePrefix() }.toSet())
        }
    }

    @Test
    fun `verify all interfaces in code align with any-of types in spec`() {
        val allInterfacesInCode = getAllTypesInCode().filter { isInterface(it) }.sorted()
        val anyOfTypesInSpec =
            MATHLINGUA_SPECIFICATION.filter { it.of is AnyOf }.map { it.getClassname() }.sorted()
        assertThat(allInterfacesInCode).isEqualTo(anyOfTypesInSpec)
    }

    @Test
    fun `verify all groups match the spec`() {
        // TODO: implement this test that verifies that all groups have the correct sections
    }
}

private const val AST_PACKAGE = "mathlingua.lib.frontend.ast"

/**
 * Adds the ast package prefix for a simple classname and does nothing for a fully qualified
 * classname
 */
private fun String.addAstPackagePrefix() =
    if (this.startsWith(AST_PACKAGE)) {
        this
    } else {
        "${AST_PACKAGE}.${this}"
    }

/**
 * Removes the ast package prefix from the given fully qualified classname and does nothing for a
 * simple classname.
 */
private fun String.removeAstPackagePrefix() =
    if (this.startsWith(AST_PACKAGE)) {
        // an additional character is removed to account
        // for the period after the package prefix
        this.substring(AST_PACKAGE.length + 1)
    } else {
        this
    }

/** Returns the fully qualified name of all the classes and interfaces in the ast package. */
private fun getAllTypesInCode(): List<String> {
    val reflections = Reflections(AST_PACKAGE, Scanners.SubTypes.filterResultsBy { true })
    return reflections.getAll(Scanners.SubTypes).filter {
        // only find mathlingua types
        it.contains(AST_PACKAGE) &&
            // ignore types associated with tests (tests make a class
            // for each test of the form <test-class>$<test-name>)
            !it.contains("$") &&
            it != "mathlingua.lib.frontend.ast.ChalkTalkNode" &&
            it != "mathlingua.lib.frontend.ast.TexTalkNode" &&
            it != "mathlingua.lib.frontend.ast.CommonNode" &&
            it != "mathlingua.lib.frontend.ast.NodeLexerToken" &&
            it != "mathlingua.lib.frontend.ast.HasMetaData"
    }
}

private fun DefinitionOf.getClassname() =
    if (this.of is Group) {
            this.of.classname
        } else {
            this.name
        }
        .addAstPackagePrefix()

private fun getAllTypesInSpec() = MATHLINGUA_SPECIFICATION.map { it.getClassname() }

private fun isInterface(classname: String) =
    try {
        ClassLoader.getSystemClassLoader().loadClass(classname).isInterface
    } catch (e: ClassNotFoundException) {
        false
    }

private fun getDirectAstImplementorsOf(classname: String): Set<String> {
    val allTypes = getAllTypesInCode()
    val loader = ClassLoader.getSystemClassLoader()
    return allTypes
        .filter {
            loader.loadClass(it).interfaces.map { intf -> intf.name }.toSet().contains(classname)
        }
        .toSet()
}
