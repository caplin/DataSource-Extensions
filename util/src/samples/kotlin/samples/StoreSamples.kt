package samples

import com.caplin.integration.datasourcex.util.store.CacheLoaderWriter
import com.caplin.integration.datasourcex.util.store.TxContext
import com.caplin.integration.datasourcex.util.store.Versioned
import com.caplin.integration.datasourcex.util.store.buildFlowStoreCache
import com.caplin.integration.datasourcex.util.store.mutableFlowStore
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TransactionContext
import org.jooq.TransactionListener
import org.jooq.impl.DefaultTransactionListenerProvider
import samples.FlowStorePublishingListener.commitEnd
import samples.FlowStorePublishingListener.rollbackEnd

class StoreSamples {

  /** The aggregate root stored under each key — replaced as a whole on every write. */
  data class Account(val id: String, val balance: Long)

  /**
   * A [CacheLoaderWriter] over a jOOQ `account` table whose `version` is drawn from a database
   * sequence, so the version is the store's durable commit order rather than an in-process counter.
   *
   * The transactional operations ([write], [delete] and the read-modify-write [load]) run on the
   * caller's transaction via `tx.transaction`. The cache-miss read-through [load] has no
   * transaction, so it runs its blocking jOOQ call on [Dispatchers.IO].
   */
  class JooqAccountStore(private val dsl: DSLContext) :
      CacheLoaderWriter<String, Account, Configuration> {

    override suspend fun load(key: String): Versioned<Account>? =
        withContext(Dispatchers.IO) {
          dsl.fetchOne("select balance, version from account where id = ?", key)?.toVersioned(key)
        }

    /** A locking read on the transaction's connection so a read-modify-write serialises writers. */
    override fun load(key: String, tx: TxContext<Configuration>): Versioned<Account>? =
        tx.transaction
            .dsl()
            .fetchOne("select balance, version from account where id = ? for update", key)
            ?.toVersioned(key)

    override fun write(key: String, value: Account, tx: TxContext<Configuration>): Long =
        tx.transaction
            .dsl()
            .fetchOne(
                """
                insert into account (id, balance, version)
                values (?, ?, nextval('account_version'))
                on conflict (id) do update
                  set balance = excluded.balance, version = nextval('account_version')
                returning version
                """,
                key,
                value.balance,
            )!!
            .get("version", Long::class.java)

    override fun delete(key: String, tx: TxContext<Configuration>): Long =
        tx.transaction
            .dsl()
            .fetchOne(
                "delete from account where id = ? returning nextval('account_version') as version",
                key,
            )!!
            .get("version", Long::class.java)

    private fun Record.toVersioned(key: String): Versioned<Account> =
        Versioned(Account(key, get("balance", Long::class.java)), get("version", Long::class.java))
  }

  /**
   * Wires a [mutableFlowStore] over a jOOQ `account` table and moves funds between two accounts in
   * a single `dsl.transaction { … }`: the locking read-modify-write (`store.get(key, config)`) and
   * both writes run on the transaction, and both deltas reach the stream together on commit or
   * neither on rollback. [FlowStorePublishingListener], installed once on the [DSLContext], is what
   * turns each commit into the delta publish.
   */
  suspend fun jooqSample(rootDsl: DSLContext, scope: CoroutineScope) {
    val cache =
        Caffeine.newBuilder().maximumSize(10_000).buildFlowStoreCache<String, Account>(scope)
    val store =
        mutableFlowStore(JooqAccountStore(rootDsl), cache, txContext = Configuration::asTxContext)

    // Install the publishing listener once on the DSLContext; transactions opened from it run the
    // store's buffered commit/rollback actions in their commit/rollback callbacks.
    val dsl =
        rootDsl
            .configuration()
            .derive(DefaultTransactionListenerProvider(FlowStorePublishingListener))
            .dsl()

    withContext(Dispatchers.IO) {
      dsl.transaction { config ->
        val alice = store.get("alice", config) ?: Account("alice", 0)
        val bob = store.get("bob", config) ?: Account("bob", 0)
        store.put("alice", alice.copy(balance = alice.balance - 10), config)
        store.put("bob", bob.copy(balance = bob.balance + 10), config)
      }
    }
  }
}

private const val COMMIT_ACTIONS = "datasourcex.flowstore.commit-actions"
private const val ROLLBACK_ACTIONS = "datasourcex.flowstore.rollback-actions"

/**
 * Runs the store's buffered post-commit / rollback actions from within jOOQ's transaction
 * lifecycle: [commitEnd] fires the commit actions ([COMMIT_ACTIONS]) and [rollbackEnd] the rollback
 * actions ([ROLLBACK_ACTIONS]), each adapted from the transaction's [Configuration] by
 * [asTxContext].
 */
private object FlowStorePublishingListener : TransactionListener {
  override fun commitEnd(ctx: TransactionContext) = ctx.configuration().runActions(COMMIT_ACTIONS)

  override fun rollbackEnd(ctx: TransactionContext) =
      ctx.configuration().runActions(ROLLBACK_ACTIONS)
}

/**
 * Adapts a jOOQ transaction [Configuration] to a [TxContext], buffering the store's post-commit
 * side effects in the transaction-scoped [Configuration.data] so [FlowStorePublishingListener] can
 * run them after the commit or discard them on rollback.
 */
private fun Configuration.asTxContext(): TxContext<Configuration> =
    object : TxContext<Configuration> {
      override val transaction: Configuration = this@asTxContext

      override fun onCommitEnd(action: () -> Unit) {
        actions(COMMIT_ACTIONS).add(action)
      }

      override fun onRollback(action: () -> Unit) {
        actions(ROLLBACK_ACTIONS).add(action)
      }
    }

private fun Configuration.runActions(key: String) = actions(key).forEach { it() }

@Suppress("UNCHECKED_CAST")
private fun Configuration.actions(key: String): MutableList<() -> Unit> =
    (data(key) as? MutableList<() -> Unit>) ?: mutableListOf<() -> Unit>().also { data(key, it) }
