package prd.peurandel.prdcore.EventListner

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import prd.peurandel.prdcore.Gui.BaseGUI

class InventoryClickHandler : Listener {
    private val guiMap: MutableMap<String, BaseGUI> = mutableMapOf()

    fun registerGUI(gui: BaseGUI) {
        guiMap[gui.title] = gui
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val inventory = event.view
        val title = inventory.title

        guiMap[title]?.onInventoryClick(event)
    }
}
