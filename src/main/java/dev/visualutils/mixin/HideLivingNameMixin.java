package dev.visualutils.mixin;

import dev.visualutils.NameHider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ukrywa zwykly nametag zywych istot (gracze, moby), gdy wlaczone sa Nametagi+. */
@Mixin(LivingEntityRenderer.class)
public abstract class HideLivingNameMixin {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true, require = 0)
    private void visualutils$hideName(LivingEntity entity, double distanceSq,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (NameHider.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }
}
