package prd.peurandel.prdcore.Manager

import org.bukkit.Material
import java.util.*

object PlayerDataCache {
    val cache: MutableMap<UUID, MutableMap<String, Any?>> = mutableMapOf()
}

object itemInfoMap {
    val cache: MutableMap<Material, ItemInfo> = mutableMapOf()
}
