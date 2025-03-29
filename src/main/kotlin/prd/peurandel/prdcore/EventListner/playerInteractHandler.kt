package prd.peurandel.prdcore.EventListner

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import prd.peurandel.prdcore.Manager.PlayerDataCache

class playerInteractHandler : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val action = event.action
        val itemInOffHand = player.inventory.itemInOffHand
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if(itemInOffHand.getData(DataComponentTypes.ITEM_NAME) == Component.text("컨트롤러")) {
                val map: MutableMap<String, Any?> = PlayerDataCache.cache[player.uniqueId] ?: return player.sendMessage("맵설정에 문제가 발생했습니다.")
                map["right_clicked"] = true
            }
        }
    }
}