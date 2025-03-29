package prd.peurandel.prdcore.Manager

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId


fun getData(name: String, serverCollection: MongoCollection<Document>): Any {
    return Json.decodeFromString(serverCollection.find(Filters.eq("name",name)).first()!!.toJson())

}
@Serializable
data class Changable(val height: Boolean) {
    companion object {
        inline fun <reified T> create(name: String, serverCollection: MongoCollection<Document>): T { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", name)).first()!!.toJson())// 수정
        }
    }
}

@Serializable
data class Armor(val armor: Int, val cost: Int, val duration: Int, val weight: Int)


@Serializable
@JsonIgnoreUnknownKeys
@SerialName("power_repulsor")
data class PowerRepulsorSkill(
    override val name: String,
    val damage: Int, // 파워 리펄서 스킬에 필요한 속성
    val cost: List<Int>
) : SkillItem

@Serializable
@JsonIgnoreUnknownKeys
@SerialName("repulsor")
data class RepulsorSkill(
    override val name: String,
    val damage: Int, // 파워 리펄서 스킬에 필요한 속성
    val cost: List<Int>
) : SkillItem


@Serializable
@JsonIgnoreUnknownKeys
@SerialName("unibeam")
data class UnibeamSkill(
    override val name: String,
    val damage: Int, // 파워 리펄서 스킬에 필요한 속성
    val cost: List<Int>
) : SkillItem


@Serializable
@JsonIgnoreUnknownKeys
@Polymorphic
sealed interface SkillItem {
    val name: String?
}


@Serializable
@JsonIgnoreUnknownKeys
data class Skill(
    val slot0: List<SkillItem>,
    val slot1: List<SkillItem>,
    val slot2: List<SkillItem>,
    val slot3: List<SkillItem>,
    val slot4: List<SkillItem>,
    val slot5: List<SkillItem>,
    val slot6: List<SkillItem>,
    val slot7: List<SkillItem>,
    val slot8: List<SkillItem>
) {
    companion object {
        inline fun <reified T> create(name: String, serverCollection: MongoCollection<Document>): T { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", name)).first()!!.toJson())// 수정
        }
    }
}

@Serializable
@JsonIgnoreUnknownKeys
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
@JsonIgnoreUnknownKeys
data class Research(
    val engine: List<Engine>,
    val armor: List<ArmorType>,
    val magic: List<Magics>,
    val skill: List<Skills>
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
@JsonIgnoreUnknownKeys
data class ResearchEngine(
    val name: String = "Engine",
    val engine: MutableList<Engine>
){
    companion object {
        fun create(serverCollection: MongoCollection<Document>): ResearchEngine { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", "Engine")).first()!!.toJson())// 수정
        }
    }
}
@Serializable
@JsonIgnoreUnknownKeys
data class Engine(
    val name: String,
    val type: String,
    val lore: List<String>,
    val tier: Byte,
    val energy: Int,
    val item: String,
    val requireEx: Int,
    val requireResearch: String?
    )
@Serializable
@JsonIgnoreUnknownKeys
data class ResearchArmor(
    val name: String = "Armor",
    val armor: MutableList<ArmorType>
) {
    companion object {
        fun create(serverCollection: MongoCollection<Document>): ResearchArmor { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", "ArmorType")).first()!!.toJson())// 수정
        }
    }
}

@Serializable
@JsonIgnoreUnknownKeys
data class ArmorType(
    val name: String,
    val type: String,
    val lore: List<String>,
    val armor: Int,
    val weight: Int,
    val cost: Int,
    val duration: Int,
    val item: String,
    val requireEx: Int
    ) {
    companion object {
        inline fun <reified T> create(name: String, serverCollection: MongoCollection<Document>): T { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", name)).first()!!.toJson())// 수정
        }
    }
}
@Serializable
@JsonIgnoreUnknownKeys
data class ResearchMaterial(

    val name: String = "Material",
    val material: MutableList<Material>

) {
    companion object {
        inline fun create(serverCollection: MongoCollection<Document>): ResearchMaterial { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", "Material")).first()!!.toJson())// 수정
        }
    }
}
@Serializable
@JsonIgnoreUnknownKeys
data class Material(
    val name: String,
    val type: String,
    val lore: List<String>,
    val armor: Int,
    val weight: Double,
    val cost: Int,
    val duration: Int,
    val item: String,
    val requireEx: Int
) {
    companion object {
        inline fun <reified T> create(name: String, serverCollection: MongoCollection<Document>): T { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", name)).first()!!.toJson())// 수정
        }
    }
}
@Serializable
@JsonIgnoreUnknownKeys
data class ResearchSkills(
    val name: String = "Skills",
    val skills: MutableList<Skills>
){
    companion object {
        inline fun <reified T> create(serverCollection: MongoCollection<Document>): T { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", "Skill")).first()!!.toJson())// 수정
        }
    }
}
@Serializable
@JsonIgnoreUnknownKeys
data class Skills(
    val name: String,
    val type: String,
    val lore: List<String>,
    val item: String,
    val requireEx: Int,
)
@Serializable
@JsonIgnoreUnknownKeys
data class ResearchMagic(
    val name: String = "Magics",
    val magics: MutableList<Magics>
){
    companion object {
        inline fun <reified T> create(serverCollection: MongoCollection<Document>): T { // 수정: 이름 변경 및 제네릭 추가
            return Json.decodeFromString(serverCollection.find(Filters.eq("name", "Magic")).first()!!.toJson())// 수정
        }
    }
}

@Serializable
@JsonIgnoreUnknownKeys
data class Magics(
    val name: String,
    val type: String,
    val lore: List<String>,
    val item: String,
    val requireEx: Int,
)