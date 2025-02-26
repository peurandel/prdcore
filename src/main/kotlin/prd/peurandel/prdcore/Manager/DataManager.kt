package prd.peurandel.prdcore.Manager

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

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
