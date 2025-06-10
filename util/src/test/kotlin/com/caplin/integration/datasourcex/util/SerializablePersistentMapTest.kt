@file:Suppress("UNCHECKED_CAST")

package com.caplin.integration.datasourcex.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

class SerializablePersistentMapTest :
    FunSpec({
      test("serialize persistent map") {
        val map = persistentMapOf("a" to 1, "b" to 3, "c" to "z").serializable()

        val fileOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(fileOutputStream)
        objectOutputStream.writeObject(map)
        objectOutputStream.flush()
        objectOutputStream.close()

        val fileInputStream = ByteArrayInputStream(fileOutputStream.toByteArray())
        val objectInputStream = ObjectInputStream(fileInputStream)
        val map2 = objectInputStream.readObject() as PersistentMap<String, Int>
        objectInputStream.close()

        println(map2)

        map2.entries.zip(map.entries).forEach { (copy, original) ->
          copy.key shouldBeEqual original.key
          copy.value shouldBeEqual original.value
        }
      }
    })
