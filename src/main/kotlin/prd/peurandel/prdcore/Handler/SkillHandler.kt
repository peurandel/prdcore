package prd.peurandel.prdcore.Handler

@Retention(AnnotationRetention.RUNTIME) // 런타임까지 어노테이션 정보 유지
@Target(AnnotationTarget.FUNCTION)    // 함수에 적용 가능한 어노테이션
annotation class SkillHandlerAnnotation(val skillId: String)