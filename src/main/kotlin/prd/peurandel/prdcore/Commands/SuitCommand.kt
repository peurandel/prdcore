package prd.peurandel.prdcore.Commands

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.Gui.MainGUI
import prd.peurandel.prdcore.Manager.SuitManager

class SuitCommand(private val plugin: JavaPlugin,database: MongoDatabase) : CommandExecutor {

    var database = database
    val userCollection = database.getCollection("users")
    val suitManager = SuitManager(plugin,database)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be run by a player.")
            return false
        }

        val player = sender as Player

        if (label.equals("suit", ignoreCase = true)) {
            handleSuitCommand(player, args)
        }

        return true
    }

    private fun handleSuitCommand(player: Player, args: Array<out String>) {
        when (args.size) {
            0 -> openMainGUI(player,player.name)
            else -> handleSingleArgument(player, args)
        }
    }

    private fun openMainGUI(player: Player,playerName: String) {
        val mainGUI = MainGUI(plugin,database)
        mainGUI.open(plugin,player)
    }

    private fun handleSingleArgument(player: Player, args: Array<out String>) {
        when (args[0]) {
            "on" -> handleSuitOn(player,args)
            "off" -> handleSuitOff(player)
            "wardrobe" -> player.sendMessage("Accessing your wardrobe")
            "open" -> openOtherPlayerGUI(player,args)
            "research" -> Research(player,args)
            "fill" -> handleFill(player,args)
            else -> player.sendMessage("Unknown argument.")
        }
    }
    private fun openOtherPlayerGUI(player: Player,args: Array<out String>) {
        if(args.size == 2) {
            openMainGUI(player,args[1].lowercase())
        }
    }
    private fun handleFill(player: Player, args: Array<out String>) {
        if(args.size==3) {
            val playerDoc = userCollection.find(Filters.eq("uuid",player.uniqueId.toString())).first() as Document
            val suitUUID = suitManager.getSuitUUID(playerDoc,args[1]) as String
            suitManager.fillSuitDurability(player.uniqueId.toString(),suitUUID,args[2].toInt())
            player.sendMessage("당신의 슈트 ${args[1]}의 내구도를 ${args[2]}만큼 올렸습니다.")
        } else if(args.size==4) {
            val playerDoc = userCollection.find(Filters.eq("name",args[1])).first() as Document
            val suitUUID = suitManager.getSuitUUID(playerDoc,args[2]) as String
            suitManager.fillSuitDurability(playerDoc.getString("uuid"),suitUUID,args[2].toInt())
            player.sendMessage("${args[1]}의 슈트 ${args[2]}의 내구도를 ${args[3]}만큼 올렸습니다.")
        } else {
            player.sendMessage("이상하게 명령어치지마셈")
        }
    }
    private fun handleSuitOn(player: Player, args: Array<out String>) {
        when (args.size) {
            3 -> applySuitToPlayer(player, args[1], args[2])
            2 -> applySuitToPlayer(player, player.name, args[1])
            else -> player.sendMessage("${ChatColor.RED}명령어 형식이 올바르지 않습니다. 사용법: /suiton [플레이어 이름] [슈트 이름]")
        }
    }


    private fun handleSuitOff(player: Player) {
        suitManager.offSuit(player)
        player.sendMessage("${ChatColor.GREEN}[PRD] ${ChatColor.WHITE}슈트를 벗었습니다.")
    }
    private fun applySuitToPlayer(player: Player, targetPlayerName: String, suitName: String) {
        val targetPlayerDoc = userCollection.find(Filters.eq("name", targetPlayerName)).firstOrNull()
        if (targetPlayerDoc == null) {
            player.sendMessage("${ChatColor.RED}$targetPlayerName 플레이어 정보를 찾을 수 없습니다.")
            return
        }

        val suitDoc = suitManager.loadSuit(targetPlayerDoc, suitName)
        if (suitDoc == null) {
            player.sendMessage("${ChatColor.RED}해당 수트($suitName)를 찾을 수 없습니다.")
            return
        }

        if(suitDoc["durability"] as Int <= 0) {
            player.sendMessage("슈트의 내구도가 부족합니다!")
            return
        }
        suitManager.setSuit(player, targetPlayerDoc.getString("uuid") ?: player.uniqueId.toString(), suitDoc)
        suitManager.wearArmor(player, suitDoc)

        player.sendMessage("${ChatColor.GREEN}[PRD] ${ChatColor.WHITE}${targetPlayerName}의 ${suitDoc.getString("name")} 슈트를 착용했습니다.")
    }

    private fun ResearchArmor(player: Player, args: Array<out String>, bool: Boolean) {
        if(bool) {
        } else {

        }
    }
    private fun ResearchEngine(player: Player, args: Array<out String>, bool: Boolean) {
        if(bool) {

        } else {

        }
    }
    private fun ResearchAdd(player: Player, args: Array<out String>) {
        when (args[2]) {
            "armor" -> ResearchArmor(player,args,true)
            "engine" -> ResearchEngine(player,args,true)

            else -> player.sendMessage("[PRD]그런 연구 테마 없다")
        }
    }
    private fun ResearchRemove(player: Player, args: Array<out String>) {
        when (args[2]) {
            "armor" -> ResearchArmor(player,args,false)
            "engine" -> ResearchEngine(player,args,false)

            else -> player.sendMessage("[PRD]그런 연구 테마 없다")
        }
    }
    private fun Research(player: Player, args: Array<out String>) {
        when (args[1]) {
            "add" -> ResearchAdd(player,args)
            "remove" -> ResearchRemove(player,args)
            "exp" -> Exp(player, args)
            else -> player.sendMessage("[PRD]exp를 하든 연구를 추가해주던 해.")
        }
    }
    private fun Exp(player: Player, args: Array<out String>) {
        when (args[2]) {
            "set" -> ExpSet(player,args)
            else -> player.sendMessage("아니 좀 ㅋㅋ")
        }
    }
    private fun ExpSet(player: Player, args: Array<out String>) {
        if(args.size != 5) {
            player.sendMessage("이상한 명령어 동작 방식임")
            return
        }
        userCollection.updateOne(
            Filters.eq<String>(
                "name",
                args[3]
            ), Updates.set("research_point",args[4].toInt())
        )
        player.sendMessage("${args[3]}의 연구 경험치를 ${args[4]}만큼 추가하였습니다.")
    }
}
