package com.caplin.integration.datasourcex.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A wrapper around [Logger] providing the ability to lazily build log lines using Kotlin native
 * string templating.
 */
@JvmInline
value class KLogger(private val logger: Logger) {

  fun error(message: () -> Any?) {
    if (logger.isErrorEnabled) logger.error(message().toString())
  }

  fun warn(message: () -> Any?) {
    if (logger.isWarnEnabled) logger.warn(message().toString())
  }

  fun info(message: () -> Any?) {
    if (logger.isInfoEnabled) logger.info(message().toString())
  }

  fun debug(message: () -> Any?) {
    if (logger.isDebugEnabled) logger.debug(message().toString())
  }

  fun trace(message: () -> Any?) {
    if (logger.isTraceEnabled) logger.trace(message().toString())
  }

  fun error(throwable: Throwable?, message: () -> Any?) {
    if (logger.isErrorEnabled) logger.error(message().toString(), throwable)
  }

  fun warn(throwable: Throwable?, message: () -> Any?) {
    if (logger.isWarnEnabled) logger.warn(message().toString(), throwable)
  }

  fun info(throwable: Throwable?, message: () -> Any?) {
    if (logger.isInfoEnabled) logger.info(message().toString(), throwable)
  }

  fun debug(throwable: Throwable?, message: () -> Any?) {
    if (logger.isDebugEnabled) logger.debug(message().toString(), throwable)
  }

  fun trace(throwable: Throwable?, message: () -> Any?) {
    if (logger.isTraceEnabled) logger.trace(message().toString(), throwable)
  }
}

inline fun <reified T : Any> getLogger(): KLogger = KLogger(LoggerFactory.getLogger(T::class.java))
