package com.kraftadmin.annotation

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
//@ConditionalOnExpression("\${kraftpulse.enabled:false} or \${kraftadmin.enabled:false}")
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnKraftEnabled