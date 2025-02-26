package prd.peurandel.prdcore.Manager

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import prd.peurandel.prdcore.Handler.SkillHandler
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions


class SkillManager(private val plugin: Plugin) : Listener {

    private val skillHandlers: MutableMap<String, MutableList<KFunction<*>>> = mutableMapOf()

    init {
        // SkillHandler 어노테이션이 붙은 함수들을 찾아 skillHandlers 맵에 등록
        registerSkillHandlers()
        Bukkit.getPluginManager().registerEvents(this, plugin) // 이벤트 리스너 등록 (필요한 경우)
    }
    private fun registerSkillHandlers() {
        // 플러그인의 모든 클래스를 순회하며 SkillHandler 어노테이션이 붙은 함수를 찾음
        plugin::class.declaredFunctions.forEach { function -> // function은 KFunction<*> 타입
            // function.kotlinFunction.let { kotlinFunction -> // kotlinFunction 부분 제거
            function.let { kotlinFunction -> // function을 직접 kotlinFunction으로 사용
                val skillHandlerAnnotation = kotlinFunction.annotations.find { it is SkillHandler } as? SkillHandler
                skillHandlerAnnotation?.let {
                    val skillId = it.skillId
                    skillHandlers.getOrPut(skillId) { mutableListOf() }.add(kotlinFunction)
                }
            }
        }
        // 필요하다면, 특정 패키지 또는 클래스에서만 스캔하도록 변경 가능
        // 예: 특정 클래스 내에서만 스캔
        // MySkillHandlerClass::class.declaredFunctions.forEach { ... }
    }


    /**
     * 스킬 트리거 이벤트를 발생시키는 함수
     * @param skillId 트리거할 스킬 ID
     * @param context 스킬 실행에 필요한 추가 정보 (선택 사항)
     */
    fun triggerSkill(skillId: String, context: Any? = null) {
        val handlers = skillHandlers[skillId] ?: return // 해당 스킬 ID에 등록된 핸들러가 없으면 종료

        handlers.forEach { handler ->
            try {
                // 핸들러 함수 실행, context 정보를 파라미터로 전달
                handler.call(plugin, SkillTriggerEvent(skillId, context)) // 'plugin' 인스턴스를 첫 번째 파라미터로 전달 (필요에 따라 변경)
            } catch (e: Exception) {
                plugin.logger.warning("Error while executing SkillHandler for skillId: $skillId")
                e.printStackTrace() // 에러 로깅
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