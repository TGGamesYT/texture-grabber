package me.tg.texture_grabber.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Texture_grabberClient implements ClientModInitializer {
    private static final Path OUTPUT_DIR = Path.of("config", "textures");

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            dumpFromResourceManager(client.getResourceManager());
            dumpFromAllPacks(client);

            ResourcePack vanillaPack = createVanillaPack(client);
            if (vanillaPack != null) {
                dumpVanillaPack(vanillaPack);
            } else {
                System.err.println("Vanilla resource pack not found!");
            }
        });
    }

    private void dumpFromResourceManager(ResourceManager manager) {
        try {
            var resources = manager.findResources("", path -> true);
            for (var entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (id.getNamespace().startsWith("fabric")) continue;
                System.out.println("[dumpFromResourceManager] Found resource: " + id);
                try (InputStream in = entry.getValue().getInputStream()) {
                    copyStream(in, id);
                    System.out.println("[dumpFromResourceManager] Saved resource: " + id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void dumpFromAllPacks(MinecraftClient client) {
        for (ResourcePackProfile profile : client.getResourcePackManager().getProfiles()) {
            try (ResourcePack pack = profile.createResourcePack()) {
                System.out.println("Dumping from pack: " + profile.getDisplayName());
                for (String namespace : pack.getNamespaces(ResourceType.CLIENT_RESOURCES)) {
                    if (namespace.startsWith("fabric")) {
                        System.out.println("Skipping fabric namespace: " + namespace);
                        continue;
                    }
                    System.out.println("Namespace: " + namespace);
                    pack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "", new ResourcePack.ResultConsumer() {
                        @Override
                        public void accept(Identifier id, InputSupplier<InputStream> inputSupplier) {
                            try (InputStream in = inputSupplier.get()) {
                                System.out.println("Copying resource: " + id);
                                copyStream(in, id);
                            } catch (IOException e) {
                                System.err.println("Failed to copy from pack: " + id + " (" + profile.getDisplayName() + ")");
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        }
    }

    private ResourcePack createVanillaPack(MinecraftClient client) {
        for (ResourcePackProfile profile : client.getResourcePackManager().getProfiles()) {
            String packName = profile.getId(); // or getId() in some versions
            System.out.println("Profile displayName: " + profile.getDisplayName().getString() + ", name: " + packName + ", source: " + profile.getSource());
            if (profile.getSource() == ResourcePackSource.BUILTIN) {
                // Accept only the default "minecraft" pack
                if ("minecraft".equals(packName) || "vanilla".equals(packName)) {
                    return profile.createResourcePack();
                }
            }
        }
        return null;
    }

    private static final String[] VANILLA_SUBFOLDERS = {
            "textures",
            "models",
            "lang",
            "sounds",
            "font",
            "shaders",
            "texts",
            "particles",
            "advancements",
            "loot_tables",
            "recipes",
            "structures",
            "tags",
            "blockstates",
            "sounds.json"
    };

    private void dumpVanillaPack(ResourcePack vanillaPack) {
        try (vanillaPack) {
            for (String namespace : vanillaPack.getNamespaces(ResourceType.CLIENT_RESOURCES)) {
                System.out.println("[dumpVanillaPack] Namespace: " + namespace);
                for (String folder : VANILLA_SUBFOLDERS) {
                    try {
                        vanillaPack.findResources(ResourceType.CLIENT_RESOURCES, namespace, folder, new ResourcePack.ResultConsumer() {
                            @Override
                            public void accept(Identifier id, InputSupplier<InputStream> inputSupplier) {
                                try (InputStream in = inputSupplier.get()) {
                                    System.out.println("[dumpVanillaPack] Found resource: " + id);
                                    copyStream(in, id);
                                } catch (IOException e) {
                                    System.err.println("Failed to copy from vanilla pack: " + id);
                                }
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("Failed to find resources in folder: " + folder);
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    private void copyStream(InputStream in, Identifier id) throws IOException {
        Path outPath = OUTPUT_DIR.resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
        Files.createDirectories(outPath.getParent());
        Files.copy(in, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}