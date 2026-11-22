package com.vanishclient;

import com.vanishclient.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class VanishClientAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Vanish Client");

    @Override
    public void onInitialize() {
        Modules.get().add(new HumanAimAssist());
        Modules.get().add(new Trails());
        Modules.get().add(new KillEffect());
        Modules.get().add(new TargetEsp());
        Modules.get().add(new HandShader());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.vanishclient";
    }
}
