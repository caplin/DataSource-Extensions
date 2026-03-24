package com.caplin.integration.datasourcex.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A wrapper around [Logger] providing the ability to lazily build log lines using Kotlin native
 * string templating.
 */
@JvmInline
value class KLogger(private val logger: Logger) {

  /** Logs an error message lazily evaluated from [message] if error logging is enabled. */
  fun error(message: () -> Any?) {
    if (logger.isErrorEnabled) logger.error(message().toString())
  }

  /** Logs a warning message lazily evaluated from [message] if warn logging is enabled. */
  fun warn(message: () -> Any?) {
    if (logger.isWarnEnabled) logger.warn(message().toString())
  }

  /** Logs an info message lazily evaluated from [message] if info logging is enabled. */
  fun info(message: () -> Any?) {
    if (logger.isInfoEnabled) logger.info(message().toString())
  }

  /** Logs a debug message lazily evaluated from [message] if debug logging is enabled. */
  fun debug(message: () -> Any?) {
    if (logger.isDebugEnabled) logger.debug(message().toString())
  }

  /** Logs a trace message lazily evaluated from [message] if trace logging is enabled. */
  fun trace(message: () -> Any?) {
    if (logger.isTraceEnabled) logger.trace(message().toString())
  }

  /**
   * Logs an error message with a [throwable], lazily evaluated from [message], if error logging is
   * enabled.
   */
  fun error(throwable: Throwable?, message: () -> Any?) {
    if (logger.isErrorEnabled) logger.error(message().toString(), throwable)
  }

  /**
   * Logs a warning message with a [throwable], lazily evaluated from [message], if warn logging is
   * enabled.
   */
  fun warn(throwable: Throwable?, message: () -> Any?) {
    if (logger.isWarnEnabled) logger.warn(message().toString(), throwable)
  }

  /**
   * Logs an info message with a [throwable], lazily evaluated from [message], if info logging is
   * enabled.
   */
  fun info(throwable: Throwable?, message: () -> Any?) {
    if (logger.isInfoEnabled) logger.info(message().toString(), throwable)
  }

  /**
   * Logs a debug message with a [throwable], lazily evaluated from [message], if debug logging is
   * enabled.
   */
  fun debug(throwable: Throwable?, message: () -> Any?) {
    if (logger.isDebugEnabled) logger.debug(message().toString(), throwable)
  }

  /**
   * Logs a trace message with a [throwable], lazily evaluated from [message], if trace logging is
   * enabled.
   */
  fun trace(throwable: Throwable?, message: () -> Any?) {
    if (logger.isTraceEnabled) logger.trace(message().toString(), throwable)
  }
}

/** Returns a [KLogger] instance for the specified class [T]. */
inline fun <reified T : Any> getLogger(): KLogger = KLogger(LoggerFactory.getLogger(T::class.java))
