package com.caplin.integration.datasourcex.reactive.api

sealed class BroadcastConfig {
  var cache: Boolean = false

  class Record : BroadcastConfig() {
    var recordType: RecordType = RecordType.GENERIC
  }

  class Mapping : BroadcastConfig()
}
