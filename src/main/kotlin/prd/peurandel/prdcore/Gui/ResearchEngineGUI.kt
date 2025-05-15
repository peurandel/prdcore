package prd.peurandel.prdcore.Gui

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.serialization.json.Json
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
import prd.peurandel.prdcore.Manager.Engine
import prd.peurandel.prdcore.Manager.ResearchEngine
import prd.peurandel.prdcore.Manager.User
import java.util.*

class ResearchEngineGUI(plugin: JavaPlugin, private var database: MongoDatabase) : BaseGUI(plugin,"Research Engine",54) {
    val playerCollection = database.getCollection("users")
    val serverCollection = database.getCollection("server")
    val engine = Json.decodeFromString<ResearchEngine>(serverCollection.find(Filters.eq("name", "Engine")).first().toJson())
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
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player)).first().toJson())

        for(i in 0..53) {
            inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        for (i in engine.engine.indices) {
            inventory.setItem(slots[i], getResearch(user, i))
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

    fun getResearch(user: User,i: Int): ItemStack{

        val engineSet = engine.engine[i]
        val itemStack: ItemStack = ItemSerialization.deserializeItemStack(engineSet.item)
        val itemMeta = itemStack.itemMeta

        if (itemMeta != null) {
            addEnchantments(itemMeta, isResearch(user,engineSet.type))
            setItemMetaDetails(itemMeta, isResearch(user,engineSet.type), engineSet)
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

    private fun setItemMetaDetails(itemMeta: ItemMeta, isResearch: Boolean, engineSet: Engine) {

        val lore: MutableList<String> = engineSet.lore as MutableList<String>
        lore.add("${ChatColor.AQUA}Tier: ${engineSet.tier}")
        lore.add(ChatColor.AQUA.toString() + "Energy: " + engineSet.energy)
        if (!isResearch) {
            lore.add(ChatColor.RED.toString() + "Require Exp: " + engineSet.requireEx)
            lore.add(ChatColor.RED.toString() + "Require Engine: " + engineSet.requireResearch)
        }
        itemMeta.setDisplayName(ChatColor.GREEN.toString() + engineSet.name)
        itemMeta.lore = lore


        val key = NamespacedKey(plugin,"name")
        itemMeta.persistentDataContainer.set(key, PersistentDataType.STRING,engineSet.name)
    }

    fun isResearch(user: User, type: String): Boolean{
        val researchList: List<Engine> = user.research.engine
        for(research in researchList) {
            if(research.type == type) {
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
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player.name)).first().toJson())
        val key = NamespacedKey(plugin,"name")
        val itemName = item.itemMeta.persistentDataContainer.get(key, PersistentDataType.STRING) as String
        val researchList: MutableList<Engine> = user.research.engine.toMutableList()
        val isResearch = isResearch(user,itemName)

        if(isResearch) {
            return
        }
        val engineSet = engine.engine.find { it.name == itemName } ?: return

        val require_ex = engineSet.requireEx

        var exp = user.research_point

        if(require_ex <= exp && researchList.find { it.type == engineSet.requireResearch } != null ) {
            exp -= require_ex

            researchList.add(engineSet)
            val serializedList = researchList.map {
                Document.parse(Json.encodeToString(it))
            }

            playerCollection.updateOne(
                Filters.eq<String>("name", player.name),
                Updates.combine(
                    Updates.set("research.engine", serializedList),
                    Updates.set("research_point", exp)
                )
            )
            open(plugin,player)

        } else {
            player.sendMessage("${ChatColor.RED}연구 점수가 부족하거나 이미 연구되었습니다.")
        }
    }
}