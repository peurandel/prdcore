package prd.peurandel.prdcore.Manager

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import prd.peurandel.prdcore.Handler.SkillHandler
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredFunctions


class SkillManager(private val plugin: Plugin) : Listener {

    private val skillHandlers: MutableMap<String, MutableList<KFunction<*>>> = mutableMapOf()
    private val handlerInstances: MutableMap<KClass<*>, Any> = mutableMapOf()


    init {
        registerSkillHandlers()
        plugin.logger.info("등록된 스킬 핸들러: ${skillHandlers.keys}")
        skillHandlers.forEach { (skillId, handlers) ->
            plugin.logger.info("스킬 ID: $skillId, 핸들러 수: ${handlers.size}")
        }
    }

    private fun registerSkillHandlers() {
        // 플러그인 클래스 자체의 함수 스캔
        scanClassForHandlers(plugin::class)

        // 여기에 다른 클래스들도 추가
        scanClassForHandlers(prd.peurandel.prdcore.EventListner.SkillHandler::class) // 예시
    }
    private fun scanClassForHandlers(kotlinClass: KClass<*>) {
        // 클래스의 함수 스캔
        kotlinClass.declaredFunctions.forEach { function ->
            val skillHandlerAnnotation = function.annotations.find { it is SkillHandler } as? SkillHandler
            skillHandlerAnnotation?.let {
                val skillId = it.skillId
                skillHandlers.getOrPut(skillId) { mutableListOf() }.add(function)
            }
        }

        // 컴패니언 객체의 함수도 스캔
        kotlinClass.companionObjectInstance?.let { companion ->
            companion::class.declaredFunctions.forEach { function ->
                val skillHandlerAnnotation = function.annotations.find { it is SkillHandler } as? SkillHandler
                skillHandlerAnnotation?.let {
                    val skillId = it.skillId
                    skillHandlers.getOrPut(skillId) { mutableListOf() }.add(function)
                }
            }
        }
    }



    /**
     * 스킬 트리거 이벤트를 발생시키는 함수
     * @param skillId 트리거할 스킬 ID
     * @param context 스킬 실행에 필요한 추가 정보 (선택 사항)
     */
    fun triggerSkill(skillId: String, context: Any? = null) {
        val handlers = skillHandlers[skillId] ?: return

        handlers.forEach { handler ->
            try {
                val parameterCount = handler.parameters.size
                when (parameterCount) {
                    2 -> handler.call(plugin, SkillTriggerEvent(skillId, context))
                    3 -> {
                        // 첫 번째 매개변수의 클래스 가져오기
                        val instanceClass = handler.parameters[0].type.classifier as? KClass<*>
                        // 싱글톤 객체이거나 컴패니언 객체인 경우 해당 인스턴스 사용
                        val instance = instanceClass?.objectInstance ?: plugin
                        handler.call(instance, plugin, SkillTriggerEvent(skillId, context))
                    }
                    else -> plugin.logger.warning("Unexpected parameter count for skill handler: $parameterCount")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error while executing SkillHandler for skillId: $skillId")
                e.printStackTrace()
            }
        }
    }

    /**
     * 스킬 틱 이벤트를 발생시키는 함수 (필요한 경우)
     * @param skillId 틱을 발생시킬 스킬 ID
     */
    fun tickSkill(skillId: String) {
        val handlers = skillHandlers[skillId] ?: return

        handlers.forEach { handler ->
            try {
                handler.call(plugin, SkillTickEvent(skillId)) // 'plugin' 인스턴스를 첫 번째 파라미터로 전달 (필요에 따라 변경)
            } catch (e: Exception) {
                plugin.logger.warning("Error while executing SkillHandler for skillId: $skillId")
                e.printStackTrace()
            }
        }
    }
}

// Skill Trigger Event 클래스 정의
class SkillTriggerEvent(val skillId: String, val context: Any?) : Event() {
    override fun getHandlers(): org.bukkit.event.HandlerList {
        return handlerList
    }

    companion object {
        @JvmStatic
        val handlerList = org.bukkit.event.HandlerList()
    }
}


// Skill Tick Event 클래스 정의 (필요한 경우)
class SkillTickEvent(val skillId: String) : Event() {
    override fun getHandlers(): org.bukkit.event.HandlerList {
        return handlerList
    }

    companion object {
        @JvmStatic
        val handlerList = org.bukkit.event.HandlerList()
    }
}