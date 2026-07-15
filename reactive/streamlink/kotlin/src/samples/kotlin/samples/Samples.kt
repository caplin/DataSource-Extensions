package samples

import com.caplin.integration.datasourcex.util.Subject
import com.caplin.integration.streamlinkx.StreamLinkConnectionFactory
import com.caplin.integration.streamlinkx.filterIsUpdate
import java.security.PrivateKey

class Samples {

  suspend fun subscribe(keymasterKey: PrivateKey) {
    // Build a factory for a Liberator, signing KeyMaster tokens with the given private key.
    val factory =
        StreamLinkConnectionFactory(
            liberator = "rttp://liberator.example.com:8080",
            keymasterKey = keymasterKey,
        )

    // Connect as a user and wait until the session is established.
    factory.connect("admin").use { connection ->
      connection.connect()
      connection.awaitConnected()

      // Subscribe to a record subject, keeping only updates and printing each field map.
      connection.getSubject(Subject("SYSTEM", "INFO")).filterIsUpdate().collect { fields ->
        println(fields)
      }
    }
  }
}
