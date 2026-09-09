@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Value
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.equals.shouldBeEqual
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Lets virtual time settle so launched collectors reach their suspension points. */
private suspend fun settle() = delay(100)

/** An upstream that counts how many times it has been collected. */
private class Upstream(
    scope: CoroutineScope,
    started: SharingStarted,
    replay: Int = 0,
) {
  private val channel = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
  val collections = AtomicInteger()

  val shared: CompletingSharedFlow<String> =
      channel
          .receiveAsFlow()
          .onStart { collections.incrementAndGet() }
          .dematerialize()
          .shareInCompleting(scope, started, replay)

  suspend fun send(value: String) = channel.send(Value(value))

  suspend fun complete() = channel.send(Completion())

  suspend fun fail(throwable: Throwable) = channel.send(Completion(throwable))
}

/** Records what one subscriber saw, so several can be compared. */
private class Sink {
  val values = Channel<String>(Channel.BUFFERED)
  val terminal = CompletableDeferred<String>()

  suspend fun next() = values.receive()

  fun receivedSoFar(): List<String> = buildList {
    while (true) add(values.tryReceive().getOrNull() ?: break)
  }
}

private fun CoroutineScope.subscribe(upstream: Upstream): Pair<Sink, Job> {
  val sink = Sink()
  val job = launch {
    try {
      upstream.shared.collect { sink.values.send(it) }
      sink.terminal.complete("completed")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      sink.terminal.complete(e::class.simpleName ?: "?")
    }
  }
  return sink to job
}

/**
 * Subscribe and discard scenarios for [shareInCompleting] — how many times the upstream is
 * collected, what a late subscriber sees, and what survives a discard.
 */
class CompletingSharedFlowSubscriptionTest :
    FunSpec({
      context("concurrent subscribers share one upstream collection") {
        withData(
            nameFn = { (name, _) -> name },
            "Eagerly" to Eagerly,
            "Lazily" to Lazily,
            "WhileSubscribed" to SharingStarted.WhileSubscribed(),
        ) { (_, started) ->
          val upstream = Upstream(backgroundScope, started)
          val (a, _) = backgroundScope.subscribe(upstream)
          val (b, _) = backgroundScope.subscribe(upstream)
          settle()

          upstream.send("A")

          a.next() shouldBeEqual "A"
          b.next() shouldBeEqual "A"
          upstream.collections.get() shouldBeEqual 1
        }
      }

      test("three subscribers leave exactly one sharing job in the scope") {
        val upstream = Upstream(backgroundScope, SharingStarted.WhileSubscribed())
        val before = backgroundScope.coroutineContext.job.children.count()

        val sinks = List(3) { backgroundScope.subscribe(upstream) }
        settle()
        upstream.send("A")
        sinks.forEach { (sink, _) -> sink.next() shouldBeEqual "A" }

        // three collector jobs plus exactly one sharing job
        val after = backgroundScope.coroutineContext.job.children.count()
        (after - before) shouldBeEqual 4
      }

      test("a late subscriber sees the replayed value without a second collection") {
        val upstream = Upstream(backgroundScope, Eagerly, replay = 1)
        upstream.send("A")
        settle()

        val (late, _) = backgroundScope.subscribe(upstream)
        settle()

        late.next() shouldBeEqual "A"
        upstream.collections.get() shouldBeEqual 1
      }

      test("with no replay a late subscriber sees only what follows it") {
        val upstream = Upstream(backgroundScope, Eagerly)
        val (early, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("A")
        early.next() shouldBeEqual "A"

        val (late, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("B")

        early.next() shouldBeEqual "B"
        late.next() shouldBeEqual "B"
        late.receivedSoFar() shouldBeEqual emptyList()
        upstream.collections.get() shouldBeEqual 1
      }

      test("a subscriber joining mid-stream shares the live collection") {
        val upstream = Upstream(backgroundScope, SharingStarted.WhileSubscribed())
        val (first, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("A")
        first.next() shouldBeEqual "A"

        val (second, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("B")

        first.next() shouldBeEqual "B"
        second.next() shouldBeEqual "B"
        upstream.collections.get() shouldBeEqual 1
      }

      context("terminal events reach every concurrent subscriber") {
        test("completion") {
          val upstream = Upstream(backgroundScope, Eagerly)
          val (a, _) = backgroundScope.subscribe(upstream)
          val (b, _) = backgroundScope.subscribe(upstream)
          settle()

          upstream.complete()

          a.terminal.await() shouldBeEqual "completed"
          b.terminal.await() shouldBeEqual "completed"
        }

        test("error") {
          val upstream = Upstream(backgroundScope, Eagerly)
          val (a, _) = backgroundScope.subscribe(upstream)
          val (b, _) = backgroundScope.subscribe(upstream)
          settle()

          upstream.fail(IllegalArgumentException())

          a.terminal.await() shouldBeEqual "IllegalArgumentException"
          b.terminal.await() shouldBeEqual "IllegalArgumentException"
        }
      }

      context("a second collection must not compete with the first for the upstream") {
        test("no value is stolen") {
          val upstream = Upstream(backgroundScope, Eagerly)
          val (a, _) = backgroundScope.subscribe(upstream)
          settle()
          val (b, _) = backgroundScope.subscribe(upstream)
          settle()

          val sent = (1..8).map { "v$it" }
          sent.forEach { upstream.send(it) }
          settle()

          a.receivedSoFar() shouldBeEqual sent
          b.receivedSoFar() shouldBeEqual sent
        }

        test("the completion is not swallowed") {
          val upstream = Upstream(backgroundScope, Eagerly)
          val (a, _) = backgroundScope.subscribe(upstream)
          settle()
          backgroundScope.subscribe(upstream)
          settle()

          upstream.send("A")
          settle()
          upstream.complete()

          a.next() shouldBeEqual "A"
          withTimeout(5.seconds) { a.terminal.await() } shouldBeEqual "completed"
        }
      }

      test("a subscriber arriving after a completed subscription rematerialises the upstream") {
        val upstream = Upstream(backgroundScope, SharingStarted.WhileSubscribed())
        val (first, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("A")
        first.next() shouldBeEqual "A"
        upstream.complete()
        first.terminal.await() shouldBeEqual "completed"
        settle()

        val (late, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("B")

        late.next() shouldBeEqual "B"
        upstream.collections.get() shouldBeEqual 2
      }

      test("a subscriber arriving after a failed subscription rematerialises the upstream") {
        val upstream = Upstream(backgroundScope, SharingStarted.WhileSubscribed())
        val (first, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.fail(IllegalArgumentException())
        first.terminal.await() shouldBeEqual "IllegalArgumentException"
        settle()

        val (late, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("B")

        late.next() shouldBeEqual "B"
        upstream.collections.get() shouldBeEqual 2
      }

      test("two subscribers arriving after a completed subscription still collect once") {
        val upstream = Upstream(backgroundScope, SharingStarted.WhileSubscribed())
        val (first, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.complete()
        first.terminal.await() shouldBeEqual "completed"
        settle()

        val (a, _) = backgroundScope.subscribe(upstream)
        val (b, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("B")

        a.next() shouldBeEqual "B"
        b.next() shouldBeEqual "B"
        upstream.collections.get() shouldBeEqual 2
      }

      test("discarding one of two subscribers leaves the other collecting") {
        val upstream = Upstream(backgroundScope, SharingStarted.WhileSubscribed())
        val (a, jobA) = backgroundScope.subscribe(upstream)
        val (b, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("A")
        a.next() shouldBeEqual "A"
        b.next() shouldBeEqual "A"

        jobA.cancel()
        settle()
        upstream.send("B")

        b.next() shouldBeEqual "B"
        a.receivedSoFar() shouldBeEqual emptyList()
        upstream.collections.get() shouldBeEqual 1
      }

      test("resubscribing after every subscriber is discarded rematerialises once") {
        val upstream = Upstream(backgroundScope, SharingStarted.WhileSubscribed())
        val jobs = List(2) { backgroundScope.subscribe(upstream).second }
        settle()
        upstream.send("A")
        settle()

        jobs.forEach(Job::cancel)
        settle()
        upstream.collections.get() shouldBeEqual 1

        val (a, _) = backgroundScope.subscribe(upstream)
        val (b, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("B")

        a.next() shouldBeEqual "B"
        b.next() shouldBeEqual "B"
        upstream.collections.get() shouldBeEqual 2
      }

      test("under Eagerly a discard does not stop the upstream") {
        val upstream = Upstream(backgroundScope, Eagerly)
        val (_, job) = backgroundScope.subscribe(upstream)
        settle()
        job.cancel()
        settle()

        val (late, _) = backgroundScope.subscribe(upstream)
        settle()
        upstream.send("B")

        late.next() shouldBeEqual "B"
        upstream.collections.get() shouldBeEqual 1
      }
    })
