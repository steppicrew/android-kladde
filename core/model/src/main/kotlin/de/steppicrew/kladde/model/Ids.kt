package de.steppicrew.kladde.model

import java.util.UUID

/** All ids are random UUIDs made on the device; never counters, which would clash across devices. */
object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
