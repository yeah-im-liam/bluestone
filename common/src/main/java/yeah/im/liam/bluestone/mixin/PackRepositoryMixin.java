package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.client.ProgrammerArtExtras;

/** NeoForge only (bluestone-neoforge.mixins.json): see ProgrammerArtExtras. */
@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {
    @ModifyReturnValue(method = "openAllSelected", at = @At("RETURN"))
    private List<PackResources> bluestone$programmerArt(List<PackResources> packs) {
        return ProgrammerArtExtras.addTo(packs);
    }
}
