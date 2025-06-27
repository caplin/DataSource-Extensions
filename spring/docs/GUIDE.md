[[_TOC_]]

# Streaming real-time data with Spring Boot

### Goals

This guide will show you how to use the Caplin platform and [Spring Boot](https://spring.io/projects/spring-boot) to rapidly build an application that can deliver on-demand, real time data to a browser or mobile application.

### Pre-requisites

This guide assumes that you are familiar with Spring Boot, else it would be beneficial to follow the [Building an Application with Spring Boot](https://spring.io/guides/gs/spring-boot/) guide before returning.

#### Software requirements

* Java JDK 17 or later
* Docker, or a similar container runtime that supports compose files.
* A Java or Kotlin IDE

### Project setup

Now let's create a simple application.

* Navigate to [Spring Initializr](https://start.spring.io/)

* It's recommended to choose _Gradle - Kotlin_ for the Project, and _Kotlin_ for the Language, though you may of course use _Java_.

* Choose the latest Spring Boot release version, at the time of writing this is 3.5.3.

* Generate the project, unzip it, and then import it into your IDE.

* Now we need to add our DataSource Starter dependency, so open up `build.gradle.kts` and add `implementation("com.caplin.integration.datasourcex:spring-boot-starter-datasource:1.0.0")` to the `dependencies` block.

* You will also need to add the Caplin repository to be able to access the Caplin DataSource libraries. To do so, add the following to the `repositories` block. Note the credentials here should be retrieved from your Caplin Account Manager. These are best passed in from the command line, via environment variable or via your global `gradle.properties` file to ensure they are not inadvertently exposed:
  ```kotlin
  maven {
    url = uri("https://repository.caplin.com/repository/caplin-release")
    credentials {
      username = <username>
      password = <password>
    }
  }
  ```

* Lastly, we'll need to configure the Liberator host that DataSource will connect to, so open `src/main/resources/application.properties` and add the line `caplin.datasource.managed.peer.outgoing=ws://localhost:19000`

### Running the Caplin platform

To launch the Caplin platform you can use the [example Docker Compose file](https://github.com/caplin/DataSource-Extensions/tree/main/examples) from the repository examples. Please refer to the brief readme for instructions. This will launch a container running a preconfigured Liberator and expose two ports; `18080` for inbound front end application connections and `19000` for the inbound WebSocket connection from our new server application.

### Creating a simple browser client

We'll want to be able to request and display some data from our server, so let us create a basic browser client application to do so. Add the following to your project as `./index.html`. This code sets up a connection to the platform with the StreamLink library (In this case, hosted by our Liberator container at `http://localhost:18080/sljs/streamlink.js`) and enables the library's support for handling streaming JSON patches behind the scenes.

> For the sake of clarity, we are omitting most error handling code.

```html
<html lang="en">
<head>
    <title>Streaming Demo</title>
    <script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"></script>
    <script src="http://localhost:18080/sljs/streamlink.js"></script>
    <script type="module">
        import * as jsonpatch from 'https://cdnjs.cloudflare.com/ajax/libs/fast-json-patch/3.1.1/fast-json-patch.min.js';

        export let streamLink = caplin.streamlink.StreamLinkFactory.create({
            liberator_urls: "rttp://localhost:18080",
            username: "admin",
            password: "admin",

            json_handler: {
                parse: function (jsonString) {
                    return JSON.parse(jsonString);
                },
                patch: function (existingObject, jsonPatchString) {
                    const patch = JSON.parse(jsonPatchString);
                    return patch.reduce(jsonpatch.applyReducer, existingObject);
                },
                format: function (obj) {
                    return JSON.stringify(obj, null, "\t");
                }
            }
        });

        streamLink.addConnectionListener({
            onConnectionStatusChange: function(connectionStatusEvent) {
                document.getElementById("connection-status").innerHTML = `<pre>${connectionStatusEvent}</pre>`
            },
        });

        window.onbeforeunload = function(event) {
            streamLink.disconnect()
        }

        streamLink.connect();

        // TODO subscriptions
    </script>
</head>
<body class="p-4">
<div class="text-xl p-4 m-4 bg-gray-100 rounded-lg" id="connection-status"></div>
</body>
</html>
```

If you open this in your browser, you should see that we have successfully connected to Liberator.

```
ConnectionStatusEventImpl [LiberatorURL=ws://localhost:18080, connectionState=LOGGEDIN]
```

### Providing static data

Now let's add some data! In this case our client wants to retrieve the local time and time zone of the server. To handle this we'll create a new `@Controller` class providing an aptly named `/serverTime` endpoint.

```kotlin
@Controller
class StreamingController {

    data class TimeEvent(val time: LocalTime, val zoneId: ZoneId)

    @MessageMapping("/serverTime")
    fun time(): TimeEvent = TimeEvent(LocalTime.now(), ZoneId.systemDefault())
}
```

Let's launch our application and see what happens - to do so you can either

* Run the main method in `com.example.demo.DemoApplication` through your IDE
* Run `./gradlew bootRun` from the terminal in your project's directory.

In the resulting logs we should see our application successfully connect to the platform

```
Peer 0 (localhost/127.0.0.1:19000): is connected
```

and we should see log line indicating our subject has been bound correctly

```
Registering [/serverTime] as Static
```

If we now edit our `index.html` to subscribe to this, adding the code provided below in place of the existing `//TODO subscriptions` placeholder, and refresh our browser, we should see the server's time and time zone data being displayed.

```javascript
document.body.innerHTML += `<div class="text-xl p-4 m-4 bg-gray-100 rounded-lg" id="serverTime"></div>`

let timeSubject = "/serverTime"
streamLink.subscribe(timeSubject, {
    onJsonUpdate: function (subscription, event) {
        document.getElementById("serverTime").innerHTML = `<pre>${timeSubject} - ${JSON.stringify(event.getJson(), null, 2)}</pre>`
    }
})
```

Note that serialization to JSON is handled for us automatically by way of Spring's [Jackson](https://github.com/FasterXML/jackson) integration.

### Providing streaming data

Now as nice as that is, we'd like more than a single response, so let's modify our endpoint to provide a stream of events, rather than just the initial response. For Kotlin we'll be returning a [Flow](https://kotlinlang.org/docs/flow.html). For Java you can instead make use of Reactor's [Flux](https://projectreactor.io/docs/core/release/reference/#flux). Both are powerful abstractions over a stream of data.

Modify the `StreamingController` class to replace our previous function with the following:

```kotlin
@MessageMapping("/serverTime")
fun serverTime(): Flow<TimeEvent> = flow {
    while (true) {
        emit(TimeEvent(LocalTime.now(), ZoneId.systemDefault()))
        delay(100)
    }
}
```

One brief restart of the server application later, and you should see the client updating in real time!

### Request parameters

Now, imagine that the browser client needs to fetch the local time in a specific time zone. To achieve this we can fairly simply add a new endpoint to our controller, this time named `zonedTime` and taking a `@DestinationVariable` that is extracted from the requested subject.

```kotlin
@MessageMapping("/zonedTime/{zoneId}")
fun zonedTime(@DestinationVariable zoneId: ZoneId): Flow<TimeEvent> = flow {
    while (true) {
        val now = ZonedDateTime.now(zoneId)
        emit(TimeEvent(now.toLocalTime(), zoneId))
        delay(100)
    }
}
```

To test this we can add to our client code to specify a time zone on a second request.

> As our parameter contains a `/` character, the zone ID must be URL encoded in order to match our subject defined in the `@MessageMapping`:

```javascript
document.body.innerHTML += `<div class="text-xl p-4 m-4 bg-gray-100 rounded-lg" id="zonedTime"></div>`
let zonedTimeSubject = "/zonedTime/Africa%2FLusaka"
streamLink.subscribe(zonedTimeSubject, {
    onJsonUpdate: function (subscription, event) {
        document.getElementById("zonedTime").innerHTML = `<pre>${zonedTimeSubject} - ${JSON.stringify(event.getJson(), null, 2)}</pre>`
    }
})
```

After another quick restart of our server, and a refresh of our browser, we now have two time streams being displayed.

### Request payloads

But what if our stream request becomes a bit more complicated, perhaps containing optional or arrays of parameters? At this point it's more natural to represent our request as a payload object. Let's assume our client now wishes to make a single subscription to the time in various user specified zones, again we can support this with a few minor additions to our server. Create a new endpoint named `/times` in your controller, this time receiving single non-annotated method parameter which will be our payload from the client.

```kotlin
data class TimesRequest(
    val zones: List<ZoneId>,
)

@MessageMapping("/times")
fun times(timesRequest: TimesRequest): Flow<List<TimeEvent>> = flow {
    while (true) {
        val now = Instant.now()
        fun timeAtZone(zoneId: ZoneId) = TimeEvent(now.atZone(zoneId).toLocalTime(), zoneId)
        emit(timesRequest.zones.map(::timeAtZone))
        delay(100)
    }
}
```

Now for our client we need to do something a bit different for this case - we'll need to establish a channel rather than a plain subscription, and then send our request. This is quite simple:

```javascript
document.body.innerHTML += `<div class="text-xl p-4 m-4 bg-gray-100 rounded-lg" id="times"></div>`
let timesSubject = "/times"
let timesChannel = streamLink.createJsonChannel(timesSubject, {
    onChannelData: function (channel, event) {
        document.getElementById("times").innerHTML = `<pre>${timesSubject} - ${JSON.stringify(event.getJson(), null, "\t")}</pre>`;
    },
}, null);

timesChannel.send({
    zones: ["America/Costa_Rica", "Australia/Sydney", "Africa/Lusaka"]
});
```

Running this we'll now see all the requested times being displayed and updating in sync.

### Two-way communication

Lastly, say we now want our client to have the ability to add new zones to the stream in an ad-hoc manner. Fortunately, we can do this with a just few tweaks.

On the client we'll add a simple text entry box and button, the clicking of which will send a message through the channel to let the server know to add a new zone.

```javascript
window.addZone = function () {
    timesChannel.send({
        zones: [document.getElementById("zone").value]
    });
}
document.body.innerHTML += `<div><input type="text" id="zone" value="Chile/EasterIsland"><button type="button" onclick="addZone()">Add zone</button> </div>`
```

And on the server we can update our `/times` endpoint to accept a stream of data from the client by changing our parameter to be either a Flow or Flux accordingly, and then update our responses to include the newly requested zones:

```kotlin
data class TimesRequest(
    val zones: List<ZoneId>,
)

@MessageMapping("/times")
fun times(zoneRequests: Flow<TimesRequest>): Flow<List<TimeEvent>> = zoneRequests
    .runningFold(emptyList<ZoneId>()) { accumulator, zoneRequest -> accumulator + zoneRequest.zones }
    .transformLatest { zones ->
        while (true) {
            val now = Instant.now()
            fun timeAtZone(zoneId: ZoneId) = TimeEvent(now.atZone(zoneId).toLocalTime(), zoneId)
            emit(zones.map(::timeAtZone))
            delay(100)
        }
    }
```

One final restart of our application, and by clicking the button we can now see additional times being added to our stream each time we add a new zone.