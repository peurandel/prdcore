package prd.peurandel.prdcore.Handler

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class buildingHandler(val plugin: JavaPlugin) {

    fun spawnArmory(player: Player, owner: String) {

        val location = player.location // Get the player's current location
        val world = player.world

        val ownerKey = NamespacedKey(plugin, "owner") // Key to identify this as an armory stand
        val typeKey = NamespacedKey(plugin, "type")     // Key to store the specific name/ID

        try {
            // Spawn the Armor Stand using world.spawn() with a configuration lambda
            val interaction = world.spawn(location, Interaction::class.java) { stand ->
                if (owner == "ad") stand.customName(Component.text("공용 무기고", NamedTextColor.YELLOW))
                else stand.customName(Component.text("무기고", NamedTextColor.YELLOW))
                stand.isCustomNameVisible = true
                stand.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, owner) // should be unique
                stand.persistentDataContainer.set(typeKey, PersistentDataType.STRING, "armory") // Store the unique name/ID you gave it

            }

            player.sendMessage("${ChatColor.GREEN}성공적으로 무기고를 소환하셨습니다!")

        } catch (e: Exception) {
            // Catch potential errors during spawning
            player.sendMessage("[]${ChatColor.RED}소환중에 문제가 발생했습니다! 어드민에게 제보해주세요!: ${e.message}")
            plugin.logger.severe("Error spawning armory stand for ${player.name} : ${e.stackTraceToString()}")
        }
    }

}