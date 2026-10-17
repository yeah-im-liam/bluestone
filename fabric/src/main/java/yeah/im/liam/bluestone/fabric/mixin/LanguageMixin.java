package yeah.im.liam.bluestone.fabric.mixin;

// The game reads its English file by its path on the class path, where assets/minecraft/lang/en_us.json from this mod
// (the vanilla rails' new names) can come first and leave a server without the game's own text. Fabric API reads the
// game's file itself in its later builds, which never came out for some of these versions.
//#if MC < 1.21.5
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import java.io.IOException;
//$$ import java.io.InputStream;
//$$ import java.nio.file.Files;
//$$ import java.nio.file.Path;
//$$ import java.util.Optional;
//$$ import net.fabricmc.loader.api.FabricLoader;
//$$ import net.minecraft.locale.Language;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ @Mixin(Language.class)
//$$ abstract class LanguageMixin {
//$$     @WrapOperation(method = "parseTranslations", at = @At(value = "INVOKE", target = "Ljava/lang/Class;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
//$$     private static InputStream bluestone$gameFile(Class<?> owner, String path, Operation<InputStream> original) throws IOException {
//$$         Optional<Path> file = FabricLoader.getInstance().getModContainer("minecraft").flatMap(game -> game.findPath(path));
//$$         return file.isPresent() ? Files.newInputStream(file.get()) : original.call(owner, path);
//$$     }
//$$ }
//#endif
