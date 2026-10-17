package yeah.im.liam.bluestone.mixin;

// Item definitions, and the loader for them, are new in 1.21.4.
//#if MC >= 1.21.4
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
 * Hands each item file to {@link TrimItemModels} between reading and decoding it.
 */
@Mixin(ClientItemInfoLoader.class)
abstract class ClientItemInfoLoaderMixin {
    @Shadow
    @Final
    private static FileToIdConverter LISTER;

    // The lambda that reads one item file: Fabric knows it by its intermediary name before 26.1, NeoForge by javac's.
    //#if MC >= 26.1
    @ModifyExpressionValue(method = "lambda$scheduleLoad$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"))
    //#elseif MC >= 1.21.5
    //$$ // 1.21.6 reads the file with its own parser, 1.21.5 with Gson's (bluestone$addTrimsGson); each is optional.
    //$$ @ModifyExpressionValue(method = {"lambda$scheduleLoad$3", "method_65926"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"), require = 0)
    //#else
    //$$ @ModifyExpressionValue(method = {"lambda$scheduleLoad$2", "method_65926"}, at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonParser;parseReader(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"))
    //#endif
    private static JsonElement bluestone$addTrims(JsonElement definition, @Local(argsOnly = true) Identifier file) {
        return TrimItemModels.addCases(LISTER.fileToId(file), definition);
    }
    //#if MC >= 1.21.5 && MC < 1.21.9
    //$$
    //$$ @ModifyExpressionValue(method = {"lambda$scheduleLoad$3", "method_65926"}, at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonParser;parseReader(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"), require = 0)
    //$$ private static JsonElement bluestone$addTrimsGson(JsonElement definition, @Local(argsOnly = true) Identifier file) {
    //$$     return TrimItemModels.addCases(LISTER.fileToId(file), definition);
    //$$ }
    //#endif
}
//#endif
