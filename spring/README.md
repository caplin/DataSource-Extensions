# Module spring-boot-starter-datasource

This module provides a starter for integrating Caplin DataSource with your 
[Spring Boot](https://spring.io/projects/spring-boot) application, and integration with 
[Spring Messaging](https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html)
for publishing data from annotated functions.

This release compiles against Spring Boot ${springBootVersion} and its associated dependencies.

## Getting Started

Please see the [hands-on tutorial](https://github.com/caplin/DataSource-Extensions/tree/main/spring/docs/GUIDE.md), 
or the [code examples](https://github.com/caplin/DataSource-Extensions/tree/main/examples).

## Basic usage

The simplest way to get started is to configure either of the following properties in your application's 
`application.yaml` or `application.properties` and allow the autoconfiguration to take care of configuring and providing
a DataSource.

* To connect to another Peer directly, such as Liberator, specify: `caplin.datasource.managed.peer.outgoing`
* To connect via Discovery, specify: `caplin.datasource.managed.discovery.address`

Additional configuration options and their defaults can be seen in [com.caplin.integration.datasourcex.spring.DataSourceConfigurationProperties].

## Advanced usage

For more advanced usage you can provide your DataSource configuration file by specifying the 
`caplin.datasource.provided.configuration-file` property, or you can provide your own DataSource Bean.
