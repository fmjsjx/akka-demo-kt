package com.hiscene.test.akka.demo.iot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.hiscene.test.akka.demo.iot.DeviceManager.DeviceRegistered
import com.hiscene.test.akka.demo.util.onMessage
import com.hiscene.test.akka.demo.util.onSignal

class DeviceGroup private constructor(
    context: ActorContext<Command>,
    private val groupId: String,
) : AbstractBehavior<DeviceGroup.Command>(context) {

    companion object {
        @JvmStatic
        fun create(groupId: String): Behavior<Command> = Behaviors.setup { DeviceGroup(it, groupId) }
    }

    interface Command

    data class DeviceTerminated(
        val device: ActorRef<Device.Command>,
        val groupId: String,
        var deviceId: String,
    ) : Command

    private val deviceIdToActor: MutableMap<String, ActorRef<Device.Command>> = HashMap()

    init {
        context.log.info("DeviceGroup {} started", groupId)
    }

    override fun createReceive(): Receive<Command> =
        newReceiveBuilder()
            .onMessage(::onTrackDevice)
            .onMessage(::onTerminated)
            .onMessage({ it.groupId == groupId }, ::onDeviceList)
            .onMessage({ it.groupId == groupId }, ::onAllTemperatures)
            .onSignal(::onPostStop)
            .build()

    private fun onTrackDevice(trackMsg: DeviceManager.RequestTrackDevice): DeviceGroup = apply {
        if (groupId == trackMsg.groupId) {
            deviceIdToActor.computeIfAbsent(trackMsg.deviceId) { deviceId ->
                context.log.info("Creating device actor for {}", deviceId)
                context.spawn(Device.create(groupId, deviceId), "device-$deviceId")
                    .also { context.watchWith(it, DeviceTerminated(it, groupId, deviceId)) }
            }.also {
                trackMsg.replyTo.tell(DeviceRegistered(it))
            }
        } else {
            context.log
                .warn("Ignoring TrackDevice request {}. This actor is responsible for group {}.", trackMsg, groupId)
        }
    }

    private fun onDeviceList(command: DeviceManager.RequestDeviceList): DeviceGroup = apply {
        command.replyTo.tell(DeviceManager.ReplyDeviceList(command.requestId, deviceIdToActor.keys))
    }

    private fun onTerminated(command: DeviceTerminated): DeviceGroup = apply {
        context.log.info("Device actor for {} has been terminated", command.deviceId)
        deviceIdToActor -= command.deviceId
    }

    private fun onAllTemperatures(r: DeviceManager.RequestAllTemperatures): DeviceGroup = apply {
        // since Java collections are mutable, we want to avoid sharing them between actors (since
        // multiple Actors (threads)
        // modifying the same mutable data-structure is not safe), and perform a defensive copy of the
        // mutable map:
        //
        // Feel free to use your favourite immutable data-structures library with Akka in Java
        // applications!
        val deviceIdToActorCopy = HashMap(deviceIdToActor)
        context.spawnAnonymous(DeviceGroupQuery.create(deviceIdToActorCopy, r.requestId, r.replyTo))
    }

    private fun onPostStop(signal: PostStop): DeviceGroup = apply {
        context.log.info("DeviceGroup {} stopped <<< {}", groupId, signal)
    }

}