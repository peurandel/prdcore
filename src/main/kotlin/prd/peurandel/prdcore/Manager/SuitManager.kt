package prd.peurandel.prdcore.Manager

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import io.papermc.paper.command.brigadier.argument.ArgumentTypes.itemStack
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.ItemSerialization


class SuitManager(val plugin: JavaPlugin, database: MongoDatabase) {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    var database = database
    val userCollection = database.getCollection("users")

    fun getWardrobeIndex(playerDoc: Document, suitUUID: String): Int {
        val wardrobes = playerDoc.getList("wardrobe", Document::class.java) ?: emptyList()
        return wardrobes.indexOfFirst { it.getString("uuid") == suitUUID }
    }

    fun getWardrobeIndex(user: User, suitUUID: String): Int {
        val wardrobes = user.wardrobe
        return wardrobes.indexOfFirst { it.uuid == suitUUID }
    }

    fun loadSuit(playerDoc: Document, suitName: String): Document? {
        val wardrobes = playerDoc.getList("wardrobe", Document::class.java) ?: emptyList()
        return wardrobes.find { it.getString("name") == suitName }
    }
    fun loadUUIDSuit(playerDoc: Document, suitUUID: String): Document? {
        val wardrobes = playerDoc.getList("wardrobe", Document::class.java) ?: emptyList()
        return wardrobes.find { it.getString("uuid") == suitUUID }
    }
    fun setSuit(player: Player, suitOwnerUuid: String, suitDoc: Document) {
        val wardrobe: WardrobeItem = json.decodeFromString(suitDoc.toJson())

        val key = NamespacedKey(plugin, "suit")
        val key2 = NamespacedKey(plugin, "suit2")

        player.persistentDataContainer.set(key, PersistentDataType.STRING, "$suitOwnerUuid:${suitDoc.getString("uuid")}")
        player.persistentDataContainer.set(key2, PersistentDataType.STRING, json.encodeToString(wardrobe))

        //해쉬 넣기
        val suitData = mutableMapOf<String, Any?>().apply{
            put("suit",suitDoc)
        }
        PlayerDataCache.cache[player.uniqueId] = suitData
    }
    fun offSuit(player: Player) {
        val key = NamespacedKey(plugin, "suit")
        player.persistentDataContainer.remove(key)
        PlayerDataCache.cache[player.uniqueId]?.remove("suit")
        //unwearing armor
        player.inventory.helmet = ItemStack(Material.AIR)
        player.inventory.chestplate = ItemStack(Material.AIR)
        player.inventory.leggings = ItemStack(Material.AIR)
        player.inventory.boots = ItemStack(Material.AIR)
        BukkitRunnable(plugin,database).stopFlight(player)
    }

    fun saveSuit(player: Player, suitOwnerUUID: String, suitUUID: String) {
        val playerDoc = userCollection.find(Filters.eq("uuid", suitOwnerUUID)).first()


        if (playerDoc != null) {
            val wardrobes = (playerDoc.getList("wardrobe", Document::class.java) ?: emptyList()).toMutableList()
            val map: MutableMap<String, Any?> = PlayerDataCache.cache[player.uniqueId] ?: return

            val suitDoc = map["suit"] as? Document?
            if (suitDoc != null) {
                // wardrobes 리스트의 해당 문서 업데이트
                for (i in wardrobes.indices) {
                    if (wardrobes[i].getString("uuid") == suitUUID) {
                        wardrobes[i] = suitDoc
                        break
                    }
                }

                // 업데이트된 wardrobes 리스트를 playerDoc에 다시 설정
                playerDoc["wardrobe"] = wardrobes

                // playerDoc을 MongoDB에 업데이트
                userCollection.updateOne(
                    Filters.eq("uuid", player.uniqueId.toString()),
                    Document("\$set", playerDoc),
                    UpdateOptions().upsert(true)
                )
            }
        }
    }

    fun getSuitUUID(playerDoc: Document, suitName: String): String? {
        val wardrobes = playerDoc.getList("wardrobe", Document::class.java) ?: emptyList()
        for(wardrobeSet in wardrobes) {
            if(wardrobeSet.getString("name") == suitName) return wardrobeSet.getString("uuid")
        }
        return null
    }
    fun getWardrobe(suitOwnerUUID: String, suitUUID: String): Document? {
        val playerDoc = userCollection.find(Filters.eq("uuid", suitOwnerUUID)).first()
        val wardrobes = (playerDoc.getList("wardrobe", Document::class.java) ?: emptyList()).toMutableList()

        for(wardrobeSet in wardrobes) {
            if(wardrobeSet["uuid"] == suitUUID) {
                return wardrobeSet
            }
        }
        return null
    }
    fun getWardrobeItem(suitOwnerUUID: String, suitUUID: String): WardrobeItem? {
        val playerDoc = userCollection.find(Filters.eq("uuid", suitOwnerUUID)).first()

        val playerdata: User = json.decodeFromString(playerDoc!!.toJson())
        val wardrobes = playerdata.wardrobe
        for(wardrobeSet in wardrobes) {
            if(wardrobeSet.uuid == suitUUID) {
                return wardrobeSet
            }
        }
        return null
    }

    fun fillSuitDurability(suitOwnerUUID: String, suitUUID: String, value: Int) {


        val playerDoc = userCollection.find(Filters.eq("uuid", suitOwnerUUID)).first() as Document
        val wardrobeIndex = getWardrobeIndex(playerDoc,suitUUID)
        userCollection.updateOne(
            Filters.eq<String>("uuid", suitOwnerUUID),
            Updates.combine(
                Updates.set("wardrobe.${wardrobeIndex}.durability", value)
            )
        )
    }
    fun wearArmor(player: Player, suitOwnerUUID: String, suitUUID: String) {
        val suitDoc = getWardrobe(suitOwnerUUID, suitUUID) as Document

        val armorTypes = listOf("helmet", "chestplate", "leggings", "boots")
        val playerArmor = mapOf(
            "helmet" to player.inventory::setHelmet,
            "chestplate" to player.inventory::setChestplate,
            "leggings" to player.inventory::setLeggings,
            "boots" to player.inventory::setBoots
        )
        armorTypes.forEach { armorType ->
            val itemStack = if (suitDoc[armorType] != null) {
                ItemSerialization.deserializeItemStack(suitDoc[armorType].toString())
            } else {
                ItemStack(Material.AIR)
            }
            itemStack.itemMeta = setArmorMeta(itemStack,suitDoc)
            itemStack.addEnchantment(Enchantment.BINDING_CURSE,1)

            playerArmor[armorType]?.invoke(itemStack)
        }
    }
    fun wearArmor(player: Player, suitDoc: Document) {
        val armorTypes = listOf("helmet", "chestplate", "leggings", "boots")
        val playerArmor = mapOf(
            "helmet" to player.inventory::setHelmet,
            "chestplate" to player.inventory::setChestplate,
            "leggings" to player.inventory::setLeggings,
            "boots" to player.inventory::setBoots
        )

        armorTypes.forEach { armorType ->
            var itemStack = ItemStack(Material.AIR)

            if (suitDoc[armorType] != null) {
                itemStack = ItemSerialization.deserializeItemStack(suitDoc[armorType].toString())

                itemStack.itemMeta = setArmorMeta(itemStack,suitDoc)
                itemStack.addEnchantment(Enchantment.BINDING_CURSE,1)
            }

            playerArmor[armorType]?.invoke(itemStack)
        }
    }
    fun setArmorMeta(item: ItemStack, suitDoc: Document): ItemMeta {
        val meta = item.itemMeta
        val ArmorDoc = suitDoc["armor"] as Document
        meta.removeEnchantments()

        if(meta is Damageable) {
            //meta.damage(suitDoc.getInteger("duration") * 24 * (1.0+ ArmorDoc.getInteger("duration").toDouble() /100))
            meta.damage(100.0)

        }
        return meta
    }
}