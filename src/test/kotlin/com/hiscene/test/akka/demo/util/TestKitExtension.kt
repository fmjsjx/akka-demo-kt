package com.hiscene.test.akka.demo.util

import akka.actor.testkit.typed.javadsl.ActorTestKit
import akka.actor.testkit.typed.javadsl.TestProbe

inline fun <reified M> ActorTestKit.testProbe(): TestProbe<M> =
    createTestProbe(M::class.java)

