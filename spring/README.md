# Module spring-boot-starter-datasource

This module provides a starter for integrating Caplin DataSource with your 
[Spring Boot](https://spring.io/projects/spring-boot) application, and integration with 
[Spring Messaging](https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html)
for publishing data from annotated functions.

This release compiles against Spring Boot ${springBootVersion} and its associated dependencies.

The simplest way to get started is to configure either of the following properties in your application's
`application.yaml` or `application.properties`

* To connect to another Peer directly, such as Liberator, specify: `caplin.datasource.managed.peer.outgoing`
* To connect via Discovery, specify: `caplin.datasource.managed.discovery.address`

Additional configuration options and their defaults can be seen in [com.caplin.integration.datasourcex.spring.DataSourceConfigurationProperties].

Alternatively you can provide your entire DataSource configuration file by specifying the 
`caplin.datasource.provided.configuration-file` property, or you can provide your own DataSource Bean.
