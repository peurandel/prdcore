package prd.peurandel.prdcore.ItemStack

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import kotlinx.serialization.json.Json
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Manager.ResearchEngine
import org.bson.Document
import org.bukkit.ChatColor
import java.util.ArrayList

class SuitItemHandler(private val plugin: JavaPlugin, private val database: MongoDatabase) {
    val serverCollection: MongoCollection<Document> = database.getCollection("server")
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    val engine = json.decodeFromString<ResearchEngine>(serverCollection.find(Filters.eq("name", "Engine")).first().toJson())

    /*
data class Engine(
    val name: String,
    val type: String,
    val lore: List<String>,
    val tier: Byte,
    val energy: Int,
    val item: String,
    val requireEx: Int,
    val requireResearch: String?
    )
     */
    fun createEngine(id: String, energy: Int, max_energy: Int): ItemStack? {

        val engineSet = if (engine.engine.find{it.type == id} != null ) engine.engine.find{it.type == id} else null
        if (engineSet == null) {
            return null
        }
        var energy = energy
        if(max_energy < energy) {
            energy = max_energy
        }
        val energyKey = NamespacedKey(plugin, "engine")
        val maxEnergyKey = NamespacedKey(plugin, "max_engine")
        val tierKey = NamespacedKey(plugin, "tier")
        val item: ItemStack = ItemSerialization.deserializeItemStack(engineSet.item)
        val meta = item.itemMeta

        meta.persistentDataContainer.set(NamespacedKey(plugin, "type"), PersistentDataType.STRING, "engine")
        meta.persistentDataContainer.set(energyKey, PersistentDataType.INTEGER, energy)
        meta.persistentDataContainer.set(tierKey, PersistentDataType.BYTE, engineSet.tier)
        meta.persistentDataContainer.set(maxEnergyKey, PersistentDataType.INTEGER, max_energy)
        meta.setDisplayName(engineSet.name)

        val lore: MutableList<String> = ArrayList()
        lore.add(ChatColor.AQUA.toString() + "Tier: " + engineSet.tier)
        lore.add(ChatColor.AQUA.toString() + "Max Energy: " + max_energy)
        lore.add(ChatColor.AQUA.toString() + "Energy: " + energy)
        meta.lore = lore

        item.itemMeta = meta
        return item
    }
    fun setEngineItem(item: ItemStack, tier: Int, energy: Int, max_energy: Int): ItemStack {

        if (tier == 0 && max_energy == 0) {
            return item
        }
        var energy = energy
        if(max_energy < energy) {
            energy = max_energy
        }


        val meta = item.itemMeta
        meta.persistentDataContainer.set(NamespacedKey(plugin, "tier"), PersistentDataType.INTEGER,tier)
        meta.persistentDataContainer.set(NamespacedKey(plugin, "max_engine"), PersistentDataType.INTEGER,max_energy)
        meta.persistentDataContainer.set(NamespacedKey(plugin, "engine"), PersistentDataType.INTEGER,energy)
        item.itemMeta = meta
        return item
    }
}