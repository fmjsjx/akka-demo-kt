package com.hiscene.test.akka.demo.util

import akka.actor.typed.javadsl.ActorContext
import akka.japi.function.Function
import kotlin.reflect.KClass

fun <T, U : Any> ActorContext<T>.messageAdapter(messageClass: KClass<U>, fn: Function<U, T>) =
    messageAdapter(messageClass.java, fn)!!

inline fun <T, reified U : Any> ActorContext<T>.messageAdapter(fn: Function<U, T>) =
    messageAdapter(U::class, fn)