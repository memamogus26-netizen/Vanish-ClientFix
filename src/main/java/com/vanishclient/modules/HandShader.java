package com.vanishclient.modules;

import com.vanishclient.VanishClientAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * Hands Shader: settings, mode list and colour logic.
 *
 * NOTE: the part that actually draws the shader on the first-person hand needs a render hook
 * (mixin or shader pipeline) written against the exact Minecraft 1.21.11 mappings. That hook is
 * NOT included yet, so this module currently only stores the settings. See README.
 */
public class HandShader extends Module {
    public enum Shader { None, Solid, Chroma, Balatro, Smoke, Stripes, Glow, Glass, Invert, Snow, Fire }

    private final SettingGroup sgMode = settings.createGroup("Mode");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Shader> mainShader = sgMode.add(new EnumSetting.Builder<Shader>()
        .name("main-shader").description("Primary hand shader effect.")
        .defaultValue(Shader.Solid).build());

    private final Setting<Boolean> dual = sgMode.add(new BoolSetting.Builder()
        .name("dual-shader-mode").description("Combine two effects.")
        .defaultValue(false).build());

    private final Setting<Shader> secondShader = sgMode.add(new EnumSetting.Builder<Shader>()
        .name("second-shader").description("Second effect used in dual mode.")
        .defaultValue(Shader.Glow).visible(dual::get).build());

    private final Setting<Double> speed = sgMode.add(new DoubleSetting.Builder()
        .name("speed").description("Animation speed.")
        .defaultValue(1).min(0.1).sliderMax(5).build());

    private final Setting<SettingColor> solidColor = sgColors.add(new ColorSetting.Builder()
        .name("solid-color").description("Colour for Solid, Glow and Glass.")
        .defaultValue(new SettingColor(220, 20, 120, 255)).build());

    private final Setting<SettingColor> fireInner = sgColors.add(new ColorSetting.Builder()
        .name("fire-inner").description("Hot core colour of the Fire shader.")
        .defaultValue(new SettingColor(255, 220, 80, 255)).build());

    private final Setting<SettingColor> fireOuter = sgColors.add(new ColorSetting.Builder()
        .name("fire-outer").description("Outer flame colour of the Fire shader.")
        .defaultValue(new SettingColor(255, 70, 10, 255)).build());

    public HandShader() {
        super(VanishClientAddon.CATEGORY, "hands-shader", "Shader effects for your hand and held item.");
    }

    @Override
    public void onActivate() {
        info("Hands Shader: settings only for now, the hand render hook is not installed yet.");
    }

    public Shader main() { return mainShader.get(); }
    public Shader second() { return dual.get() ? secondShader.get() : Shader.None; }

    /** Colour a shader would put on the hand at a point in its animation. Used by the render hook. */
    public Color tint(Shader s, double seconds, double phase) {
        double t = seconds * speed.get() + phase;
        switch (s) {
            case Fire -> {
                // flicker between the outer and inner flame colour
                double f = 0.5 + 0.5 * Math.sin(t * 9) * Math.sin(t * 5.3 + phase * 3);
                Color a = fireOuter.get(), b = fireInner.get();
                return new Color(
                    (int) (a.r + (b.r - a.r) * f), (int) (a.g + (b.g - a.g) * f), (int) (a.b + (b.b - a.b) * f), 255);
            }
            case Chroma -> {
                double h = (t * 0.25) % 1.0;
                return new Color(
                    (int) (127.5 + 127.5 * Math.sin(h * Math.PI * 2)),
                    (int) (127.5 + 127.5 * Math.sin(h * Math.PI * 2 + 2.094)),
                    (int) (127.5 + 127.5 * Math.sin(h * Math.PI * 2 + 4.188)), 255);
            }
            case None -> { return new Color(255, 255, 255, 0); }
            default -> { return new Color(solidColor.get()); }
        }
    }
}
