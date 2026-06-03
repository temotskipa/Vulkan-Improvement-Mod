package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.VulkanGraphicsApiPreference;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsMixin implements OptionsAccessor {
    @Inject(
            method = "load",
            at = @At(
                    value = "FIELD",
                    opcode = org.objectweb.asm.Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/client/Options;preferredGraphicsBackendFromStartup:Lnet/minecraft/client/PreferredGraphicsApi;",
                    shift = At.Shift.BEFORE))
    private void vim$preferVulkanBeforeStartupSnapshot(CallbackInfo ci) {
        VulkanGraphicsApiPreference.applyModDefaultUpgrade((Options) (Object) this, this.vulkanimprovement$getOptionsFile());
    }
}
