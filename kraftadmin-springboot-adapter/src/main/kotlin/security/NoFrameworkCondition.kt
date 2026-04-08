package com.kraftadmin.security

import com.kraftadmin.config.KraftAdminSpringSecurityConfig
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class NoFrameworkSecurityCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        return !KraftAdminSpringSecurityConfig.isSpringSecurityActive()
    }
}