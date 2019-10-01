package com.pandulapeter.beagle.views.items.keyValue

import com.pandulapeter.beagle.views.items.DrawerItemViewModel
import java.util.UUID

internal data class KeyValueItemViewModel(
    private val pair: Pair<CharSequence, CharSequence>
) : DrawerItemViewModel {

    override val id = "keyValue_${UUID.randomUUID()}"
    val key = pair.first
    val value = pair.second
}