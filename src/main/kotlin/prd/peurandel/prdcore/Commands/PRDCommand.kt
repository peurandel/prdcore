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

class PRDCommand(private val plugin: JavaPlugin,database: MongoDatabase) : CommandExecutor, TabCompleter {

    val ServerCollection = database.getCollection("server")
    val ItemDoc = ServerCollection.find(Filters.eq("name","item")).first() as Document
    val TypeKeys = listOf("material", "engines", "skills", "techs", "softwares", "magics", "orbital")

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
            else -> player.sendMessage("알 수 없는 명령어: ${args[0]}")
        }
        return true
    }



    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String>? {
        return when (args.size) {
            1 -> listOf("item").filter { it.startsWith(args[0], true) }
            2 -> tabCompleterArgsSize2(args)
            3 -> tabCompleterArgsSize3(args)
            4 -> tabCompleterArgsSize4(args)
            else -> null
        }
    }

    private fun tabCompleterArgsSize2(args: Array<String>): List<String>? {
        return when (args[0]) {
            "item" -> listOf("get", "set").filter { it.startsWith(args[1], true) }
            else -> null
        }
    }

    private fun tabCompleterArgsSize3(args: Array<String>): List<String>? {
        val materialKeys = getItemTypeKeys()
        return when (args[0]) {
            "item" -> when (args[1]) {
                "get","set" -> materialKeys.filter { it.startsWith(args[2],true)}
                else -> null
            }
            else -> null
        }
    }
    private fun tabCompleterArgsSize4(args: Array<String>): List<String>? {
        return when (args[0]) {
            "item" -> when (args[1]) {
                "get","set" -> if(args[2] in TypeKeys) getItemKeys(args[2]) else null
                else -> null
            }
            else -> null
        }
    }

    fun getItemTypeKeys(): List<String> {
        return ItemDoc["list"] as List<String>
    }
    fun getItemKeys(type: String): List<String> {
        val TypeDoc = ItemDoc[type] as Document

        return TypeDoc.keys.toList()
    }
    private fun handleItemGet(player: Player, args: Array<String>) {
        val TypeDoc = getTypeDoc(player, args) ?: return
        if(args.size == 3) {
            val itemList = TypeDoc.keys.joinToString(", ")
            player.sendMessage("[$args[2]] 타입의 아이템 목록: $itemList")
        } else if(args.size == 4){
            val valueDoc = getValueDoc(player,TypeDoc,args[3]) as Document

            if(valueDoc["item"] != null) {
                player.inventory.addItem(ItemSerialization.deserializeItemStack(valueDoc["item"].toString()))
                player.sendMessage("${ChatColor.GREEN}[PRD] ${ChatColor.WHITE}성공적으로 ${args[2]}의 ${args[3]} 아이템을 가져왔습니다!")

            } else {
                player.sendMessage("${ChatColor.GREEN}[PRD] ${ChatColor.WHITE}그런 아이템 없음")
            }
        }
    }

    private fun handleItemSet(player: Player, args: Array<String>) {
        val TypeDoc = getTypeDoc(player, args) ?: return
        if(args.size == 3) {
            val itemList = TypeDoc.keys.joinToString(", ")
            player.sendMessage("[$args[2]] 타입의 아이템 목록: $itemList")
        } else if(args.size == 4){
            val handItem: String = ItemSerialization.serializeItemStack(player.inventory.itemInMainHand)

            ServerCollection.updateOne(
                Filters.eq<String>("name", "item"),
                Updates.combine(
                    Updates.set("${args[2]}.${args[3]}.item",handItem)
                )
            )
            player.sendMessage("${ChatColor.GREEN}[PRD] ${ChatColor.WHITE}성공적으로 ${args[2]}의 ${args[3]}의 아이템으로 업데이트했습니다.")
        }
    }

    private fun getTypeDoc(player: Player, args: Array<String>): Document? {

        if (args.size < 3 || args[2] !in TypeKeys) {
            player.sendMessage("올바르지 않은 타입입니다. 사용 가능한 타입: ${TypeKeys.joinToString(", ")}")
            return null
        }

        val TypeDoc = ItemDoc[args[2]] as? Document
        if (TypeDoc == null) {
            player.sendMessage("해당 타입의 아이템 데이터가 존재하지 않습니다: ${args[2]}")
        }

        return TypeDoc
    }

    private fun getValueDoc(player: Player, TypeDoc: Document,args: String): Document? {

        return if(TypeDoc[args] !=null) {
            TypeDoc[args] as Document
        } else {
            player.sendMessage("그딴 값 없음 ㅋㅋ")
            null
        }
    }

}
