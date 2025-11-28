@file:Suppress("JavaDefaultMethodsNotOverriddenByDelegation")

package com.hiscene.test.akka.demo.iot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.hiscene.test.akka.demo.util.*
import java.util.OptionalDouble

class Device private constructor(
    context: ActorContext<Command>,
    val groupId: String,
    val deviceId: String,
) : AbstractBehavior<Device.Command>(context) {

    companion object {
        @JvmStatic
        fun create(groupId: String, deviceId: String): Behavior<Command> =
            Behaviors.setup { Device(it, groupId, deviceId) }
    }

    private var lastTemperatureReading: OptionalDouble = OptionalDouble.empty()

    init {
        context.log.info("Device actor {}-{} started", groupId, deviceId)
    }

    interface Command

    data class ReadTemperature(val requestId: Long, val replyTo: ActorRef<RespondTemperature>) : Command

    data class RespondTemperature(val requestId: Long, val deviceId: String, val value: OptionalDouble)

    data class RecordTemperature(
        val requestId: Long,
        val value: Double,
        val replyTo: ActorRef<TemperatureRecorded>,
    ) : Command {
        fun toRecorded() = TemperatureRecorded(requestId)
    }

    data class TemperatureRecorded(val requestId: Long)

    object Passivate : Command

    override fun createReceive(): Receive<Command> =
        newReceiveBuilder()
            .onMessage(::onReadTemperature)
            .onMessage(::onRecordTemperature)
            .onMessage(Passivate::class) { Behaviors.stopped() }
            .onSignal(::onPostStop)
            .build()

    private fun onRecordTemperature(command: RecordTemperature): Behavior<Command> = apply {
        context.log.info("Recorded temperature received: {}", command)
        lastTemperatureReading = OptionalDouble.of(command.value)
        command.replyTo.tell(command.toRecorded())
    }

    private fun onReadTemperature(command: ReadTemperature): Behavior<Command> = apply {
        command.replyTo.tell(RespondTemperature(command.requestId, deviceId, lastTemperatureReading))
    }

    private fun onPostStop(signal: PostStop): Device = apply {
        context.log.info("Device actor {}-{} stopped <<< {}", groupId, deviceId, signal)
    }

}