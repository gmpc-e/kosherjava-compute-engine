package com.elad.halacha.engine.compute

import com.kosherjava.zmanim.AstronomicalCalendar
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.ZmanimCalendar
import java.lang.reflect.Method
import java.util.*

data class ExternalMethod(
    val name: String,
    val owner: String // "AstronomicalCalendar" | "ZmanimCalendar" | "ComplexZmanimCalendar"
)

object MethodRegistry {
    private val targets: List<Pair<String, Class<*>>> = listOf(
        "ComplexZmanimCalendar" to ComplexZmanimCalendar::class.java,
        "ZmanimCalendar" to ZmanimCalendar::class.java,
        "AstronomicalCalendar" to AstronomicalCalendar::class.java
    )

    // Only zero-arg public methods that return java.util.Date and start with "get"
    val available: List<ExternalMethod> by lazy {
        targets.flatMap { (owner, clazz) ->
            clazz.methods
                .filter { m: Method ->
                    m.name.startsWith("get") &&
                            m.parameterCount == 0 &&
                            Date::class.java.isAssignableFrom(m.returnType)
                }
                .map { ExternalMethod(it.name, owner) }
        }.distinctBy { it.name } // de-dup if present in multiple classes
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun resolve(name: String): ExternalMethod? =
        available.firstOrNull { it.name.equals(name, ignoreCase = true) }
}