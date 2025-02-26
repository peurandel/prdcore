package prd.peurandel.prdcore.Handler

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerHandler : Listener {
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
    }
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        
    }
}