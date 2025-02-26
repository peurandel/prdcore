package prd.peurandel.prdcore.Manager

import java.util.*

object PlayerDataCache {
    val cache: MutableMap<UUID, MutableMap<String, Any?>> = mutableMapOf()
}
