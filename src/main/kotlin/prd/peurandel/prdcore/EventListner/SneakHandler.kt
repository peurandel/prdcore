package prd.peurandel.prdcore.EventListner

import com.mongodb.client.MongoDatabase
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Manager.BukkitRunnable
import prd.peurandel.prdcore.Manager.PlayerDataCache

class SneakHandler(plugin: JavaPlugin, database: MongoDatabase): Listener {


    val bukkitRunnable = BukkitRunnable(plugin,database)
    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val playerUUID = player.uniqueId
        val map: MutableMap<String, Any?> = PlayerDataCache.cache[playerUUID] ?: return
        if(map["suit"] != null && map["onFlightMode"] == true) {
            if(!player.isOnGround && map["isFlight"] != true)
                bukkitRunnable.startFlight(player,map)
        }
    }
}