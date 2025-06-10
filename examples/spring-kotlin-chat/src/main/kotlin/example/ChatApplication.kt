package example

import example.stubs.ChatService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class ChatApplication {

  @Bean fun chatService() = ChatService.create()
}

fun main() {
  runApplication<ChatApplication>()
}
