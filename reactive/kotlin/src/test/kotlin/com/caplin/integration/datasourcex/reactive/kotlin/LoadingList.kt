package com.caplin.integration.datasourcex.reactive.kotlin

class LoadingList<E>(
    private val backingList: MutableList<E> = mutableListOf(),
    private val loader: () -> E
) : List<E> by backingList {

  fun next(): E = loader().also(backingList::add)
}
