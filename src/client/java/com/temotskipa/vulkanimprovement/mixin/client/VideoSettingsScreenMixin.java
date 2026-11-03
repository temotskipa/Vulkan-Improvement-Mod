package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanBackendNotice;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementVideoOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
	@Shadow
	@Final
	private LinearLayout header;
	/**
	 * The line under the title that says the mod is off, beside Minecraft's restart warning.
	 */
	@Unique
	private @Nullable StringWidget vim$backendNotice;

	protected VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	@Inject(method = "addOptions", at = @At("TAIL"))
	private void vim$addRendererOptions(CallbackInfo ci) {
		if (!VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance())) {
			return;
		}
		OptionsList optionsList = this.list;
		if (optionsList == null) {
			return;
		}
		OptionInstance<?>[] options = VulkanImprovementVideoOptions.createOptions();
		if (options.length == 0) {
			return;
		}
		optionsList.addHeader(VulkanImprovementVideoOptions.HEADER);
		optionsList.addSmall(options);
	}

	/**
	 * Adds the line when the screen is built, which Minecraft also does when the window moves to
	 * another monitor.
	 */
	@Inject(method = "addTitle", at = @At("TAIL"))
	private void vim$addBackendNotice(CallbackInfo ci) {
		this.vim$backendNotice = null;
		Component line = VulkanBackendNotice.videoSettingsLine(this.minecraft);
		if (line != null) {
			this.vim$backendNotice = new StringWidget(line, this.font);
			this.header.addChild(this.vim$backendNotice);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void vim$updateRendererOptions(CallbackInfo ci) {
		VulkanImprovementVideoOptions.updateWidgetState(this.list);
		vim$updateBackendNotice();
	}

	/**
	 * Shows, changes or hides the line as the graphics API is changed, as Minecraft does with its
	 * restart warning.
	 */
	@Unique
	private void vim$updateBackendNotice() {
		Component line = VulkanBackendNotice.videoSettingsLine(this.minecraft);
		StringWidget notice = this.vim$backendNotice;
		if (line == null) {
			if (notice != null && notice.isVisible()) {
				notice.setVisible(false);
				this.repositionElements();
			}
			return;
		}
		if (notice == null) {
			notice = new StringWidget(line, this.font);
			this.vim$backendNotice = notice;
			this.header.addChild(notice);
			this.addRenderableWidget(notice);
			this.repositionElements();
		}
		else if (!notice.isVisible() || !notice.getMessage().equals(line)) {
			notice.setMessage(line);
			notice.setVisible(true);
			this.repositionElements();
		}
	}
}
