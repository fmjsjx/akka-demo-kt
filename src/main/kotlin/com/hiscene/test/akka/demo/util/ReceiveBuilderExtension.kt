package com.hiscene.test.akka.demo.util

import akka.actor.typed.Behavior
import akka.actor.typed.Signal
import akka.actor.typed.javadsl.ReceiveBuilder
import akka.japi.function.Function
import akka.japi.function.Predicate
import kotlin.reflect.KClass

fun <T : Any, M : T> ReceiveBuilder<T>.onMessage(type: KClass<M>, handler: Function<M, Behavior<T>>) =
    onMessage(type.java, handler)!!

inline fun <T : Any, reified M : T> ReceiveBuilder<T>.onMessage(handler: Function<M, Behavior<T>>) =
    onMessage(M::class, handler)

fun <T : Any, M : T> ReceiveBuilder<T>.onMessage(type: KClass<M>, test: Predicate<M>, handler: Function<M, Behavior<T>>) =
    onMessage(type.java, test, handler)!!

inline fun <T : Any, reified M : T> ReceiveBuilder<T>.onMessage(test: Predicate<M>, handler: Function<M, Behavior<T>>) =
    onMessage(M::class, test, handler)

fun <T : Any, M : Signal> ReceiveBuilder<T>.onSignal(type: KClass<M>, handler: Function<M, Behavior<T>>) =
    onSignal(type.java, handler)!!

inline fun <T : Any, reified M : Signal> ReceiveBuilder<T>.onSignal(handler: Function<M, Behavior<T>>) =
    onSignal(M::class, handler)