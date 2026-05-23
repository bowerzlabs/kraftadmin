package com.kraftadmin

import org.springframework.context.ApplicationEvent

class KraftPulseSampleEvent(
    source: Any,
    val message: String
) : ApplicationEvent(source)