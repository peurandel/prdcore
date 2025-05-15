package prd.peurandel.prdcore.EventListner

import com.mongodb.client.MongoDatabase
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.buildings.armoryGUI
import prd.peurandel.prdcore.Manager.PlayerDataCache
import java.util.*

class playerInteractHandler(private val plugin: JavaPlugin,private val database: MongoDatabase) : Listener {
    private val typeKey = NamespacedKey(plugin, "type")
    private val ownerKey = NamespacedKey(plugin, "owner") // Assuming owner is common

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


    // --- Refactored function for ALL entity interactions with our PDC tag ---
    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val clickedEntity = event.rightClicked
        // 1. Check if the entity has our 'type' PDC tag at all
        val pdc = clickedEntity.persistentDataContainer
        if (!pdc.has(typeKey, PersistentDataType.STRING)) {
            return // Not one of our tagged entities, ignore
        }

        // 2. Get the specific type from the PDC tag
        val entityTypeString = pdc.get(typeKey, PersistentDataType.STRING)

        // 3. Decide what to do based on the type string
        when (entityTypeString) {
            "armory" -> handleArmoryInteraction(event, player, clickedEntity, pdc)
            "research_station" -> handleResearchInteraction(event, player, clickedEntity, pdc) // Example
            "skill_trainer" -> handleSkillTrainerInteraction(event, player, clickedEntity, pdc) // Example
            // Add more cases for other types as needed
            else -> {
                // Optional: Log or notify about an unknown tagged entity type
                plugin.logger.warning("Player ${player.name} interacted with an entity of unknown prdcore type: '$entityTypeString'")
            }
        }
    }

    // --- Specific handler function for "armory" type ---
    private fun handleArmoryInteraction(event: PlayerInteractEntityEvent, player: Player, entity: Entity, pdc: PersistentDataContainer) {
        // Optional: Armory doesn't have to be an ArmorStand.

        val owner = pdc.get(ownerKey, PersistentDataType.STRING) ?: "unknown"

        player.sendMessage(Component.text("You clicked on an armory owned by: ${if (owner == "ad") "public" else owner}", NamedTextColor.AQUA)) // Use Component

        /*
        val usingUserKey = NamespacedKey(plugin, "usingUserKey")
        var usingUser: Player? = null
        if(pdc.get(usingUserKey, PersistentDataType.STRING) == "null") {
            usingUser = Bukkit.getPlayer(UUID.fromString(pdc.get(usingUserKey, PersistentDataType.STRING)))
        }

        if(usingUser == null) armoryGUI(plugin,database).open(plugin,player)
        else player.sendMessage("${ChatColor.RED}이미 해당 무기고를 쓰고 있는 유저가 있습니다!")
        */
        armoryGUI(plugin,database).open(plugin,player)
        // Cancel the default interaction
        event.isCancelled = true
    }

    // --- Placeholder for another type ---
    private fun handleResearchInteraction(event: PlayerInteractEntityEvent, player: org.bukkit.entity.Player, entity: Entity, pdc: PersistentDataContainer) {
        player.sendMessage(Component.text("You clicked on a Research Station!", NamedTextColor.GREEN))
        // TODO: Add research station logic (open research GUI?)
        event.isCancelled = true
    }

    // --- Placeholder for another type ---
    private fun handleSkillTrainerInteraction(event: PlayerInteractEntityEvent, player: org.bukkit.entity.Player, entity: Entity, pdc: PersistentDataContainer) {
        player.sendMessage(Component.text("You clicked on a Skill Trainer!", NamedTextColor.GOLD))
        // TODO: Add skill trainer logic (open skill GUI?)
        event.isCancelled = true
    }

}