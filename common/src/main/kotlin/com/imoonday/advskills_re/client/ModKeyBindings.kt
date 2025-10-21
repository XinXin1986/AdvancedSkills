package com.imoonday.advskills_re.client

import com.imoonday.advskills_re.client.screen.*
import com.imoonday.advskills_re.client.screen.SkillWheelScreen.Companion.quickCastSlot
import com.imoonday.advskills_re.component.SkillContainer
import com.imoonday.advskills_re.init.Skills
import com.imoonday.advskills_re.network.c2s.UseSkillC2SRequest
import com.imoonday.advskills_re.util.choiceData
import com.imoonday.advskills_re.util.skillContainer
import dev.architectury.event.EventResult
import dev.architectury.event.events.client.ClientRawInputEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.option.KeyBinding
import org.lwjgl.glfw.GLFW

@Environment(EnvType.CLIENT)
object ModKeyBindings {

    val skillKeys = mutableListOf<KeyBinding>()
    private var isUsingQuickCast = false

    @JvmField
    val OPEN_LIST_SCREEN = register("openListScreen", GLFW.GLFW_KEY_K, false) { client, _ ->
        val player = client.player!!
        client.setScreen(
            if (!player.choiceData.isEmpty() && SkillChoiceScreen.new) {
                SkillChoiceScreen(player) { SkillListScreen(player) }
            } else {
                SkillListScreen(player)
            }
        )
    }

    @JvmField
    val OPEN_GALLERY_SCREEN = register("openGalleryScreen", GLFW.GLFW_KEY_G, false) { client, _ ->
        client.setScreen(SkillGalleryScreen(Skills.FIREBALL))
    }

    @JvmField
    val OPEN_SLOT_SCREEN = register("openSlotScreen", GLFW.GLFW_KEY_N, false) { client, _ ->
        client.setScreen(SkillSlotScreen())
    }

//    @JvmField
//    val SWITCH_PREVIOUS_SKILL = register("switchPreviousSkill", GLFW.GLFW_KEY_UNKNOWN, false) { client, _ ->
//        val player = client.player ?: return@register
//        quickCastSlot = quickCastSlot?.minus(1) ?: 1
//        if (quickCastSlot!! < 1) {
//            quickCastSlot = player.skillContainer.slotSize
//        }
//    }
//
//    @JvmField
//    val SWITCH_NEXT_SKILL = register("switchNextSkill", GLFW.GLFW_KEY_UNKNOWN, false) { client, _ ->
//        val player = client.player ?: return@register
//        quickCastSlot = quickCastSlot?.plus(1) ?: 1
//        if (quickCastSlot!! > player.skillContainer.slotSize) {
//            quickCastSlot = 1
//        }
//    }

    @JvmField
    val OPEN_SKILL_WHEEL = register("openSkillWheel", GLFW.GLFW_KEY_N, false) { client, _ ->
        client.setScreen(SkillWheelScreen())
    }

    @JvmField
    val USE_SELECTED_SKILL = registerUseSkillKey("useSelectedSkill", GLFW.GLFW_KEY_N) { client, keyState ->
        client.player?.run {
            if (quickCastSlot != null) {
                if (!isSpectator) requestUse(quickCastSlot!!, keyState)
            }
        }
    }

    @JvmField
    val SWITCH_SKILL_HELP_KEY = registerKeyBinding("switchSkillHelpKey", GLFW.GLFW_KEY_RIGHT_ALT)

//    @JvmField
//    val QUICK_CAST = registerWithDoubleTrigger(
//        "quickCast",
//        GLFW.GLFW_KEY_R,
//        { ClientConfig.get().quickCastWheelHoldTime },
//        firstTriggerCallback = { client, _ ->
//            val slot = quickCastSlot
//            slot != null && client.player?.getSkill(slot)?.isEmpty != true
//        },
//        secondTriggerCallback = { client, _ ->
//            if (!isUsingQuickCast && client.currentScreen == null) {
//                isUsingQuickCast = true
//                client.setScreen(SkillWheelScreen())
//            }
//        },
//        releaseCallback = { client, _, pressTime ->
//            isUsingQuickCast = false
//            if (pressTime <= ClientConfig.get().quickCastWheelHoldTime) {
//                client.player?.run {
//                    val slot = quickCastSlot ?: return@run
//                    if (!isSpectator) {
//                        requestUse(
//                            slot,
//                            if (isCharging(getSkill(slot))) UseSkillC2SRequest.KeyState.RELEASE
//                            else UseSkillC2SRequest.KeyState.PRESS
//                        )
//                    }
//                }
//            }
//        }
//    )

    fun init() {
        for (index in 1..SkillContainer.MAX_SLOT_SIZE) {
            registerUseSkillKey(
                index,
                if (index <= 6) (GLFW.GLFW_KEY_KP_0 + index)
                else GLFW.GLFW_KEY_UNKNOWN
            ) { client, keyState ->
                client.player?.run {
                    if (!isSpectator) requestUse(index, keyState)
                }
            }
        }
        registerMouseScrollEvent()
    }

    private fun register(
        name: String,
        code: Int,
        longPressCheck: Boolean,
        releaseCallback: (MinecraftClient, KeyBinding) -> Unit = { _, _ -> },
        callback: (MinecraftClient, KeyBinding) -> Unit,
    ): KeyBinding {
        val key = registerKeyBinding("advskills_re.key.$name", code);
        KeyMappingRegistry.register(key)
        // 按键状态变量
        var isPressed = false

        ClientTickEvent.CLIENT_POST.register { client ->
            if (longPressCheck) {
                if (key.isPressed) {
                    if (!isPressed) {
                        callback(client, key) // 按下触发
                        isPressed = true
                    }
                } else {
                    if (isPressed) {
                        releaseCallback(client, key) // 松开触发
                        isPressed = false
                    }
                }
            } else {
                if (key.wasPressed()) {
                    callback(client, key) // 按下瞬间触发
                } else if (!key.isPressed) {
                    releaseCallback(client, key) // 松开触发
                }
            }
        }

        return key
    }

    private fun registerWithDoubleTrigger(
        name: String,
        code: Int,
        interval: () -> Int, // 二次触发的间隔时间（毫秒）
        firstTriggerCallback: (MinecraftClient, KeyBinding) -> Boolean,
        secondTriggerCallback: (MinecraftClient, KeyBinding) -> Unit,
        releaseCallback: (MinecraftClient, KeyBinding, pressTime: Long) -> Unit,
    ): KeyBinding {
        val key = registerKeyBinding("advskills_re.key.$name", code);
        // 用于记录按键状态和计时
        var isPressed = false
        var secondTriggered = false
        var pressStartTime: Long = 0

        ClientTickEvent.CLIENT_POST.register { client ->
            if (key.isPressed || isPressed && key.isPressedInScreen) {
                if (!isPressed) {
                    // 第一次触发
                    val firstTriggerResult = firstTriggerCallback(client, key)
                    isPressed = true

                    pressStartTime = if (!firstTriggerResult) {
                        // 如果第一次触发返回 false，则立即执行二次触发逻辑
                        secondTriggerCallback(client, key)
                        secondTriggered = true
                        System.currentTimeMillis() - interval()
                    } else {
                        secondTriggered = false
                        System.currentTimeMillis()
                    }
                } else if (!secondTriggered && System.currentTimeMillis() - pressStartTime >= interval()) {
                    // 二次触发
                    secondTriggerCallback(client, key)
                    secondTriggered = true
                }
            } else if (isPressed) {
                // 松开时触发
                releaseCallback(client, key, System.currentTimeMillis() - pressStartTime)
                isPressed = false
            }
        }

        return key
    }

    private fun registerUseSkillKey(
        index: Int,
        code: Int,
        callbacks: (MinecraftClient, UseSkillC2SRequest.KeyState) -> Unit,
    ): KeyBinding {
        val name = "advskills_re.key.useSkill$index"
        val key = registerUseSkillKey(name, code, callbacks);
        KeyMappingRegistry.register(key)
        skillKeys.add(key)
        return key
    }


    private fun registerUseSkillKey(
        name: String,
        code: Int,
        callbacks: (MinecraftClient, UseSkillC2SRequest.KeyState) -> Unit,
    ): KeyBinding {
        val key = registerKeyBinding(name, code);
        var isPressed = false
        ClientTickEvent.CLIENT_POST.register { client ->
            if (key.isPressed) {
                if (!isPressed) {
                    // 按下触发
                    callbacks(client, UseSkillC2SRequest.KeyState.PRESS)
                    isPressed = true
                }
            } else {
                if (isPressed) {
                    // 松开触发
                    callbacks(client, UseSkillC2SRequest.KeyState.RELEASE)
                    isPressed = false
                }
            }
        }
        return key
    }


    private fun registerKeyBinding(
        name: String,
        code: Int
    ): KeyBinding {
        val key = KeyBinding(
            name,
            code,
            "advskills_re.key.category"
        )
        KeyMappingRegistry.register(key);
        return key
    }

    private fun registerMouseScrollEvent() {
        ClientRawInputEvent.MOUSE_SCROLLED.register { client, dWheel ->
            if (SWITCH_SKILL_HELP_KEY.isPressed && client.player != null) {
                val player = client.player!!
                if (dWheel > 0) {
                    quickCastSlot = findPrevSkillSlot(player, quickCastSlot)

                } else if (dWheel < 0) {
                    quickCastSlot = findNextSkillSlot(player, quickCastSlot)
                }
                //中断事件，防止游戏内物品栏也跟着滚动
                return@register EventResult.interruptFalse()
            }
            //如果辅助键没有按下，则正常传递事件
            EventResult.pass()
        }
    }

    private fun findPrevSkillSlot(player: ClientPlayerEntity, curSlot: Int?): Int {
        val slotSize = player.skillContainer.slotSize
        var slot = curSlot?.minus(1) ?: slotSize
        if (slot < 1) {
            slot = slotSize
        }
        return slot
//        do {
//            var nextSlot = curSlot.minus(1)
//            if (nextSlot < 1) {
//                nextSlot = slotSize
//            }
//            if (SkillSlot.isValidIndex(player, nextSlot)) {
//                val skill = player.getSkill(nextSlot)
//                if (skill !is PassiveSkill) {
//                    return nextSlot
//                }
//            }
//        } while (nextSlot != curSlot)
//        return curSlot
    }

    private fun findNextSkillSlot(player: ClientPlayerEntity, curSlot: Int?): Int {
        val slotSize = player.skillContainer.slotSize
        var slot = curSlot?.plus(1) ?: 1
        if (slot > slotSize) {
            slot = 1
        }
        return slot
//        do {
//            var nextSlot = curSlot.plus(1)
//            if (nextSlot > slotSize) {
//                nextSlot = 1
//            }
//            if (SkillSlot.isValidIndex(player, nextSlot)) {
//                val skill = player.getSkill(nextSlot)
//                if (skill !is PassiveSkill) {
//                    return nextSlot
//                }
//            }
//        } while (nextSlot != curSlot)
//        return curSlot
    }
}