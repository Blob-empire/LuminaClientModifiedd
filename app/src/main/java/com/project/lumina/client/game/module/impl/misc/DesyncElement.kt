package com.project.lumina.client.game.module.impl.misc

import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import com.project.lumina.client.game.module.api.setting.intValue
import com.project.lumina.client.game.module.api.setting.stringValue
import com.project.lumina.client.util.AssetManager
import kotlinx.coroutines.*
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

class DesyncElement(
    iconResId: Int = AssetManager.getAsset("ic_timer_sand_black_24dp")
) : Element(
    name = "Desync",
    category = CheatCategory.Misc,
    iconResId = iconResId,
    displayNameResId = AssetManager.getString("module_desync_display_name")
) {

    private val packetQueue = ConcurrentLinkedQueue<PlayerAuthInputPacket>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var flushingJob: Job? = null
    private var desynced = false

    private var mode by stringValue(
        this,
        "Mode",
        "Jitter",
        listOf("Jitter", "Freeze", "Pulse")
    )

    private var maxQueue by intValue(this, "MaxQueue", 120, 20..400)
    private var minDelay by intValue(this, "MinDelay", 35, 0..500)
    private var maxDelay by intValue(this, "MaxDelay", 90, 0..1000)
    private var burstSize by intValue(this, "Burst", 6, 1..20)

    override fun onEnabled() {
        super.onEnabled()

        if (!isSessionCreated) return

        desynced = true
        packetQueue.clear()
    }

    override fun onDisabled() {
        super.onDisabled()

        desynced = false
        flushQueue()
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !desynced) return

        val packet = interceptablePacket.packet

        if (packet is PlayerAuthInputPacket) {

            /*
             * Prevent queue overflow
             */
            if (packetQueue.size >= maxQueue) {
                flushPartial()
            }

            when (mode.lowercase()) {

                /*
                 * Complete freeze
                 */
                "freeze" -> {
                    packetQueue.add(packet)
                    interceptablePacket.intercept()
                }

                /*
                 * Randomized packet choke
                 */
                "jitter" -> {

                    val shouldDrop =
                        Random.nextFloat() > 0.35f

                    if (shouldDrop) {
                        packetQueue.add(packet)
                        interceptablePacket.intercept()
                    }
                }

                /*
                 * Sends in bursts
                 */
                "pulse" -> {

                    packetQueue.add(packet)
                    interceptablePacket.intercept()

                    if (packetQueue.size >= burstSize) {
                        flushPartial()
                    }
                }
            }
        }
    }

    private fun flushPartial() {

        if (flushingJob?.isActive == true) return

        flushingJob = scope.launch {

            val sendAmount =
                minOf(packetQueue.size, burstSize)

            repeat(sendAmount) {

                val packet = packetQueue.poll() ?: return@repeat

                session.clientBound(packet)

                delay(
                    Random.nextLong(
                        minDelay.toLong(),
                        maxDelay.toLong()
                    )
                )
            }
        }
    }

    private fun flushQueue() {

        flushingJob?.cancel()

        flushingJob = scope.launch {

            while (packetQueue.isNotEmpty()) {

                val packet = packetQueue.poll() ?: continue

                session.clientBound(packet)

                delay(
                    Random.nextLong(
                        minDelay.toLong(),
                        maxDelay.toLong()
                    )
                )
            }
        }
    }
}
