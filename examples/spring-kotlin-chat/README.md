# Module spring-kotlin-chat

This example shows a typical integration with an existing service.

[Liberator Explorer UI](http://localhost:18080/diagnostics/liberatorexplorer_react/index.html) can be used to interact 
with it.

You can subscribe to the subjects
* `/info/{roomId}`
* `/tail/{roomId}`
* `/get/{roomId}/{messageId}`

And you can contribute JSON to the subject `/post` with messages in the format:
```json
{
  "roomId": "example-room-id",
  "message": "example-message"
}
```

# Package example

Contains an example integration against a stub chat service.

# Package example.stubs

Contains a stub chat service that we're demonstrating an integration against.