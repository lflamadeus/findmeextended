package com.lai.findmeextended.fabric;

import com.lai.findmeextended.FindMeModClient;
import net.fabricmc.api.ClientModInitializer;

public class FindMeClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new FindMeModClient();
    }
}
