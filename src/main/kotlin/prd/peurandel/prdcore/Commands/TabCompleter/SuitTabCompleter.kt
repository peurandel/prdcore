package prd.peurandel.prdcore.Commands.TabCompleter

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class SuitTabCompleter : TabCompleter {

    private val commands = listOf("open", "on", "off", "wardrobe","fill")

    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<String>): List<String> {
        if (sender !is Player) return emptyList()
        val player: Player = sender

        return when(args.size) {
            1 -> filterResults(commands, args[0])
            2 -> {
                when(args[0]) {
                    "on", "open" -> {
                        if (player.hasPermission("prd.commands.suit.${args[0]}")) {
                            getOnlinePlayerNames()
                        } else {
                            emptyList()
                        }
                    }
                    "wardrobe" -> {
                        if (player.hasPermission("prd.commands.suit.wardrobe")) {
                            filterResults(listOf("setname", "list"), args[1])
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            3 -> {
                if (args[0].equals("wardrobe", ignoreCase = true) && args[1].equals("setname", ignoreCase = true) && player.hasPermission("prd.commands.suit.wardrobe.setname")) {
                    getWardrobeNames()
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun filterResults(options: List<String>, input: String): List<String> {
        return options.filter { it.startsWith(input, ignoreCase = true) }
    }

    private fun getOnlinePlayerNames(): List<String> {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        val playerNames = ArrayList<String>()

        for (player in onlinePlayers) {
            playerNames.add(player.name)
        }

        return playerNames
    }

    private fun getWardrobeNames(): List<String> {
        // 플레이어의 워드로브 이름을 반환하는 로직을 추가하세요.
        return emptyList()
    }
}
