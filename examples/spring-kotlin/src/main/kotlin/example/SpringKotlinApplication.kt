package example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringKotlinApplication {

  //  You may provide your own DataSource bean if you already have one or do not wish for one to be
  //  autoconfigured from Spring configuration properties.
  //  @Bean fun dataSource() : DataSource = DataSource.fromConfigString("...")
}

fun main() {
  runApplication<SpringKotlinApplication>()
}
