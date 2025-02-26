package prd.peurandel.prdcore.EventListner

import org.bson.Document
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import prd.peurandel.prdcore.Manager.PlayerDataCache

class DamageHandler: Listener {

    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        if(event.entity is Player) {
            PlayerDamageHandler(event)
        }
    }

    fun PlayerDamageHandler(event: EntityDamageEvent) {
        val player = event.entity as Player
        val key = player.uniqueId
        val map: MutableMap<String, Any?> = PlayerDataCache.cache[key] ?: return

        if(map["suit"]!=null) {
            PlayerSuitHandler(event,map)
        }
    }
    fun PlayerSuitHandler(event: EntityDamageEvent, map: MutableMap<String, Any?>) {


        val suit = map["suit"] as Document
        val damage = (event.damage / 4).toInt()
        val damageCause = event.cause
        val player = event.entity as Player
        val durability = suit["durability"] as Int
        suit.set("durability",if ((durability-damage) < 0) 0 else (durability-damage)) // 내구도 0미만으로 내려가는 것 방지

    }


}
