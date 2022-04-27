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

package mathlingua.cli

import java.io.File
import kotlin.system.exitProcess
import mathlingua.lib.newMlg

fun main(args: Array<String>) {
    try {
        if (args.isEmpty()) {
            throw CliException("Expected a command")
        }

        val mlg = newMlg()
        val subcommand = args[0]
        val subArgs = args.sliceArray(1 until args.size)

        when (subcommand) {
            "check" -> {
                val parseResult = parseArgs(subArgs, mapOf(), null)
                mlg.check(parseResult.positional.map { File(it) })
            }
            "edit" -> {
                val parseResult = parseArgs(subArgs, mapOf("--no-open" to 0, "--port" to 1), 0)
                val noOpen = "no-open" in parseResult.named
                val portValues = parseResult.named["port"]
                val port =
                    if (portValues != null) {
                        portValues.first().toIntOrNull()
                            ?: throw CliException("--port requires an integer argument")
                    } else {
                        8080
                    }
                mlg.edit(noOpen, port)
            }
            "doc" -> {
                parseArgs(subArgs, mapOf(), 0)
                mlg.doc()
            }
            "clean" -> {
                parseArgs(subArgs, mapOf(), 0)
                mlg.clean()
            }
            "version" -> {
                parseArgs(subArgs, mapOf(), 0)
                mlg.version()
            }
            "help" -> {
                parseArgs(subArgs, mapOf(), 0)
                println(HELP)
            }
        }
    } catch (e: CliException) {
        System.err.println("ERROR: ${e.message}\n\n$HELP")
        exitProcess(1)
    }
}

private val HELP =
    """
Usage: mlg COMMAND [ARGS]...

Commands:
  check     Check the MathLingua files for errors
  clean     Delete generated HTML files
  document  Generate a read-only web app in the 'docs' directory for exploring
            the MathLingua content
  edit      Start a web app on the specified port (defaults to 8080) that
            allows the MathLingua files to be viewed as well as edited. Open
            localhost:<port> (i.e. localhost:8080) in your web browser to
            access the app.
  help      Show this message and exit
  version   Print the MathLingua version
""".trimIndent()
