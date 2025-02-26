package prd.peurandel.prdcore.EventListner

import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class playerInteractHandler {
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val action = event.action
        val itemInOffHand = player.inventory.itemInOffHand

    }
}