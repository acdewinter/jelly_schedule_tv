package dev.jellyschedule.tv

import dev.jellyschedule.tv.data.json.PluginJson
import kotlinx.serialization.KSerializer

/** Payloads captured from the plugin's DevHost (`tools/DevHost`) after seeding it like `tools/ui-test/ui-test.mjs` does. */
object Fixtures {
    fun text(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    fun <T> load(name: String, serializer: KSerializer<T>): T = PluginJson.decodeFromString(serializer, text(name))
}
