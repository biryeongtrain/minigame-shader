package kim.biryeong.minigamelib.shaderfx.api;

import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public class ShaderEffect {
    final Identifier location;
    final int id;
    final String snippet;

    public ShaderEffect(Identifier location, int id, String snippet) {
        this.location = location;
        this.id = id;
        this.snippet = snippet;
    }

    public Identifier location() {
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
