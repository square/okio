Okio Behavior
=============

Decorators for `Source` and `Sink` which apply behaviors such as rate limiting and emulating
failures.

`BehaviorSource` and `BehaviorSink` each wrap a `Source` and `Sink`, respectively, and expose
a mutable `Behavior` object.

```java
Buffer sink = new Buffer();
BehaviorSink behaviorSink = new BehaviorSink(sink);
Behavior behavior = behaviorSink.behavior();
```

The `Behavior` has properties which can be modified over time.

 * Before/after `flush()` callbacks (applies to `Sink` only).
 * Before/after `close()` callbacks.
 * Configuring the read/write transfer rate.
