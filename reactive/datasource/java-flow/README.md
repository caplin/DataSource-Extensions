# Module datasourcex-java-flow

Provides an API for binding implementations of Java's [Flow](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.html) to subjects and channels provided by [Caplin DataSource](https://www.caplin.com/developer/caplin-platform/datasource/).   
This library is designed for use from both Java and Kotlin.

# Alternative Implementation

For most projects it is recommended to use the `datasourcex-reactivestreams` module instead, as it provides better compatibility with reactive libraries such as RxJava, Reactor & Akka Streams.

@see com.caplin.integration.datasourcex.reactive.java.Bind.using  
@see com.caplin.integration.datasourcex.reactive.java.Bind  

@sample samples.Samples.bind