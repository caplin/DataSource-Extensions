@file:Suppress("UNCHECKED_CAST")

package com.caplin.integration.datasourcex.util

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.test.logging.info
import io.kotest.matchers.equals.shouldBeEqual
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlinx.collections.immutable.persistentSetOf

@OptIn(ExperimentalKotest::class)
class SerializablePersistentSetTest :
    FunSpec({
      test("serialize persistent set") {
        val set = persistentSetOf("a", "b", "c").serializable()

        val fileOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(fileOutputStream)
        objectOutputStream.writeObject(set)
        objectOutputStream.flush()
        objectOutputStream.close()

        val fileInputStream = ByteArrayInputStream(fileOutputStream.toByteArray())
        val objectInputStream = ObjectInputStream(fileInputStream)
        val set2 = objectInputStream.readObject() as Set<String>
        objectInputStream.close()

        info { set2 }

        set2.zip(set).forEach { (copy, original) -> copy shouldBeEqual original }
      }
    })
