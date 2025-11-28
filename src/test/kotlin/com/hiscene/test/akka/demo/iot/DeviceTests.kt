package com.hiscene.test.akka.demo.iot

import akka.actor.testkit.typed.javadsl.ActorTestKit
import com.hiscene.test.akka.demo.util.testProbe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.OptionalDouble


class DeviceTests {

    companion object {

        lateinit var testKit: ActorTestKit

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            testKit = ActorTestKit.create("test")
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            testKit.shutdownTestKit()
        }

    }

    @Test
    fun testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
        val probe = testKit.testProbe<Device.RespondTemperature>()
        val deviceActor = testKit.spawn(Device.create("group", "device"))
        deviceActor.tell(Device.ReadTemperature(42, probe.ref))
        val response = probe.receiveMessage()
        assertEquals(42, response.requestId)
        assertEquals(OptionalDouble.empty(), response.value)
    }

    @Test
    fun testReplyWithLatestTemperatureReading() {
        val recordProbe = testKit.testProbe<Device.TemperatureRecorded>()
        val readProbe = testKit.testProbe<Device.RespondTemperature>()
        val deviceActor = testKit.spawn(Device.create("group", "device"))

        deviceActor.tell(Device.RecordTemperature(1, 24.0, recordProbe.ref))
        assertEquals(1, recordProbe.receiveMessage().requestId)

        deviceActor.tell(Device.ReadTemperature(2, readProbe.ref))
        readProbe.receiveMessage().also {
            assertEquals(2, it.requestId)
            assertEquals(OptionalDouble.of(24.0), it.value)
        }

        deviceActor.tell(Device.RecordTemperature(3, 55.0, recordProbe.ref))
        assertEquals(3, recordProbe.receiveMessage().requestId)

        deviceActor.tell(Device.ReadTemperature(4, readProbe.ref))
        readProbe.receiveMessage().also {
            assertEquals(4, it.requestId)
            assertEquals(OptionalDouble.of(55.0), it.value)
        }
    }

    @Test
    fun testReplyToRegistrationRequests() {
        val probe = testKit.testProbe<DeviceManager.DeviceRegistered>()
        val groupActor = testKit.spawn(DeviceGroup.create("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device", probe.ref))
        val registered1 = probe.receiveMessage()

        // another deviceId
        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device3", probe.ref))
        val registered2 = probe.receiveMessage()
        assertNotEquals(registered1.device, registered2.device)

        // Check that the device actors are working
        val recordProbe = testKit.testProbe<Device.TemperatureRecorded>()
        registered1.device.tell(Device.RecordTemperature(1, 24.0, recordProbe.ref))
        assertEquals(1, recordProbe.receiveMessage().requestId)
        registered2.device.tell(Device.RecordTemperature(2, 26.0, recordProbe.ref))
        assertEquals(2, recordProbe.receiveMessage().requestId)
    }

    @Test
    fun testIgnoreWrongRegistrationRequests() {
        val probe = testKit.testProbe<DeviceManager.DeviceRegistered>()
        val groupActor = testKit.spawn(DeviceGroup.create("group"))
        groupActor.tell(DeviceManager.RequestTrackDevice("wrongGroup", "device1", probe.ref))
        probe.expectNoMessage()
    }

    @Test
    fun testReturnSameActorForSameDeviceId() {
        val probe = testKit.testProbe<DeviceManager.DeviceRegistered>()
        val groupActor = testKit.spawn(DeviceGroup.create("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device", probe.ref))
        val registered1 = probe.receiveMessage()

        // registering same again should be idempotent
        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device", probe.ref))
        val registered2 = probe.receiveMessage()
        assertEquals(registered1.device, registered2.device)
    }

    @Test
    fun testListActiveDevices() {
        val registeredProbe = testKit.testProbe<DeviceManager.DeviceRegistered>()
        val groupActor = testKit.spawn(DeviceGroup.create("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1", registeredProbe.ref))
        registeredProbe.receiveMessage()

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2", registeredProbe.ref))
        registeredProbe.receiveMessage()

        val deviceListProbe = testKit.testProbe<DeviceManager.ReplyDeviceList>()

        groupActor.tell(DeviceManager.RequestDeviceList(1, "group", deviceListProbe.ref))
        deviceListProbe.receiveMessage().also {
            assertEquals(1L, it.requestId)
            assertArrayEquals(arrayOf("device1", "device2"), it.ids.sorted().toTypedArray())
        }
    }

    @Test
    fun testListActiveDevicesAfterOneShutsDown() {
        val registeredProbe = testKit.testProbe<DeviceManager.DeviceRegistered>()
        val groupActor = testKit.spawn(DeviceGroup.create("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1", registeredProbe.ref))
        val registered1 = registeredProbe.receiveMessage()

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2", registeredProbe.ref))
        registeredProbe.receiveMessage()

        val toShotDown = registered1.device

        val deviceListProbe = testKit.testProbe<DeviceManager.ReplyDeviceList>()

        groupActor.tell(DeviceManager.RequestDeviceList(1, "group", deviceListProbe.ref))
        deviceListProbe.receiveMessage().also {
            assertEquals(1L, it.requestId)
            assertArrayEquals(arrayOf("device1", "device2"), it.ids.sorted().toTypedArray())
        }

        toShotDown.tell(Device.Passivate)
        registeredProbe.expectTerminated(toShotDown, registeredProbe.remainingOrDefault)

        registeredProbe.awaitAssert {
            groupActor.tell(DeviceManager.RequestDeviceList(2, "group", deviceListProbe.ref))
            deviceListProbe.receiveMessage().also {
                assertEquals(2, it.requestId)
                assertArrayEquals(arrayOf("device2"), it.ids.sorted().toTypedArray())
            }
        }
    }

    @Test
    fun testReturnTemperatureValueForWorkingDevices() {
        val requester = testKit.testProbe<DeviceManager.RespondAllTemperatures>()
        val device1 = testKit.testProbe<Device.Command>()
        val device2 = testKit.testProbe<Device.Command>()

        val deviceIdToActor = mapOf(
            "device1" to device1.ref,
            "device2" to device2.ref,
        )

        val queryActor = testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref))

        assertEquals(0, device1.expectMessageClass(Device.ReadTemperature::class.java).requestId)
        assertEquals(0, device2.expectMessageClass(Device.ReadTemperature::class.java).requestId)

        queryActor.tell(
            DeviceGroupQuery.WrappedRespondTemperature(
                Device.RespondTemperature(
                    0,
                    "device1",
                    OptionalDouble.of(1.0),
                )
            )
        )

        queryActor.tell(
            DeviceGroupQuery.WrappedRespondTemperature(
                Device.RespondTemperature(
                    0,
                    "device2",
                    OptionalDouble.of(2.0),
                )
            )
        )

        requester.receiveMessage().also { response ->
            assertEquals(1L, response.requestId)
            val expectedTemperatures = mapOf(
                "device1" to DeviceManager.Temperature(1.0),
                "device2" to DeviceManager.Temperature(2.0),
            )
            assertEquals(expectedTemperatures, response.temperatures)
        }
    }

    @Test
    fun testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
        val requester = testKit.testProbe<DeviceManager.RespondAllTemperatures>()
        val device1 = testKit.testProbe<Device.Command>()
        val device2 = testKit.testProbe<Device.Command>()

        val deviceIdToActor = mapOf(
            "device1" to device1.ref,
            "device2" to device2.ref,
        )

        val queryActor = testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref))

        assertEquals(0, device1.expectMessageClass(Device.ReadTemperature::class.java).requestId)
        assertEquals(0, device2.expectMessageClass(Device.ReadTemperature::class.java).requestId)

        queryActor.tell(
            DeviceGroupQuery.WrappedRespondTemperature(
                Device.RespondTemperature(
                    0,
                    "device1",
                    OptionalDouble.empty(),
                )
            )
        )

        queryActor.tell(
            DeviceGroupQuery.WrappedRespondTemperature(
                Device.RespondTemperature(
                    0,
                    "device2",
                    OptionalDouble.of(2.0),
                )
            )
        )

        requester.receiveMessage().also { response ->
            assertEquals(1L, response.requestId)
            val expectedTemperatures = mapOf(
                "device1" to DeviceManager.TemperatureNotAvailable,
                "device2" to DeviceManager.Temperature(2.0),
            )
            assertEquals(expectedTemperatures, response.temperatures)
        }
    }

    @Test
    fun testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {
        val requester = testKit.testProbe<DeviceManager.RespondAllTemperatures>()
        val device1 = testKit.testProbe<Device.Command>()
        val device2 = testKit.testProbe<Device.Command>()

        val deviceIdToActor = mapOf(
            "device1" to device1.ref,
            "device2" to device2.ref,
        )

        val queryActor = testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref))

        assertEquals(0, device1.expectMessageClass(Device.ReadTemperature::class.java).requestId)
        assertEquals(0, device2.expectMessageClass(Device.ReadTemperature::class.java).requestId)

        queryActor.tell(
            DeviceGroupQuery.WrappedRespondTemperature(
                Device.RespondTemperature(
                    0,
                    "device1",
                    OptionalDouble.of(1.0),
                )
            )
        )

        device2.stop()

        requester.receiveMessage().also { response ->
            assertEquals(1L, response.requestId)
            val expectedTemperatures = mapOf(
                "device1" to DeviceManager.Temperature(1.0),
                "device2" to DeviceManager.DeviceNotAvailable,
            )
            assertEquals(expectedTemperatures, response.temperatures)
        }
    }

    @Test
    fun testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {
        val requester = testKit.testProbe<DeviceManager.RespondAllTemperatures>()
        val device1 = testKit.testProbe<Device.Command>()
        val device2 = testKit.testProbe<Device.Command>()

        val deviceIdToActor = mapOf(
            "device1" to device1.ref,
            "device2" to device2.ref,
        )

        val queryActor = testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref))

        assertEquals(0, device1.expectMessageClass(Device.ReadTemperature::class.java).requestId)
        assertEquals(0, device2.expectMessageClass(Device.ReadTemperature::class.java).requestId)

        queryActor.tell(
            DeviceGroupQuery.WrappedRespondTemperature(
                Device.RespondTemperature(
                    0,
                    "device1",
                    OptionalDouble.of(1.0),
                )
            )
        )

        queryActor.tell(
            DeviceGroupQuery.WrappedRespondTemperature(
                Device.RespondTemperature(
                    0,
                    "device2",
                    OptionalDouble.of(2.0),
                )
            )
        )

        device2.stop()

        requester.receiveMessage().also { response ->
            assertEquals(1L, response.requestId)
            val expectedTemperatures = mapOf(
                "device1" to DeviceManager.Temperature(1.0),
                "device2" to DeviceManager.Temperature(2.0),
            )
            assertEquals(expectedTemperatures, response.temperatures)
        }
    }

    @Test
    fun testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
        val requester = testKit.testProbe<DeviceManager.RespondAllTemperatures>()
        val device1 = testKit.testProbe<Device.Command>()
        val device2 = testKit.testProbe<Device.Command>()

        val deviceIdToActor = mapOf(
            "device1" to device1.ref,
            "device2" to device2.ref,
        )

        val queryActor =
            testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref, Duration.ofMillis(200)))

        assertEquals(0, device1.expectMessageClass(Device.ReadTemperature::class.java).requestId)
        assertEquals(0, device2.expectMessageClass(Device.ReadTemperature::class.java).requestId)

        queryActor.tell(
            DeviceGroupQuery.WrappedRespondTemperature(
                Device.RespondTemperature(
                    0,
                    "device1",
                    OptionalDouble.of(1.0),
                )
            )
        )

        // no reply from device2

        requester.receiveMessage().also { response ->
            assertEquals(1L, response.requestId)
            val expectedTemperatures = mapOf(
                "device1" to DeviceManager.Temperature(1.0),
                "device2" to DeviceManager.DeviceTimedOut,
            )
            assertEquals(expectedTemperatures, response.temperatures)
        }
    }

    @Test
    fun testCollectTemperaturesFromAllActiveDevices() {
        val registeredProbe = testKit.testProbe<DeviceManager.DeviceRegistered>()
        val groupActor = testKit.spawn(DeviceGroup.create("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1", registeredProbe.ref))
        val deviceActor1 = registeredProbe.receiveMessage().device

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2", registeredProbe.ref))
        val deviceActor2 = registeredProbe.receiveMessage().device

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device3", registeredProbe.ref))
        @Suppress("UnusedVariable", "unused") val deviceActor3 = registeredProbe.receiveMessage().device

        // Check that the device actors are working
        val recordProbe = testKit.testProbe<Device.TemperatureRecorded>()
        deviceActor1.tell(Device.RecordTemperature(0, 1.0, recordProbe.ref))
        assertEquals(0L, recordProbe.receiveMessage().requestId)
        deviceActor2.tell(Device.RecordTemperature(1, 2.0, recordProbe.ref))
        assertEquals(1L, recordProbe.receiveMessage().requestId)
        // No temperature for device 3

        val allTempProbe = testKit.testProbe<DeviceManager.RespondAllTemperatures>()
        groupActor.tell(DeviceManager.RequestAllTemperatures(0, "group", allTempProbe.ref))
        allTempProbe.receiveMessage().also { response ->
            assertEquals(0L, response.requestId)

            val expectedTemperatures = mapOf(
                "device1" to DeviceManager.Temperature(1.0),
                "device2" to DeviceManager.Temperature(2.0),
                "device3" to DeviceManager.TemperatureNotAvailable,
            )

            assertEquals(expectedTemperatures, response.temperatures)
        }
    }

}