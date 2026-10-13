package yeah.im.liam.bluestone.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.client.TrimItemModels;

/**
 * Hands each item file to {@link TrimItemModels} between reading and decoding it. The lambda is the same in 26.1 to
 * 26.3 and in NeoForge's copy.
 */
@Mixin(ClientItemInfoLoader.class)
abstract class ClientItemInfoLoaderMixin {
    @Shadow
    @Final
    private static FileToIdConverter LISTER;

    @ModifyExpressionValue(method = "lambda$scheduleLoad$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"))
    private static JsonElement bluestone$addTrims(JsonElement definition, @Local(argsOnly = true) Identifier file) {
        return TrimItemModels.addCases(LISTER.fileToId(file), definition);
    }
}
