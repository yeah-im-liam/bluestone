package yeah.im.liam.bluestone.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.Bluestone;

/**
 * Gives trimmed armor its bluestone, purplestone and greenstone icons.
 */
public final class TrimItemModels {
    private static final List<String> MATERIALS = List.of("bluestone", "purplestone", "greenstone");
    private static final Identifier SELECT = Bluestone.vanilla("select");
    private static final Identifier MODEL = Bluestone.vanilla("model");
    private static final Identifier TRIM_MATERIAL = Bluestone.vanilla("trim_material");
    private static final Identifier REDSTONE = Bluestone.vanilla("redstone");

    private TrimItemModels() {
    }

    public static JsonElement addCases(Identifier item, JsonElement definition) {
        if (item.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            visit(item.getPath(), definition);
        }
        return definition;
    }

    private static void visit(String item, JsonElement element) {
        if (element instanceof JsonArray array) {
            array.forEach(child -> visit(item, child));
        } else if (element instanceof JsonObject object) {
            if (is(object.get("type"), SELECT) && is(object.get("property"), TRIM_MATERIAL) && object.get("cases") instanceof JsonArray cases) {
                addCases(item, cases);
            }
            object.entrySet().forEach(entry -> visit(item, entry.getValue()));
        }
    }

    private static void addCases(String item, JsonArray cases) {
        JsonObject redstone = null;
        Set<Identifier> taken = new HashSet<>();
        for (JsonElement element : cases) {
            if (element instanceof JsonObject trimCase) {
                for (JsonElement when : values(trimCase.get("when"))) {
                    Identifier material = when instanceof JsonPrimitive value && value.isString() ? Identifier.tryParse(value.getAsString()) : null;
                    taken.add(material);
                    if (REDSTONE.equals(material) && redstone == null) {
                        redstone = trimCase;
                    }
                }
            }
        }
        if (redstone == null) {
            return;
        }
        List<JsonElement> list = cases.asList();
        int position = list.indexOf(redstone) + 1;
        for (String material : MATERIALS) {
            Identifier id = Bluestone.id(material);
            if (taken.contains(id)) {
                continue;
            }
            JsonObject model = new JsonObject();
            model.addProperty("type", MODEL.toString());
            model.addProperty("model", Bluestone.id("item/" + item + "_" + material + "_trim").toString());
            if (redstone.get("model") instanceof JsonObject redstoneModel && redstoneModel.get("tints") instanceof JsonArray tints) {
                model.add("tints", tints.deepCopy());
            }
            JsonObject trimCase = new JsonObject();
            trimCase.add("model", model);
            trimCase.addProperty("when", id.toString());
            list.add(position++, trimCase);
        }
    }

    private static List<JsonElement> values(@Nullable JsonElement when) {
        return when instanceof JsonArray array ? array.asList() : when == null ? List.of() : List.of(when);
    }

    private static boolean is(@Nullable JsonElement element, Identifier id) {
        return element instanceof JsonPrimitive value && value.isString() && id.equals(Identifier.tryParse(value.getAsString()));
    }
}
