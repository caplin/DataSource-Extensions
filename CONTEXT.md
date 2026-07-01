# DataSource-Extensions

The domain language for a library that wraps Caplin's DataSource SDK with reactive APIs. Terms fall into two groups: concepts inherited from the Caplin DataSource network, and the binding vocabulary this library coins on top of it.

## The DataSource network

**Subject**:
A hierarchical path identifying a resource clients can subscribe to, e.g. `/fx/gbpusd`.
_Avoid_: topic, channel (a Channel is a distinct concept), endpoint

**Liberator**:
The Caplin gateway clients connect through. It routes messages, authenticates, and applies object-maps at subscription time.

**Peer**:
A DataSource participant connected to the Liberator — typically a Service providing data. This library runs as a peer. A new peer connection triggers cache replay for Broadcasts.
_Avoid_: node, client (a Client is a distinct concept)

**Client**:
An end-user application that connects to the Liberator via StreamLink and subscribes to Subjects. This library never talks to a Client directly — the Liberator sits between them.
_Avoid_: subscriber, browser

**Service**:
A named data publisher this library registers with the Liberator. It owns the discard timeout, throttle time, and remote-label settings for the Subjects it serves.

**Namespace**:
An Ant-style pattern (`*`, `**`, `{var}`) that matches a set of Subjects and extracts path variables from them.
_Avoid_: subject pattern, glob

**Object mapping**:
A placeholder-to-token substitution the Liberator applies to a Subject at subscription time (e.g. mapping `%u` to the requesting user's id).

## Binding

**Bind**:
To wire a source of data to a Namespace so the library serves it over DataSource. The four binding shapes below are the core vocabulary.

**Active**:
A binding that serves a Subject on demand: each subscribing peer gets its own subscription to the source, torn down when that peer discards.
_Avoid_: on-demand, pull, request-response

**Active container**:
An Active binding for a Container: it serves the structure (which Rows exist) as a stream of changes; peers request each Row separately.

**Channel**:
A bidirectional binding — the peer both sends messages to and receives messages from the server over one Subject.
_Avoid_: two-way stream, socket

**Broadcast**:
A binding that publishes the same data to every connected peer at once, with no per-peer subscription. A cached Broadcast replays its last value to newly connected peers.
_Avoid_: fan-out, publish-all

## Data flavours

Every binding is served in one of three flavours, matching a DataSource wire format:

**Json**:
Data bound as a JSON object.

**Record**:
Data bound as a Record — a collection of key/value fields. Comes in GENERIC and TYPE1 record types.

**Mapping**:
Data bound as a Subject string that redirects the peer: the peer must re-request on the returned Subject.
_Avoid_: redirect, remap

## Containers

**Container**:
A DataSource collection of Rows addressed by one Subject. A peer requests the container to learn its structure, then requests each Row.
_Avoid_: table, list, grid

**Row**:
A single keyed entry in a Container, served at a Subject derived from the container's path.
_Avoid_: item, record (a Record is the unrelated data flavour), entry

**Image**:
A message flagged as a complete snapshot, as opposed to an incremental update (a delta) that follows it.
_Avoid_: snapshot, full refresh

## Spring surface

A plain Spring `@Controller` with `@MessageMapping` methods serves Subjects directly. The `@Data*` annotations below are specializations of those that expose extra DataSource behaviour.

**Data service**:
A `@DataService` — a `@Controller` specialization whose methods serve Subjects, adding Service-level settings such as the remote-label pattern and discard timeout.

**Data message mapping**:
A `@DataMessageMapping("/subject/{var}")` — a `@MessageMapping` specialization that binds one Subject pattern to a handler method and selects its data flavour (Json, Record, or Mapping).

**Ingress variable**:
A handler parameter, marked `@IngressDestinationVariable`, filled from a Subject path variable — optionally from a Liberator token such as the user id or session id rather than a literal segment.
