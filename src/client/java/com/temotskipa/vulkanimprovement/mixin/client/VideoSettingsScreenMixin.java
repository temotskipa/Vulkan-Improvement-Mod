package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.VulkanImprovementVideoOptions;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    protected VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }
    
    @Inject(method = "addOptions", at = @At("TAIL"))
    private void vim$addRendererOptions(CallbackInfo ci) {
        OptionsList optionsList = this.list;
        if (optionsList != null) {
            optionsList.addHeader(VulkanImprovementVideoOptions.HEADER);
            optionsList.addSmall(VulkanImprovementVideoOptions.createOptions());
        }
    }
}
