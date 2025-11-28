package com.hiscene.test.akka.demo.iot


import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.actor.typed.javadsl.TimerScheduler
import com.hiscene.test.akka.demo.util.messageAdapter
import com.hiscene.test.akka.demo.util.onMessage
import java.time.Duration

class DeviceGroupQuery private constructor(
    deviceIdToActor: Map<String, ActorRef<Device.Command>>,
    timeout: Duration,
    timers: TimerScheduler<Command>,
    context: ActorContext<Command>,
    private val requestId: Long,
    private val requester: ActorRef<DeviceManager.RespondAllTemperatures>,
) : AbstractBehavior<DeviceGroupQuery.Command>(context) {

    companion object {
        @JvmStatic
        fun create(
            deviceIdToActor: Map<String, ActorRef<Device.Command>>,
            requestId: Long,
            requester: ActorRef<DeviceManager.RespondAllTemperatures>,
            timeout: Duration = Duration.ofSeconds(3),
        ): Behavior<Command> = Behaviors.setup { context ->
            Behaviors.withTimers { timers ->
                DeviceGroupQuery(deviceIdToActor, timeout, timers, context, requestId, requester)
            }
        }

        private object CollectionTimeout : Command
    }

    interface Command

    data class WrappedRespondTemperature(
        val response: Device.RespondTemperature,
    ) : Command

    private data class DeviceTerminated(
        val deviceId: String,
    ) : Command

    private val repliesSoFar: MutableMap<String, DeviceManager.TemperatureReading> = HashMap()
    private val stillWaiting: MutableSet<String>

    init {
        timers.startSingleTimer(CollectionTimeout, timeout)

        val respondTemperatureAdapter = context.messageAdapter(::WrappedRespondTemperature)

        deviceIdToActor.forEach { (deviceId, actorRef) ->
            context.watchWith(actorRef, DeviceTerminated(deviceId))
            actorRef.tell(Device.ReadTemperature(0L, respondTemperatureAdapter))
        }
        stillWaiting = HashSet(deviceIdToActor.keys)
    }

    override fun createReceive(): Receive<Command> =
        newReceiveBuilder()
            .onMessage(::onRespondTemperature)
            .onMessage(::onDeviceTerminated)
            .onMessage(::onCollectionTimeout)
            .build()

    private fun respondWhenAllCollected(): Behavior<Command> =
        if (stillWaiting.isEmpty()) {
            requester.tell(DeviceManager.RespondAllTemperatures(requestId, repliesSoFar))
            Behaviors.stopped()
        } else {
            this
        }

    private fun onRespondTemperature(command: WrappedRespondTemperature): Behavior<Command> {
        val reading = command.response.value.let {
            if (it.isPresent) {
                DeviceManager.Temperature(it.asDouble)
            } else {
                DeviceManager.TemperatureNotAvailable
            }
        }
        command.response.deviceId.also { deviceId ->
            repliesSoFar[deviceId] = reading
            stillWaiting -= deviceId
        }
        return respondWhenAllCollected()
    }

    private fun onDeviceTerminated(terminated: DeviceTerminated): Behavior<Command> {
        terminated.deviceId.also { deviceId ->
            if (deviceId in stillWaiting) {
                repliesSoFar[deviceId] = DeviceManager.DeviceNotAvailable
                stillWaiting -= deviceId
            }
        }
        return respondWhenAllCollected()
    }

    private fun onCollectionTimeout(timeout: CollectionTimeout): Behavior<Command> {
        context.log.warn("Collection timeout <<< {}", timeout)
        stillWaiting.forEach { deviceId ->
            repliesSoFar[deviceId] = DeviceManager.DeviceTimedOut
        }
        stillWaiting.clear()
        return respondWhenAllCollected()
    }

}