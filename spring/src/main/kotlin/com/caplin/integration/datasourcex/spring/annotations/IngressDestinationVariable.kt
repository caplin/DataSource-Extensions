package com.caplin.integration.datasourcex.spring.annotations

import com.caplin.integration.datasourcex.spring.annotations.IngressToken.USER_ID
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import org.springframework.messaging.handler.annotation.DestinationVariable

/**
 * An annotation that extends the behavior of [DestinationVariable] to enable ingress mapping.
 *
 * This annotation can be used to define a destination template variable to extract, along with the
 * token that should be mapped into this position on ingress by Liberator.
 *
 * Example usage:
 * ```kotlin
 * @MessageMapping("/mystream/{userId}")
 * fun myStream(@IngressDestinationVariable(IngressToken.USER_ID) userId: String)
 * ```
 *
 * See Liberator's
 * [object-map](https://www.caplin.com/developer/caplin-platform/liberator/liberator-object-configuration-part-2#object-map)
 * configuration.
 *
 * @param value The name of the destination template variable to bind to.
 * @param token The token that should be mapped into this position on ingress by Liberator. Built-in
 *   tokens are defined as constants under [IngressToken].
 */
@Target(VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class IngressDestinationVariable(

    /**
     * The token that should be mapped into this position.
     *
     * Built-in tokens are available under [IngressToken].
     */
    val token: String,

    /** The name of the destination template variable to bind to. */
    val value: String = "",
)

object IngressToken {
  /**
   * Requests a user session ID - which persists over reconnection - to be mapped into this
   * variable. It may be used in combination with the [USER_ID] token to create user session
   * specific paths.
   */
  const val PERSISTENT_SESSION_ID = "%g"

  /**
   * Requests a user session ID - which does not persist over reconnection - to be mapped into this
   * variable. It may be used in combination with the [USER_ID] token to create user session
   * specific paths.
   */
  const val CONNECTION_SESSION_ID = "%G"

  /** This contains the known user ID. */
  const val USER_ID = "%u"
}
