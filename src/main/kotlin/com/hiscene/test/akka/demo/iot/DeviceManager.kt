package com.hiscene.test.akka.demo.iot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.hiscene.test.akka.demo.util.onMessage
import com.hiscene.test.akka.demo.util.onSignal

class DeviceManager private constructor(
    context: ActorContext<Command>,
) : AbstractBehavior<DeviceManager.Command>(context) {

    companion object {
        @JvmStatic
        fun create(): Behavior<Command> = Behaviors.setup(::DeviceManager)

    }

    interface Command

    data class RequestTrackDevice(
        val groupId: String,
        val deviceId: String,
        val replyTo: ActorRef<DeviceRegistered>,
    ) : Command, DeviceGroup.Command

    data class DeviceRegistered(
        val device: ActorRef<Device.Command>,
    )

    data class RequestDeviceList(
        val requestId: Long,
        val groupId: String,
        val replyTo: ActorRef<ReplyDeviceList>,
    ) : Command, DeviceGroup.Command

    data class ReplyDeviceList(
        val requestId: Long,
        val ids: Set<String>,
    )

    private data class DeviceGroupTerminated(val groupId: String) : Command

    data class RequestAllTemperatures(
        val requestId: Long,
        val groupId: String,
        val replyTo: ActorRef<RespondAllTemperatures>,
    ) : DeviceGroupQuery.Command, DeviceGroup.Command, Command

    data class RespondAllTemperatures(
        val requestId: Long,
        val temperatures: Map<String, TemperatureReading>,
    )

    interface TemperatureReading

    data class Temperature(
        val value: Double,
    ) : TemperatureReading

    object TemperatureNotAvailable : TemperatureReading
    object DeviceNotAvailable: TemperatureReading
    object DeviceTimedOut: TemperatureReading

    private val groupIdToActor: MutableMap<String, ActorRef<DeviceGroup.Command>> = HashMap()

    init {
        context.log.info("DeviceManager started")
    }

    override fun createReceive(): Receive<Command> =
        newReceiveBuilder()
            .onMessage(::onTrackDevice)
            .onMessage(::onRequestDeviceList)
            .onMessage(::onTerminated)
            .onSignal(::onPostStop)
            .build()

    private fun onTrackDevice(trackMsg: RequestTrackDevice): DeviceManager = apply {
        groupIdToActor.computeIfAbsent(trackMsg.groupId) { groupId ->
            context.log.info("Creating device group actor for {}", groupId)
            context.spawn(DeviceGroup.create(groupId), "group-$groupId").also {
                context.watchWith(it, DeviceGroupTerminated(groupId))
            }
        }.tell(trackMsg)
    }

    private fun onRequestDeviceList(request: RequestDeviceList): DeviceManager = apply {
        val ref = groupIdToActor[request.groupId]
        if (ref != null) {
            ref.tell(request)
        } else {
            request.replyTo.tell(ReplyDeviceList(request.requestId, emptySet()))
        }
    }

    private fun onTerminated(command: DeviceGroupTerminated): DeviceManager = apply {
        context.log.info("Device group actor for {} has been terminated", command.groupId)
        groupIdToActor -= command.groupId
    }

    private fun onPostStop(signal: PostStop): DeviceManager = apply {
        context.log.info("DeviceManager stopped <<< {}", signal)
    }

}