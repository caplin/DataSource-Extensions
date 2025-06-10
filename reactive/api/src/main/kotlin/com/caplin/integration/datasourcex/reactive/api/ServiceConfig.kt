package com.caplin.integration.datasourcex.reactive.api

import com.caplin.datasource.DataSource
import com.caplin.datasource.Service
import com.caplin.datasource.Service.RequiredState
import java.time.Duration

class ServiceConfig(val name: String) {

  /**
   * Will default to the [DataSource] `datasrc-local-label`
   *
   * @see Service.setRemoteLabelPattern
   */
  var remoteLabelPattern: String? = null

  /** @see Service.setDiscardTimeout */
  var discardTimeout: Duration? = null

  /** @see Service.setThrottleTime */
  var throttleTime: Duration? = null

  /** @see Service.setRequiredState */
  var requiredState: RequiredState? = null

  /** @see Service.addIfLabelPattern */
  var ifLabelPatterns: List<String>? = null
}
