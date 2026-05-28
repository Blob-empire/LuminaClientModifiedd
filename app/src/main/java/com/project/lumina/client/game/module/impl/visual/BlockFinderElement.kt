/*
 * © Project Lumina 2026 — GPLv3 Licensed
 * You may use, modify, and share this code under the GPL.
 *
 * Just know: changing names and colors doesn't make you a developer.
 * Think before you fork. Build something real — or don't bother.
 */

package com.project.lumina.client.game.module.impl.visual

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.CornerPathEffect
import com.project.lumina.client.game.InterceptablePacket
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.render.ESPRenderOverlayView
import com.project.lumina.client.overlay.manager.OverlayManager
import com.project.lumina.client.R
import com.project.lumina.client.util.AssetManager
import com.project.lumina.client.game.module.api.setting.stringValue
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket
import kotlin.math.cos
import kotlin.math.sin

class BlockFinderElement(
    iconResId: Int = AssetManager.getAsset("ic_brand_finder")
) : Element(
    name = "BlockFinder",
    category = CheatCategory.Visual,
    iconResId = iconResId,
    displayNameResId = AssetManager.getString("module_block_finder_display_name")
) {
    companion object {
        private var renderView: ESPRenderOverlayView? = null

        fun setRenderView(view: ESPRenderOverlayView) {
            renderView = view
        }
    }

    // Block type names for selection
    private val blockTypes = listOf(
        "Chest",
        "Trapped Chest",
        "Barrel",
        "Ender Chest",
        "Furnace",
        "Blast Furnace",
        "Smoker",
        "Hopper",
        "Dispenser",
        "Dropper",
        "Brewing Stand"
    )

    private var selectedBlockType by stringValue(
        "Block Type",
        "Chest",
        blockTypes
    )

    private var selectedBlockIndex = 0
    private var detectedBlocks = mutableListOf<BlockData>()
    private var currentBlockSelection = -1

    private val fov by floatValue("FOV", 110f, 40f..110f)
    private val strokeWidth by floatValue("Stroke Width", 2f, 1f..10f)
    private val cornerRadius by floatValue("Corner Radius", 4f, 0f..20f)
    private val scanRadius by intValue("Scan Radius", 64, 10..256)

    private var lastUpdateTime = 0L

    data class BlockData(
        val x: Int,
        val y: Int,
        val z: Int,
        val blockName: String
    )

    override fun onEnabled() {
        super.onEnabled()

        if (!isSessionCreated) return

        if (renderView == null) {
            renderView = ESPRenderOverlayView.createAndShow()
            BlockFinderElement.setRenderView(renderView!!)
        }
        renderView?.post {
            renderView?.invalidate()
        }

        scanForBlocks()
    }

    override fun onDisabled() {
        super.onDisabled()
        renderView?.let {
            ESPRenderOverlayView.dismissOverlay(it)
            renderView = null
        }
        detectedBlocks.clear()
        currentBlockSelection = -1
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        if (packet is PlayerAuthInputPacket) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > 1000) {
                scanForBlocks()
                lastUpdateTime = currentTime
            }
        }
    }

    private fun scanForBlocks() {
        if (!isSessionCreated) return

        detectedBlocks.clear()
        val player = session.localPlayer
        val world = session.world
        val playerX = player.posX.toInt()
        val playerY = player.posY.toInt()
        val playerZ = player.posZ.toInt()

        // Scan in a cube around the player
        for (x in (playerX - scanRadius)..(playerX + scanRadius)) {
            for (z in (playerZ - scanRadius)..(playerZ + scanRadius)) {
                for (y in (playerY - 64)..(playerY + 64)) {
                    val blockId = world.getBlockIdAt(
                        org.cloudburstmc.math.vector.Vector3i.from(x, y, z)
                    )

                    // Check if block matches selected type
                    if (matchesBlockType(blockId)) {
                        detectedBlocks.add(
                            BlockData(x, y, z, selectedBlockType)
                        )
                    }
                }
            }
        }
    }

    private fun matchesBlockType(blockId: Int): Boolean {
        // Map block IDs to their types
        // These are approximate Minecraft block IDs
        return when (selectedBlockType) {
            "Chest" -> blockId in 54..54 || blockId in 146..146
            "Trapped Chest" -> blockId in 145..145
            "Barrel" -> blockId in 458..458
            "Ender Chest" -> blockId in 130..130
            "Furnace" -> blockId in 61..61
            "Blast Furnace" -> blockId in 451..451
            "Smoker" -> blockId in 452..452
            "Hopper" -> blockId in 154..154
            "Dispenser" -> blockId in 23..23
            "Dropper" -> blockId in 158..158
            "Brewing Stand" -> blockId in 117..117
            else -> false
        }
    }

    private fun rotateX(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()

        return Matrix4f.from(
            1f, 0f, 0f, 0f,
            0f, c, -s, 0f,
            0f, s, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun rotateY(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()

        return Matrix4f.from(
            c, 0f, s, 0f,
            0f, 1f, 0f, 0f,
            -s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun getBlockBoxVertices(blockData: BlockData): Array<Vector3f> {
        val x = blockData.x.toFloat()
        val y = blockData.y.toFloat()
        val z = blockData.z.toFloat()

        return arrayOf(
            Vector3f.from(x, y, z),
            Vector3f.from(x, y + 1, z),
            Vector3f.from(x + 1, y + 1, z),
            Vector3f.from(x + 1, y, z),
            Vector3f.from(x, y, z + 1),
            Vector3f.from(x, y + 1, z + 1),
            Vector3f.from(x + 1, y + 1, z + 1),
            Vector3f.from(x + 1, y, z + 1)
        )
    }

    private fun worldToScreen(
        pos: Vector3f,
        viewProjMatrix: Matrix4f,
        screenWidth: Int,
        screenHeight: Int
    ): Vector2f? {
        val w = viewProjMatrix.get(3, 0) * pos.x +
                viewProjMatrix.get(3, 1) * pos.y +
                viewProjMatrix.get(3, 2) * pos.z +
                viewProjMatrix.get(3, 3)

        if (w < 0.01f) return null

        val inverseW = 1f / w

        val screenX = screenWidth / 2f + (0.5f * ((viewProjMatrix.get(0, 0) * pos.x +
                viewProjMatrix.get(0, 1) * pos.y +
                viewProjMatrix.get(0, 2) * pos.z +
                viewProjMatrix.get(0, 3)) * inverseW) * screenWidth + 0.5f)

        val screenY = screenHeight / 2f - (0.5f * ((viewProjMatrix.get(1, 0) * pos.x +
                viewProjMatrix.get(1, 1) * pos.y +
                viewProjMatrix.get(1, 2) * pos.z +
                viewProjMatrix.get(1, 3)) * inverseW) * screenHeight + 0.5f)

        return Vector2f.from(screenX, screenY)
    }

    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated || detectedBlocks.isEmpty()) return

        val player = session.localPlayer
        val screenWidth = canvas.width
        val screenHeight = canvas.height

        val viewProjMatrix = Matrix4f.createPerspective(
            fov,
            screenWidth.toFloat() / screenHeight,
            0.1f,
            512f
        ).mul(
            Matrix4f.createTranslation(player.vec3Position)
                .mul(rotateY(-player.rotationYaw - 180))
                .mul(rotateX(-player.rotationPitch))
                .invert()
        )

        detectedBlocks.forEachIndexed { index, block ->
            val isSelected = index == currentBlockSelection
            val paint = createBlockPaint(isSelected)

            drawBlockBox(block, viewProjMatrix, screenWidth, screenHeight, canvas, paint)
        }
    }

    private fun createBlockPaint(isSelected: Boolean): Paint {
        return Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = this@BlockFinderElement.strokeWidth
            isAntiAlias = true
            isDither = true
            pathEffect = CornerPathEffect(cornerRadius)
            color = if (isSelected) {
                Color.argb(255, 255, 105, 180) // Pink for selected
            } else {
                Color.argb(255, 255, 0, 0) // Red for not selected
            }
        }
    }

    private fun drawBlockBox(
        block: BlockData,
        viewProjMatrix: Matrix4f,
        screenWidth: Int,
        screenHeight: Int,
        canvas: Canvas,
        paint: Paint
    ) {
        val vertices = getBlockBoxVertices(block)
        val screenPositions = mutableListOf<Vector2f>()

        vertices.forEach { vertex ->
            val screenPos = worldToScreen(vertex, viewProjMatrix, screenWidth, screenHeight)
                ?: return@forEach
            screenPositions.add(screenPos)
        }

        if (screenPositions.size < 8) return

        // Draw all 12 edges of the box
        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )

        edges.forEach { (start, end) ->
            val startPos = screenPositions[start]
            val endPos = screenPositions[end]

            if (isOnScreen(startPos, canvas) && isOnScreen(endPos, canvas)) {
                val padding = paint.strokeWidth / 2
                canvas.drawLine(
                    startPos.x.coerceIn(padding, canvas.width - padding),
                    startPos.y.coerceIn(padding, canvas.height - padding),
                    endPos.x.coerceIn(padding, canvas.width - padding),
                    endPos.y.coerceIn(padding, canvas.height - padding),
                    paint
                )
            }
        }
    }

    private fun isOnScreen(pos: Vector2f, canvas: Canvas): Boolean {
        return pos.x >= 0 && pos.x <= canvas.width &&
                pos.y >= 0 && pos.y <= canvas.height
    }

    fun handleKeyInput(keyCode: Int) {
        if (!isEnabled || detectedBlocks.isEmpty()) return

        when (keyCode) {
            // I key - previous block
            73 -> {
                currentBlockSelection = (currentBlockSelection - 1).coerceAtLeast(-1)
                if (currentBlockSelection == -1) {
                    currentBlockSelection = detectedBlocks.size - 1
                }
                displaySelectionInfo()
            }
            // O key - next block
            79 -> {
                currentBlockSelection = (currentBlockSelection + 1) % detectedBlocks.size
                displaySelectionInfo()
            }
            // P key - open selected block
            80 -> {
                if (currentBlockSelection >= 0 && currentBlockSelection < detectedBlocks.size) {
                    openSelectedBlock()
                }
            }
        }
    }

    private fun displaySelectionInfo() {
        if (currentBlockSelection >= 0 && currentBlockSelection < detectedBlocks.size) {
            val block = detectedBlocks[currentBlockSelection]
            session.displayClientMessage(
                "§6Selected: ${block.blockName} at (${block.x}, ${block.y}, ${block.z})"
            )
        }
    }

    private fun openSelectedBlock() {
        if (currentBlockSelection < 0 || currentBlockSelection >= detectedBlocks.size) return

        val block = detectedBlocks[currentBlockSelection]

        // Send container open packet
        val openPacket = ContainerOpenPacket().apply {
            id = 0
            blockPosition = org.cloudburstmc.math.vector.Vector3i.from(block.x, block.y, block.z)
        }

        session.clientBound(openPacket)
        session.displayClientMessage(
            "§aOpening ${block.blockName}..."
        )
    }
}
