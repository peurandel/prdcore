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
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.Button
import prd.peurandel.prdcore.ItemStack.ItemSerialization
import java.util.ArrayList

class ModuleEngineGUI(plugin: JavaPlugin, database: MongoDatabase, suitUUID: String) : BaseGUI(plugin,"Module Engine",54) {
    private val database = database
    private val suitUUID = suitUUID
    val playerCollection = database.getCollection("users")
    val serverCollection = database.getCollection("server")

    override fun initializeItems(plugin: JavaPlugin, player: String) {
        val playerDoc: Document = playerCollection.find(Filters.eq("name",player)).first()

        val itemDoc: Document = serverCollection.find(Filters.eq("name","item")).first()
        val engineDoc: Document = itemDoc["engines"] as Document
        val researchDoc = playerDoc["research"] as Document
        val researchList: List<String> = researchDoc["engine"] as List<String>
        for(i in 0..53) {
            if(i < researchList.size) {

                inventory.setItem(i,getEngineItem(playerDoc,engineDoc,researchList,i))

            } else {
                inventory.setItem(i, ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE))

            }
        }

        val item: ItemStack = GUIInfo(Bukkit.getOfflinePlayer(player).player?.uniqueId.toString())

        inventory.setItem(48, Button().GoBack(plugin,"To Module Menu"))
        inventory.setItem(49,item)
    }

    override fun onInventoryClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val rawSlot = event.rawSlot
        if(isVaildSlot(event,rawSlot)) {
            val item = event.currentItem
            val buttonName = item?.let { ButtonName(it) }
            val player = event.whoClicked as Player

            val playerDoc: Document = playerCollection.find(Filters.eq("name",player.name)).first()

            val itemDoc: Document = serverCollection.find(Filters.eq("name","item")).first()
            val engineDoc: Document = itemDoc["engines"] as Document

            val researchDoc = playerDoc["research"] as Document
            val researchList: List<String> = researchDoc["engine"] as List<String>
            SelectEngine(event,player, playerDoc, item as ItemStack, engineDoc, rawSlot, researchList)
            if (buttonName != null){
                processButton(event,player,buttonName)
            }
        }
    }

    fun processButton(event: InventoryClickEvent, player : Player, buttonName : String) {
        when(buttonName) {

            "goback" -> {ModuleGUI(plugin,database,getSuitUUID(event)).open(plugin,player)}
            "close" -> {player.closeInventory()}
        }
    }
    fun getSuitUUID(event: InventoryClickEvent): String {
        val keySuitUUID = NamespacedKey(plugin,"suitUUID")
        val suitUUID = getInfoItem(event)?.itemMeta?.persistentDataContainer?.get(keySuitUUID,PersistentDataType.STRING) as String
        return suitUUID
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
        val keySuitUUID = NamespacedKey(plugin,"suitUUID")
        meta.persistentDataContainer.set(keyButton, PersistentDataType.STRING,"close")
        meta.persistentDataContainer.set(keyUserUUID, PersistentDataType.STRING,uuid)
        meta.persistentDataContainer.set(keySuitUUID, PersistentDataType.STRING,suitUUID)
        meta.setDisplayName("${ChatColor.RED}Close")
        item.itemMeta = meta
        return item
    }

    fun getInfoItem(event: InventoryClickEvent): ItemStack? {
        return event.inventory.getItem(49)
    }
    fun getEngineItem(playerDoc:Document, engineDoc: Document, researchList: List<String>,i: Int): ItemStack? {
        val engine: Document = engineDoc[researchList.get(i)] as Document
        val engineitem = engine.getString("item")
        val engineName = engine.getString("name")

        val tier = engine.getInteger("tier")
        val max_energy = engine.getInteger("energy")
        val item: ItemStack = ItemSerialization.deserializeItemStack(engineitem)

        //아이템 정보 정의
        val meta = item.itemMeta
        meta.setDisplayName(engineName)
        val lore: MutableList<String> = ArrayList()
        lore.add(ChatColor.AQUA.toString() + "Tier: " + tier)
        lore.add(ChatColor.AQUA.toString() + "Energy: " + max_energy)
        meta.lore = lore

        if(isSelected(playerDoc,engineName)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        }

        item.itemMeta = meta
        return item
    }
    fun isSelected(playerDoc: Document, engineName: String): Boolean {
        val wardrobes = playerDoc["wardrobe"] as? List<Document> ?: return false

        for (wardrobeSet in wardrobes) {
            if (wardrobeSet.getString("uuid") == suitUUID) {
                return wardrobeSet.getString("engine") == engineName
            }
        }
        return false
    }

    fun SelectEngine(event: InventoryClickEvent, player: Player,playerDoc: Document,item: ItemStack,engineDoc: Document,rawSlot: Int,researchList: List<String>) {
        val wardrobes = playerDoc["wardrobe"] as List<Document>
        //슈트 이름 인젝션 방지
        if(rawSlot+1 > researchList.size) {
            return
        }
        val engine: Document = engineDoc[researchList.get(rawSlot)] as Document
        val engineName = engine.getString("name")
        val tier = engine.getInteger("tier")
        val max_energy = engine.getInteger("energy")

        for(i in wardrobes.indices) {

            val wardrobeSet = wardrobes[i]
            if(wardrobeSet.getString("uuid") == getSuitUUID(event)) {
                val suitIndex = i
                playerCollection.updateOne(
                    Filters.eq<String>("name", player.name),
                    Updates.combine(
                        Updates.set("wardrobe.${suitIndex}.engine",engineName),
                        Updates.set("wardrobe.${suitIndex}.tier", tier),
                        Updates.set("wardrobe.${suitIndex}.max_energy", max_energy)
                    )
                )
                // 새로 GUI를 오픈해버리면 suitUUID를 소실

                ModuleGUI(plugin,database,getSuitUUID(event)).open(plugin,player)

            }
        }
    }
}