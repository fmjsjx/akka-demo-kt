package com.hiscene.test.akka.demo

import akka.actor.typed.ActorSystem
import com.hiscene.test.akka.demo.iot.IotSupervisor
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class AkkaRunner : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        // Create ActorSystem and top level supervisor
        ActorSystem.create(IotSupervisor.create(), "iot-system")
    }

}