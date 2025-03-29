package prd.peurandel.prdcore.EventListner

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Handler.SkillHandler
import prd.peurandel.prdcore.Manager.SkillTriggerEvent

object SkillHandler {
    @SkillHandler(skillId = "repulsor")
/**
     * Executes the Repulsor skill for a player.
     *
     * @param plugin The JavaPlugin instance associated with the skill.
     * @param event The skill trigger event containing the player context.
     * Plays a firework blast sound and sends a message to the player when the skill is activated.
     */
        fun RepulsorSkill(plugin: JavaPlugin, event: SkillTriggerEvent) {
        val player = event.context as? Player
        player?.let {
            it.world.playSound(it.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f)
            it.sendMessage("앙 기모")
        }
    }
}