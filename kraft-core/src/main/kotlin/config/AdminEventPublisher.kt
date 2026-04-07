package com.kraftadmin.config

interface AdminEventPublisher {
    fun publish(event: AdminEvent)
}
