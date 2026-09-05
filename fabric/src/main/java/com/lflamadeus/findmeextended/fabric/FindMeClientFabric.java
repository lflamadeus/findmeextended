package com.lflamadeus.findmeextended.fabric;

import com.lflamadeus.findmeextended.FindMeModClient;
import net.fabricmc.api.ClientModInitializer;

public class FindMeClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new FindMeModClient();
    }
}
