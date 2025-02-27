package prd.peurandel.prdcore.Manager

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class Changable(val height: Boolean)

@Serializable
data class Armor(val armor: Int, val cost: Int, val duration: Int, val weight: Int)


@Serializable
data class Skill(
    val slot0: List<String>,
    val slot1: List<String>,
    val slot2: List<String>,
    val slot3: List<String>,
    val slot4: List<String>,
    val slot5: List<String>,
    val slot6: List<String>,
    val slot7: List<String>,
    val slot8: List<String>
)

@Serializable
data class WardrobeItem(
    var name: String,
    val uuid: String,
    val engine: String?,
    val tier: Int?,
    val energy: Int?,
    val skill: Skill,
    val armorName: String?,
    val material: String?,
    val helmet: String?,
    val chestplate: String?,
    val leggings: String?,
    val boots: String?,
    val max_energy: Int?,
    val armor: Armor?,
    val duration: Int?,
    val weight: Double?,
    val max_durability: Int?,
    val durability: Int?
)

@Serializable
data class Research(
    val engine: List<String>,
    val armor: List<String>
)


@Serializable
@JsonIgnoreUnknownKeys
data class User(
    val uuid: String,
    var name: String,
    val joinTime: Long,
    val height: Int,
    val changable: Changable,
    val wardrobe: MutableList<WardrobeItem>,
    val wardrobeCount: Int,
    val money: Int,
    val wardrobepage: Int,
    val research_point: Int,
    val research: Research
)


//연구
@Serializable
data class ResearchEngine(
    val name: String = "Engine",
    val engine: MutableList<Engine>

)
@Serializable
data class Engine(
    val name: String,
    val id: String,
    val lore: List<String>,
    val tier: Byte,
    val energy: Int,
    val requireEx: Int,
    val requireResearch: String?
    )
@Serializable
data class ResearchArmor(
    val name: String = "Armor",
    val armor: MutableList<ArmorType>
)

@Serializable
data class ArmorType(
    val name: String,
    val id: String,
    val lore: List<String>,
    val armor: Int,
    val weight: Int,
    val cost: Int,
    val duration: Int,
    val requireEx: Int
    )
@Serializable
data class ResearchMaterial(

    val name: String = "Material",
    val material: MutableList<Material>

)
@Serializable
data class Material(
    val name: String,
    val id: String,
    val lore: List<String>,
    val armor: Int,
    val weight: Double,
    val cost: Int,
    val duration: Int,
    val requireEx: Int
)
@Serializable
data class ResearchSkills(
    val name: String = "Skills",
    val skills: MutableList<Skills>
)
@Serializable
data class Skills(
    val name: String,
    val id: String,
    val lore: List<String>,
    val requireEx: Int,
)
@Serializable
data class ResearchMagic(
    val name: String = "Magics",
    val magics: MutableList<Magics>
)

@Serializable
data class Magics(
    val name: String,
    val id: String,
    val lore: List<String>,
    val requireEx: Int,
)