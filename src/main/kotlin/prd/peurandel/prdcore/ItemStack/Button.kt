package prd.peurandel.prdcore.ItemStack

import com.google.gson.JsonParser
import org.bukkit.*
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.net.URL
import java.util.*


class Button {

    fun ProfileHead(player: String): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as SkullMeta

        val offlinePlayer: OfflinePlayer = Bukkit.getOfflinePlayer(getPlayerUUID(player))
        meta.owningPlayer = offlinePlayer
        meta.setDisplayName("$player's Head")
        item.itemMeta = meta
        return item
    }

    fun Wardrobe(plugin: JavaPlugin): ItemStack {
        val item = ItemStack(Material.LEATHER_CHESTPLATE)
        val meta = item.itemMeta as LeatherArmorMeta

        meta.setDisplayName("${ChatColor.GREEN}Wardrobe")
        meta.setColor(Color.PURPLE)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        meta.addItemFlags(ItemFlag.HIDE_DESTROYS)
        meta.addItemFlags(ItemFlag.HIDE_PLACED_ON)
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"wardrobe")
        item.itemMeta = meta

        return item
    }


    fun Slot(plugin: JavaPlugin?, name: String, ready: Boolean,infoList: List<String>): ItemStack {
        var item = ItemStack(Material.GLASS)
        if (ready) item = ItemStack(Material.BEACON)
        val meta = item.itemMeta
        //Item Meta Set
        // Name & Lore
        meta.setDisplayName(ChatColor.GREEN.toString() + name)
        val lore: MutableList<String> = ArrayList()
        if (ready) {
            lore.add("${ChatColor.GRAY}This wardrobe slot contains your current suit set")
            for(i in infoList.indices) {
                lore.add("${ChatColor.GRAY}${infoList[i]}")
            }
        } else {
            lore.add("${ChatColor.GRAY}This wardrobe slot is locked and cannot be used")
            lore.add("${ChatColor.RED}Requires ")
        }

        meta.lore = lore
        // Persistent
        val key = NamespacedKey(plugin!!, "button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "wordrobe.slot")

        item.setItemMeta(meta)
        return item
    }
    fun GoBack(plugin: JavaPlugin,text: String): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.RED}Back")
        val lore : List<String> = arrayListOf("${ChatColor.GRAY}$text")
        meta.lore = lore

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"goback")
        item.setItemMeta(meta)
        return item
    }
    fun Armor(plugin: JavaPlugin): ItemStack {
        val item = ItemStack(Material.IRON_CHESTPLATE)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.RED}장갑")
        val lore : List<String> = arrayListOf("${ChatColor.GRAY}장갑 없이 슈트가 의미가 있을까요?")
        meta.lore = lore

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"armor")
        item.setItemMeta(meta)
        return item
    }
    fun Engine(plugin: JavaPlugin): ItemStack {
        val item = ItemStack(Material.BEACON)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.RED}엔진")
        val lore : List<String> = arrayListOf("${ChatColor.GRAY}엔진이 없는 슈트는 고철덩어리에 불과하죠.")
        meta.lore = lore

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"engine")
        item.setItemMeta(meta)
        return item
    }

    fun SoftWare(plugin: JavaPlugin): ItemStack {
        val item = ItemStack(Material.COMMAND_BLOCK)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.RED}소프트웨어")
        val lore : List<String> = arrayListOf("${ChatColor.GRAY}소프트웨어는 슈트를 완성시킵니다.")
        meta.lore = lore

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"software")
        item.setItemMeta(meta)
        return item
    }
    fun Magic(plugin: JavaPlugin): ItemStack {
        val item = ItemStack(Material.ENCHANTING_TABLE)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.RED}마법")
        val lore : List<String> = arrayListOf("${ChatColor.GRAY}완벽한 케이크 위에는 작은 체리가 하나 올려져 있죠.")
        meta.lore = lore

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"magic")
        item.setItemMeta(meta)
        return item
    }
    fun Skill(plugin: JavaPlugin): ItemStack {
        val item = ItemStack(Material.REDSTONE)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.RED}기술")
        val lore : List<String> = arrayListOf("${ChatColor.GRAY}슈트를 좀 쓸만하게 만들때가 됐죠.")
        meta.lore = lore

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"skill")
        item.setItemMeta(meta)
        return item
    }

    fun skillPropellant(plugin: JavaPlugin): ItemStack { //추진체
        val item = ItemStack(Material.ELYTRA)
        val meta = item.itemMeta
        meta.setDisplayName("${ChatColor.GREEN}추진체")
        val lore: MutableList<String> = ArrayList()
        lore.add("${ChatColor.GRAY}추진체가 있어야 비행할 수 있습니다.")
        lore.add("${ChatColor.GRAY}빠르다고 다 좋은 것은 아닙니다. 상황에 맞는 추진체를 잘 선택해주세요!")
        meta.lore = lore

        val key = NamespacedKey(plugin, "button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "skill.propellant")
        item.setItemMeta(meta)
        return item
    }

    fun skillFirearm(plugin: JavaPlugin): ItemStack { //추진체
        val item = ItemStack(Material.GUNPOWDER)
        val meta = item.itemMeta
        meta.setDisplayName("${ChatColor.GREEN}총기")
        val lore: MutableList<String> = ArrayList()
        lore.add("${ChatColor.GRAY}직관적이고 효과적입니다.")
        lore.add("${ChatColor.GRAY}비행하는 적에게는 적합하지 않습니다.")
        lore.add("${ChatColor.GRAY}그럴 땐 소프트웨어와 결합하여 그 효과를 유지할 수 있습니다.")
        meta.lore = lore

        val key = NamespacedKey(plugin, "button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "skill.firearm")
        item.setItemMeta(meta)

        return item
    }
    fun Orbital(plugin: JavaPlugin): ItemStack {
        val item = ItemStack(Material.END_CRYSTAL)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.RED}궤도위성")
        val lore : List<String> = arrayListOf("${ChatColor.GRAY}감당 안되는 상황엔 궤도의 도움을 받을 수 있죠.")
        meta.lore = lore

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"orbital")
        item.setItemMeta(meta)
        return item
    }
    fun Research(plugin: JavaPlugin): ItemStack {
        val item = ItemStack(Material.KNOWLEDGE_BOOK)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.RED}Research")
        val lore : List<String> = arrayListOf("${ChatColor.GRAY}이곳에서 기술을 연구할 수 있습니다.")
        meta.lore = lore

        val key = NamespacedKey(plugin,"button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"research")
        item.setItemMeta(meta)
        return item
    }
    fun Skill(plugin: JavaPlugin, UUID: String): ItemStack{
        val item = ItemStack(Material.DIAMOND_SWORD)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.BLUE}SKILL")

        val key = NamespacedKey(plugin,"button")
        val UUIDkey = NamespacedKey(plugin,"uuid")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"skill")
        meta.persistentDataContainer.set(UUIDkey, PersistentDataType.STRING,UUID)

        item.setItemMeta(meta)
        return item
    }


    fun Module(plugin: JavaPlugin, UUID: String): ItemStack{
        val item = ItemStack(Material.CHEST)
        val meta = item.itemMeta

        meta.setDisplayName("${ChatColor.BLUE}Module")

        val key = NamespacedKey(plugin,"button")
        val UUIDkey = NamespacedKey(plugin,"uuid")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING,"module")
        meta.persistentDataContainer.set(UUIDkey, PersistentDataType.STRING,UUID)

        item.setItemMeta(meta)
        return item
    }
    fun Next_Page(plugin: JavaPlugin?, text: String): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta
        //Item Meta Set
        // Name &Lore
        meta.setDisplayName("${ChatColor.GREEN}Next Page")
        val lore: MutableList<String> = ArrayList()
        lore.add("${ChatColor.YELLOW}" + text)

        // Persistent
        val key = NamespacedKey(plugin!!, "button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "nextpage")

        meta.lore = lore
        item.setItemMeta(meta)

        return item
    }

    fun Previous_Page(plugin: JavaPlugin?, text: String): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta
        //Item Meta Set
        // Name & Lore
        meta.setDisplayName("${ChatColor.GREEN}Previous Page")
        val lore: MutableList<String> = ArrayList()
        lore.add("${ChatColor.YELLOW}" + text)
        meta.lore = lore

        // Persistent
        val key = NamespacedKey(plugin!!, "button")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "previouspage")

        item.setItemMeta(meta)
        return item
    }
    // 오프라인 플레이어 UUID 가져오기

    fun getPlayerUUID(playerName: String): UUID {
        val url = URL("https://api.mojang.com/users/profiles/minecraft/$playerName")
        val json = JsonParser.parseReader(url.readText().reader()).asJsonObject
        val id = json.get("id").asString
        return UUID.fromString(id.replaceFirst(
            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(),
            "$1-$2-$3-$4-$5"
        ))
    }


}