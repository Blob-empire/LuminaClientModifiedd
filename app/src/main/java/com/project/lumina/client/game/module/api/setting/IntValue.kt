/*
 * © Project Lumina 2026 — GPLv3 Licensed
 * You may use, modify, and share this code under the GPL.
 *
 * Just know: changing names and colors doesn't make you a developer.
 * Think before you fork. Build something real — or don't bother.
 */

package com.project.lumina.client.game.module.api.setting

import com.project.lumina.client.constructors.Configurable
import com.project.lumina.client.constructors.Element
import kotlin.reflect.KProperty

fun Element.intValue(
    name: String,
    defaultValue: Int,
    range: IntRange
): IntValueDelegate {
    return IntValueDelegate(this, name, defaultValue, range)
}

fun intValue(
    module: Configurable,
    name: String,
    defaultValue: Int,
    range: IntRange
): IntValueDelegate {
    return IntValueDelegate(module, name, defaultValue, range)
}

class IntValueDelegate(
    private val module: Configurable,
    private val name: String,
    private val defaultValue: Int,
    private val range: IntRange
) {
    private var currentValue: Int = defaultValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        return currentValue
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        currentValue = value.coerceIn(range)
    }
}
