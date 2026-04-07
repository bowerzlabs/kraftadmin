package com.kraftadmin.annotations

import com.kraftadmin.utils.custom_actions.DefaultKraftActionHandler
import com.kraftadmin.utils.custom_actions.KraftActionHandler
import kotlin.reflect.KClass


@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class KraftAdminCustomAction(
    val name: String,
    val label: String = "",
    val icon: String = "play",
    val variant: String = "default",
    val handler: KClass<out KraftActionHandler<*>> = DefaultKraftActionHandler::class
)

enum class ActionVariant {
    DEFAULT, PRIMARY, DANGER, SUCCESS, WARNING
}