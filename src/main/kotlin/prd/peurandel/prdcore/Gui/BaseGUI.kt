package prd.peurandel.prdcore.Gui

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin

abstract class BaseGUI(open val plugin: JavaPlugin, val title: String, val size: Int) {
    protected val inventory: Inventory = Bukkit.createInventory(null,size,title)

    abstract fun initializeItems(plugin: JavaPlugin,player: String)

    fun open(plugin: JavaPlugin, player: Player) {
        initializeItems(plugin,player.name)
        player.openInventory(inventory)
    }

    abstract fun onInventoryClick(event: InventoryClickEvent)
}