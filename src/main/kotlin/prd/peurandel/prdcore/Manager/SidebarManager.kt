package prd.peurandel.prdcore.Manager
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.ScoreboardManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SidebarManager(database: MongoDatabase) {

    private val scoreboardManager: ScoreboardManager = Bukkit.getScoreboardManager()
    private val sidebarMap: MutableMap<Player, Scoreboard> = mutableMapOf()
    val database = database
    fun createSidebar(player: Player) {
        val scoreboard: Scoreboard = scoreboardManager.newScoreboard
        val objective: Objective = scoreboard.registerNewObjective("simpleSidebar", "dummy", Component.text("${ChatColor.AQUA}${ChatColor.BOLD}SUIT"))
        objective.displaySlot = DisplaySlot.SIDEBAR

        // Add lines to the sidebar
        updateSidebar(player, scoreboard,getMoneyValue(player),0,getResearchValue(player))

        // Set the scoreboard for the player
        player.scoreboard = scoreboard
        sidebarMap[player] = scoreboard
    }
    fun updateSidebar(player: Player, scoreboard: Scoreboard? = null, money: Int, cash: Int, research: Int) {
        val currentScoreboard = scoreboard ?: sidebarMap[player] ?: return
        val objective = currentScoreboard.getObjective("simpleSidebar") ?: return

        // Clear existing entries and teams
        currentScoreboard.entries.forEach { currentScoreboard.resetScores(it) }

        // Remove existing teams if they exist
        currentScoreboard.getTeam("money")?.unregister()
        currentScoreboard.getTeam("cash")?.unregister()
        currentScoreboard.getTeam("blank")?.unregister()
        currentScoreboard.getTeam("research")?.unregister()

        // Money
        val moneyTeam = currentScoreboard.registerNewTeam("money")
        moneyTeam.addEntry("${ChatColor.GOLD}Purse: ")
        moneyTeam.suffix(Component.text("$money"))
        objective.getScore("${ChatColor.GOLD}Purse: ").score = 4

        // CASH
        val cashTeam = currentScoreboard.registerNewTeam("cash")
        cashTeam.addEntry("${ChatColor.AQUA}캐시: ")
        cashTeam.suffix(Component.text("$cash"))
        objective.getScore("${ChatColor.AQUA}캐시: ").score = 3

        // 공백
        val gongTeam = currentScoreboard.registerNewTeam("blank")
        gongTeam.addEntry("${ChatColor.RESET}")
        objective.getScore("${ChatColor.RESET}").score = 2

        // RESEARCH
        val researchTeam = currentScoreboard.registerNewTeam("research")
        researchTeam.addEntry("${ChatColor.GRAY}연구 점수: ")
        researchTeam.suffix(Component.text("$research"))
        objective.getScore("${ChatColor.GRAY}연구 점수: ").score = 1
    }

    fun removeSidebar(player: Player) {
        val scoreboard = sidebarMap.remove(player) ?: return
        scoreboard.clearSlot(DisplaySlot.SIDEBAR)
        player.scoreboard = scoreboardManager.mainScoreboard // Reset to the main scoreboard
    }


    private fun getMoneyValue(player: Player): Int {
        val playerCollection = database.getCollection("users")
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player.name)).first().toJson())
        return user.money
    }

    private fun getResearchValue(player: Player): Int {
        val playerCollection = database.getCollection("users")
        val user = Json.decodeFromString<User>(playerCollection.find(Filters.eq("name",player.name)).first().toJson())
        return user.research_point
    }
    fun getPlayerScoreboard(player: Player): Scoreboard {
        // If player doesn't have a scoreboard yet, create one
        if (!sidebarMap.containsKey(player)) {
            createSidebar(player)
        }
        // Return the player's scoreboard
        return sidebarMap[player] ?: scoreboardManager.newScoreboard
    }
}