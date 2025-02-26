package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button
import prd.peurandel.prdcore.ItemStack.ItemSerialization
import java.util.*

class ResearchEngineGUI(plugin: JavaPlugin, private var database: MongoDatabase) : BaseGUI(plugin,"Research Engine",54) {
    val playerCollection = database.getCollection("users")
    val slots = intArrayOf(
        0, 9, 18,
        27, 36, 37,
        38, 29, 20,
        11, 2, 3,
        4, 13, 22,
        31, 40, 41,
        42, 33, 24,
        15, 6, 7
    )
    override fun initializeItems(plugin: JavaPlugin, player: String) {
        val playerDoc: Document = playerCollection.find(Filters.eq("name",player)).first()
        val playerUUID = Bukkit.getOfflinePlayer(player).player?.uniqueId.toString()
        for(i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        val itemNames = arrayOf(
            "furnace", "improved_furnace", "advanced_furnace",
            "blast_furnace", "improved_blast_furnace", "advanced_blast_furnace",
            "diesel", "improved_diesel", "advanced_diesel",
            "basic_zet", "first_zet", "second_zet",
            "third_zet", "4th_zet", "basic_nuclear",
            "first_nuclear", "second_nuclear", "third_nuclear",
            "basic_fushion_reactor", "improved_fushion_reactor", "advanced_fushion_reactor",
            "first_fushion_reactor", "1_5th_fushion_reactor", "second_fushion_reactor"
        )

        for (i in itemNames.indices) {
            inventory.setItem(slots[i], getResearch(playerDoc, itemNames[i]))
        }

        inventory.setItem(48, Button().GoBack(plugin,"To Research Menu"))
        inventory.setItem(49,item)
    }


    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as Player
        val rawSlot = event.rawSlot
        if(isVaildSlot(event,rawSlot)) {
            val item = event.currentItem
            val buttonName = item?.let { ButtonName(it) }
            if (item != null && slots.contains(event.rawSlot)) {
                processEngineButton(player,item)
            }
            if (buttonName != null){
                processButton(player,buttonName)
            }
        }
    }

    fun getResearch(playerDoc: Document,name: String): ItemStack{

        val itemDoc = getItemDocument(name)

        val itemStack: ItemStack = ItemSerialization.deserializeItemStack(itemDoc.getString("item"))
        val itemMeta = itemStack.itemMeta

        if (itemMeta != null) {
            addEnchantments(itemMeta, isResearch(playerDoc,name))
            setItemMetaDetails(itemMeta, isResearch(playerDoc,name), itemDoc,name)
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            itemStack.setItemMeta(itemMeta)
        }
        return itemStack
    }
    private fun addEnchantments(itemMeta: ItemMeta, isResearch: Boolean) {
        if (isResearch) {
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true)
        }
    }

    private fun setItemMetaDetails(itemMeta: ItemMeta, isResearch: Boolean, itemDoc: Document,name: String) {
        val itemName = itemDoc.getString("name")
        val itemLore = itemDoc.getString("lore")

        val tier = itemDoc.getInteger("tier")
        val max_energy = itemDoc.getInteger("energy")
        val lore: MutableList<String> = ArrayList()
        lore.add(ChatColor.GRAY.toString() + itemLore)
        lore.add("${ChatColor.AQUA}Tier: $tier")
        lore.add(ChatColor.AQUA.toString() + "Energy: " + max_energy)
        if (!isResearch) {
            val requireEx = itemDoc.getInteger("require_ex")
            val requireEngine = itemDoc.getString("require_engine")

            lore.add(ChatColor.RED.toString() + "Require Exp: " + requireEx)
            lore.add(ChatColor.RED.toString() + "Require Engine: " + requireEngine)
        }
        itemMeta.setDisplayName(ChatColor.GREEN.toString() + itemName)
        itemMeta.lore = lore


        val key = NamespacedKey(plugin,"name")
        itemMeta.persistentDataContainer.set(key, PersistentDataType.STRING,name)
    }
    fun getItemDocument(name: String): Document {
        val serverDoc = database.getCollection("server")
        val item = serverDoc.find(Filters.eq("name","item")).first() as Document
        val itemContainer = item["engines"] as Document
        return itemContainer[name] as Document
    }

    fun isResearch(playerDoc: Document, name: String): Boolean{
        val researchDoc = playerDoc["research"] as Document
        val researchList: List<String> = researchDoc["engine"] as List<String>
        for(research in researchList) {
            if(Objects.equals(research,name)) {
                return true
            }
        }
        return false
    }
    fun processButton(player : Player, buttonName : String) {
        when(buttonName) {
            "goback" -> {ResearchGUI(plugin,database).open(plugin,player)}
            "close" -> {player.closeInventory()}
        }
    }
    fun ButtonName(item: ItemStack): String? {
        val meta = item.itemMeta
        val key = NamespacedKey(plugin,"button")
        val container = meta.persistentDataContainer
        return container.get(key, PersistentDataType.STRING)
    }
    fun isVaildSlot(event: InventoryClickEvent, rawSlot: Int): Boolean {
        return rawSlot < event.inventory.size
    }

    fun GUIInfo(uuid: String) : ItemStack {
        val item: ItemStack = ItemStack(Material.BARRIER)
        val meta: ItemMeta = item.itemMeta
        val keyButton = NamespacedKey(plugin,"button")
        val keyUserUUID = NamespacedKey(plugin,"userUUID")
        meta.persistentDataContainer.set(keyButton, PersistentDataType.STRING,"close")
        meta.persistentDataContainer.set(keyUserUUID, PersistentDataType.STRING,uuid)
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }

    fun processEngineButton(player : Player,item: ItemStack) {
        val playerDoc: Document = playerCollection.find(Filters.eq("name",player.name)).first()
        val key = NamespacedKey(plugin,"name")
        val itemName = item.itemMeta.persistentDataContainer.get(key, PersistentDataType.STRING) as String
        val researchDoc = playerDoc["research"] as Document
        val researchList: List<String> = researchDoc["engine"] as List<String>
        val isResearch = isResearch(playerDoc,itemName)

        if(isResearch) {
            return
        }
        val itemDoc = getItemDocument(itemName)

        val require_ex = itemDoc.getInteger("require_ex")
        val require_engine = itemDoc.getString("require_engine")

        var exp = playerDoc["research_point"] as Int

        if(require_ex <= exp && researchList.contains(require_engine)) {
            exp -= require_ex

            val AddedResearchList = researchList.toMutableList()
            AddedResearchList.add(itemName)
            playerCollection.updateOne(
                Filters.eq<String>("name", player.name),
                Updates.combine(
                    Updates.set("research.engine", AddedResearchList),
                    Updates.set("research_point", exp)
                )
            )
            open(plugin,player)

        } else {
            player.sendMessage("조건 만족 안됨 ㅡㅡ")
        }

    }
}