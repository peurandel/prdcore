package prd.peurandel.prdcore.Commands

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import prd.peurandel.prdcore.ItemStack.ItemSerialization
import prd.peurandel.prdcore.Manager.*

class PRDCommand(private val plugin: JavaPlugin,database: MongoDatabase) : CommandExecutor, TabCompleter {

    val ServerCollection = database.getCollection("server")
    private lateinit var configManager: ConfigManager
    private lateinit var messageConfigManager: MessageConfigManager // MessageConfigManager 인스턴스

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }
        val player = sender as Player

        if (args.isEmpty()) {
            player.sendMessage("/prd <action> <item> <value> 형식으로 입력해 주세요.")
            return true
        }

        when (args[0]) {
            "item" -> {
                if (args.size != 4) {
                    player.sendMessage("사용법: /prd item <get/set> <type> <value>")
                    return true
                }
                when (args[1]) {
                    "get" -> handleItemGet(player, args)
                    "set" -> handleItemSet(player, args)
                    else -> player.sendMessage("알 수 없는 서브 명령어: ${args[1]}")
                }
            }
            "reload" -> {
                if (sender == player) {
                    sender.sendMessage("알 수 없는 명령어거나 권한이 없습니다.")
                    return true
                }
                //reloadPluginConfigs()
                sender.sendMessage(messageConfigManager.getMessage("config_reloaded"))
                return true
            }
            else -> player.sendMessage("알 수 없는 명령어: ${args[0]}")
        }
        return true
    }



    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String>? {
        return when (args.size) {
            1 -> listOf("item","reload").filter { it.startsWith(args[0], true) }
            2 -> tabCompleterArgsSize2(args)
            //3 -> tabCompleterArgsSize3(args)
            //4 -> tabCompleterArgsSize4(args)
            else -> null
        }
    }
    fun tabCompleterArgsSize2(args: Array<String>): List<String>? {
        return when(args[1]) {
            "item" -> listOf("set","get")
            else -> null
        }
    }




    private fun handleItemGet(player: Player, args: Array<String>) {

        //args : 0 = item, 1 = get 2 = type
        val type = getType(args[2],args[3]) ?: player.sendMessage("${ChatColor.RED}[!] 그런거 없음") as String
        player.inventory.addItem(ItemSerialization.deserializeItemStack(type))
        player.sendMessage("${ChatColor.GREEN}[PRD] ${ChatColor.WHITE}성공적으로 ${args[2]}의 ${args[3]} 아이템을 가져왔습니다!")
    }

    fun getType(type: String,name: String) : String? {
        return when(type) {
            "engine" -> ResearchEngine.create(ServerCollection).engine.find{ it.name == name}?.item
            "armor" -> ResearchArmor.create(ServerCollection).armor.find{ it.name == name}?.item
            "material" -> ResearchMaterial.create(ServerCollection).material.find{ it.name == name}?.item
            "skills" -> (ResearchSkills.create(ServerCollection) as ResearchSkills).skills.find{ it.name == name}?.item
            "magics" -> (ResearchMagic.create(ServerCollection) as ResearchMagic).magics.find{ it.name == name}?.item
            else -> null
        }
    }


    fun getTypeIndex(type:String,id: String): Int? {
        when(type) {
            "engine" -> {
                val List = (ResearchEngine.create(ServerCollection) as ResearchEngine).engine
                for(i in List.indices) {
                    if(List[i].type ==id) {
                        return i
                    }
                }
                return null
            }
            "armor" -> {
                val List = ResearchArmor.create(ServerCollection).armor
                for(i in List.indices) {
                    if(List[i].type ==id) {
                        return i
                    }
                }
                return null
            }
            "material" -> {
                val List = ResearchMaterial.create(ServerCollection).material
                for(i in List.indices) {
                    if(List[i].type ==id) {
                        return i
                    }
                }
                return null
            }
            "skills" -> {
                val List = (ResearchSkills.create(ServerCollection) as ResearchSkills).skills
                for(i in List.indices) {
                    if(List[i].type ==id) {
                        return i
                    }
                }
                return null
            }

            "magics" -> {
                val List = (ResearchMagic.create(ServerCollection) as ResearchMagic).magics
                for(i in List.indices) {
                    if(List[i].type ==id) {
                        return i
                    }
                }
                return null
            }
        }
        return null
    }
    fun getTypeName(type:String): String? {
        return when(type) {
            "engine" -> "Engine"
            "armor" -> "ArmorType"
            "material" -> "Material"
            "skills" -> "Skill"
            "magics" -> "Magic"
            else -> null
        }
    }

    private fun handleItemSet(player: Player, args: Array<String>) {

        //args : 0 = item, 1 = get 2 = type
        val typeName = getTypeName(args[2])
        if(typeName == null) {
            player.sendMessage("잘못된 타입 이름")
            return
        }
        val handItem: String = ItemSerialization.serializeItemStack(player.inventory.itemInMainHand)

        player.sendMessage("${getTypeIndex(args[2],args[3])}")

        ServerCollection.updateOne(
            Filters.eq<String>("name", typeName),
            Updates.combine(
                Updates.set("${args[2]}.${getTypeIndex(args[2],args[3])}.item",handItem)
            )
        )
        player.sendMessage("${ChatColor.GREEN}[PRD] ${ChatColor.WHITE}성공적으로 ${args[2]}의 ${args[3]}의 아이템으로 업데이트했습니다.")

    }


    private fun loadPluginSettings() {
    }

    fun getPluginConfig(): ConfigManager { // ConfigManager 인스턴스 반환 메서드 (필요한 경우)
        return configManager
    }
    fun getMessageConfig(): MessageConfigManager { // MessageConfigManager 인스턴스 반환 메서드 (필요한 경우)
        return messageConfigManager
    }


}
