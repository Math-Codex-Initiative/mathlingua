/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mathlingua.frontend.support

import mathlingua.frontend.chalktalk.phase2.ast.common.Phase2Node

data class Location(val row: Int, val column: Int)

interface LocationTracker {
    fun hasLocationOf(node: Phase2Node): Boolean
    fun getLocationOf(node: Phase2Node): Location?
}

interface MutableLocationTracker : LocationTracker {
    fun setLocationOf(node: Phase2Node, location: Location)
}

internal fun newLocationTracker(): MutableLocationTracker {
    return MutableLocationTrackerImpl()
}

//////////////////////////////////////////////////////////////////////////////////

private class MutableLocationTrackerImpl : MutableLocationTracker {
    private val map: MutableMap<Int, Location> = mutableMapOf()

    override fun hasLocationOf(node: Phase2Node) = map.containsKey(node.id())

    override fun getLocationOf(node: Phase2Node) = map[node.id()]

    override fun setLocationOf(node: Phase2Node, location: Location) {
        map[node.id()] = location
    }

    private fun Phase2Node.id() = System.identityHashCode(this)
}
