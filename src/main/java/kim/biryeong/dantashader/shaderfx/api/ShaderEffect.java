package kim.biryeong.dantashader.shaderfx.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;

public class ShaderEffect {
    final ResourceLocation location;
    final int id;
    final String snippet;

    public ShaderEffect(ResourceLocation location, int id, String snippet) {
        this.location = location;
        this.id = id;
        this.snippet = snippet;
    }

    public ResourceLocation location() {
        return location;
    }

    public int id() {
        return this.id;
    }

    public String asChar() {
        return String.valueOf((char) (0xE100 + id()));
    }

    public int asFullscreenColor() {
        return ARGB.color(251, 0, 0, id());
    }

    public int asLocalEffectColor() {
        return ARGB.color(253, 0, 0, id());
    }

    public String snippet() {
        return this.snippet;
    }
}
