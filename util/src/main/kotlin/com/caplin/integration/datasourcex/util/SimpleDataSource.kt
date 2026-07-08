package com.caplin.integration.datasourcex.util

import com.caplin.datasource.DataSource
import com.caplin.datasource.Peer
import com.caplin.datasource.PeerStatus
import com.caplin.datasource.PeerStatus.DOWN
import com.caplin.datasource.PeerStatus.UP
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

class SimpleDataSource(private val dataSource: DataSource) : DataSource by dataSource {

  private val _state =
      MutableSharedFlow<Map<Peer, PeerStatus>>(replay = 1, extraBufferCapacity = Int.MAX_VALUE)

  init {
    var knownPeers = dataSource.peers.associate { PeerKey(it) as Peer to DOWN }.toPersistentMap()
    _state.tryEmit(knownPeers)
    val lock = ReentrantLock()
    dataSource.addConnectionListener {
      lock.withLock {
        knownPeers = knownPeers.putting(PeerKey(it.peer), it.peerStatus)
        _state.tryEmit(knownPeers)
      }
    }
  }

  val peerStates = _state.asSharedFlow()

  /** Await all known peers to be connected */
  suspend fun awaitConnected() {
    _state.first { it.values.all { it == UP } }
  }

  /** A [Peer] whose identity is its index, so it can be used as a map key. */
  private class PeerKey(peer: Peer) : Peer by peer {
    override fun equals(other: Any?) = other is PeerKey && other.index == index

    override fun hashCode() = index

    override fun getLoggingId(): String? {
      return super.getLoggingId()
    }
  }
}
