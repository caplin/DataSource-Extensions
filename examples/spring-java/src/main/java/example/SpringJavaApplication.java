package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringJavaApplication {

//  You may provide your own DataSource bean if you already have one or do not wish for one to be autoconfigured from
//  Spring configuration properties.
//  @Bean
//  DataSource dataSource() {
//      return DataSource.fromConfigString("...");
//  }

    public static void main(String[] args) {
        SpringApplication.run(SpringJavaApplication.class, args);
    }
}