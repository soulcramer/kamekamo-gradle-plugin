package kamekamo.gradle.util

/**
 * Merges two maps whose values are also maps. Unlike [Map.plus], the value maps will themselves be
 * merged.
 */
internal fun <A, B, C> Map<A, Map<B, C>>.deepMerge(map: Map<A, Map<B, C>>): Map<A, Map<B, C>> {
    return toMutableMap().apply {
        map.forEach { (key, value) ->
            this.merge(key, value) { originalValueMap, newValueMap -> originalValueMap + newValueMap }
        }
    }
}