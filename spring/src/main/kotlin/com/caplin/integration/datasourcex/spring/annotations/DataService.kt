package com.caplin.integration.datasourcex.spring.annotations

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MINUTES
import org.springframework.stereotype.Controller

@Controller
annotation class DataService(
    val value: String = "",
    val remoteLabelPattern: String = "",
    val discardTimeout: Long = 1L,
    val timeUnit: TimeUnit = MINUTES,
)
