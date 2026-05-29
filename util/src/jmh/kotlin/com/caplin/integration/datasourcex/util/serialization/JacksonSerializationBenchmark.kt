package com.caplin.integration.datasourcex.util.serialization

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.mutableFlowMapOf
import com.caplin.integration.datasourcex.util.serialization.jackson2.registerDataSourceModule
import com.caplin.integration.datasourcex.util.serialization.jackson3.addDataSourceModule
import com.fasterxml.jackson.core.type.TypeReference as Jackson2TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import tools.jackson.core.type.TypeReference as Jackson3TypeReference
import tools.jackson.module.kotlin.jacksonMapperBuilder

/**
 * Compares Jackson 2 and Jackson 3 throughput for serializing and deserializing a representative
 * DataSource event (a 50-entry [FlowMapStreamEvent.InitialState]) through the DataSource module.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class JacksonSerializationBenchmark {

  private val jackson2 = jacksonObjectMapper().registerDataSourceModule()
  private val jackson3 = jacksonMapperBuilder().addDataSourceModule().build()

  private val jackson2Type = object : Jackson2TypeReference<FlowMapStreamEvent<String, Int>>() {}
  private val jackson3Type = object : Jackson3TypeReference<FlowMapStreamEvent<String, Int>>() {}

  private lateinit var event: FlowMapStreamEvent<String, Int>
  private lateinit var json: String

  @Setup
  fun setup() {
    val map = mutableFlowMapOf<String, Int>()
    repeat(50) { map.put("key$it", it) }
    event = FlowMapStreamEvent.InitialState(map.asMap())
    json = jackson2.writeValueAsString(event)
  }

  @Benchmark fun serializeJackson2(): String = jackson2.writeValueAsString(event)

  @Benchmark fun serializeJackson3(): String = jackson3.writeValueAsString(event)

  @Benchmark
  fun deserializeJackson2(): FlowMapStreamEvent<String, Int> =
      jackson2.readValue(json, jackson2Type)

  @Benchmark
  fun deserializeJackson3(): FlowMapStreamEvent<String, Int> =
      jackson3.readValue(json, jackson3Type)
}
