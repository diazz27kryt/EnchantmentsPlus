package ru.diazz27kryt.enchantmentsplus;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnchantmentsPlus implements ModInitializer {
    public static final String MOD_ID = "enchantmentsplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("EnchantmentsPlus for Minecraft 1.21.11 loaded.");
    }
}
