package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button

/**
 * GUI for managing Suit Skill Sets.
 *
 * This GUI allows players to interact with and manage their suit skill sets.
 * It provides buttons for skill slots, GUI information, and navigation.
 *
 * @param plugin Instance of the JavaPlugin.
 * @param database MongoDB database instance.
 */
class skillGUI(plugin: JavaPlugin, database: MongoDatabase, suitUUID: String) : BaseGUI(plugin, "Suit Skill Set", 27) {

    val database = database
    val suitUUID = suitUUID
    companion object {
        const val SLOT_BUTTON_START_INDEX = 0
        const val SLOT_BUTTON_END_INDEX = 8
        const val FILLER_PANE_START_INDEX = 9
        const val FILLER_PANE_END_INDEX = 26
        const val BACK_BUTTON_SLOT_INDEX = 21
        const val INFO_BUTTON_SLOT_INDEX = 22

        private const val SLOT_KEY = "slot"

        private val SLOT_KEY_NAMESPACE = NamespacedKey("suit_skill_gui", SLOT_KEY)
    }

    /**
     * Initializes the items in the inventory for the skill GUI.
     *
     * This method sets up the buttons for skill slots, filler panes,
     * a back button, and an information button.
     *
     * @param plugin Instance of the JavaPlugin.
     * @param player Player's name or identifier.
     */
    override fun initializeItems(plugin: JavaPlugin, player: String) {
        // Skill slot buttons (slots 0-8)
        for (i in SLOT_BUTTON_START_INDEX..SLOT_BUTTON_END_INDEX) {
            inventory.setItem(i, createSlotButton(i.toByte()))
        }

        // Filler panes (slots 9-26)
        for (i in FILLER_PANE_START_INDEX..FILLER_PANE_END_INDEX) {
            inventory.setItem(i, createFillerPane())
        }

        // Go Back button (slot 21)
        inventory.setItem(BACK_BUTTON_SLOT_INDEX, createGoBackButton(plugin))

        // GUI Info button (slot 22)
        inventory.setItem(INFO_BUTTON_SLOT_INDEX, createGUIInfoButton(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString()))
    }

    /**
     * Handles inventory click events within the skill GUI.
     *
     * This method is called when a player clicks within the GUI inventory.
     * It currently sends a message indicating a click and cancels the event.
     *
     * @param event The InventoryClickEvent.
     */
    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true // Prevents taking items from the GUI
        val item = event.currentItem
        val buttonName = item?.let { ButtonName(it) }
        when (buttonName) {
            "slot" -> {
                val slotNum = item.itemMeta.persistentDataContainer.get(SLOT_KEY_NAMESPACE, PersistentDataType.BYTE)
                if (slotNum != null) {
                    skillSlotGUI(plugin,database,suitUUID,slotNum)
                } else {
                    event.whoClicked.sendMessage("${ChatColor.RED}비정상적인 접근입니다.")
                }
                // Handle slot button click action here
            }
            "close" -> {
                event.whoClicked.closeInventory()
                // Handle close button action here
            }
            "goback" -> {
                WardrobeGUI(plugin,database).open(plugin,event.whoClicked as Player)
            }
        }
    }


    fun ButtonName(item: ItemStack): String? {
        val meta = item.itemMeta
        val key = NamespacedKey(plugin,"button")
        val container = meta.persistentDataContainer
        return container.get(key, PersistentDataType.STRING)
    }
    /**
     * Creates an ItemStack for a skill slot button.
     *
     * This button is used to represent a skill slot in the GUI.
     *
     * @param slotNum The slot number (0-8).
     * @return ItemStack representing the slot button.
     */
    private fun createSlotButton(slotNum: Byte): ItemStack {
        val item = ItemStack(Material.STRUCTURE_VOID)
        val meta: ItemMeta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(item.type)!! // 안전한 ItemMeta 획득
        meta.persistentDataContainer.apply {
            set(NamespacedKey(plugin,"button"), PersistentDataType.STRING, "slot")
            set(SLOT_KEY_NAMESPACE, PersistentDataType.BYTE, slotNum)
        }
        meta.setDisplayName("${ChatColor.GREEN}Slot $slotNum")
        item.itemMeta = meta
        return item
    }

    /**
     * Creates an ItemStack for GUI information button (close button in this context).
     *
     * This button is used to display GUI information and close the GUI.
     *
     * @param uuid User UUID associated with the GUI.
     * @return ItemStack representing the GUI info/close button.
     */
    private fun createGUIInfoButton(uuid: String): ItemStack {
        val item: ItemStack = ItemStack(Material.BARRIER)
        val meta: ItemMeta = item.itemMeta
        val keyButton = NamespacedKey(plugin,"button")
        val keyUserUUID = NamespacedKey(plugin,"userUUID")
        val keySuitUUID = NamespacedKey(plugin,"suitUUID")
        meta.persistentDataContainer.apply {
            set(keyButton, PersistentDataType.STRING,"close")
            set(keyUserUUID, PersistentDataType.STRING,uuid)
            set(keySuitUUID, PersistentDataType.STRING,suitUUID)
        }
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }

    /**
     * Creates an ItemStack for filler glass panes.
     *
     * These panes are used to fill empty spaces in the GUI for visual clarity.
     *
     * @return ItemStack representing the filler pane.
     */
    private fun createFillerPane(): ItemStack {
        return ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
    }

    /**
     * Creates an ItemStack for "Go Back" button.
     *
     * This button is used to navigate back to the previous GUI.
     *
     * @param plugin Instance of the JavaPlugin.
     * @return ItemStack representing the Go Back button.
     */
    private fun createGoBackButton(plugin: JavaPlugin): ItemStack {
        return Button().GoBack(plugin, "To Wardrobe")
    }
}