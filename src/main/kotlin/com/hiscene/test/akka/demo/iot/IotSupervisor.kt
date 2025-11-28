package com.hiscene.test.akka.demo.iot

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.hiscene.test.akka.demo.util.onSignal

class IotSupervisor private constructor(context: ActorContext<Unit>): AbstractBehavior<Unit>(context) {

    companion object {
        @JvmStatic
        fun create(): Behavior<Unit> = Behaviors.setup(::IotSupervisor)
    }

    init {
        context.log.info("IoT Application started")
    }

    override fun createReceive(): Receive<Unit> =
        newReceiveBuilder()
            .onSignal(::onPostStop)
            .build()

    private fun onPostStop(signal : PostStop): IotSupervisor = apply {
        context.log.info("IoT Application stopped <<< {}", signal)
    }

}