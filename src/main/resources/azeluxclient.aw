classTweaker v1 named
# PlayerInventory fields (existing)
accessible field net/minecraft/entity/player/PlayerInventory selectedSlot I
accessible field net/minecraft/entity/player/PlayerInventory main Lnet/minecraft/util/collection/DefaultedList;

# RenderPhase protected fields needed by StorageESP's custom THROUGH_WALL_LINES layer
accessible field net/minecraft/client/render/RenderPhase LINES_PROGRAM Lnet/minecraft/client/render/RenderPhase$ShaderProgram;
accessible field net/minecraft/client/render/RenderPhase NO_LAYERING Lnet/minecraft/client/render/RenderPhase$Layering;
accessible field net/minecraft/client/render/RenderPhase TRANSLUCENT_TRANSPARENCY Lnet/minecraft/client/render/RenderPhase$Transparency;
accessible field net/minecraft/client/render/RenderPhase COLOR_MASK Lnet/minecraft/client/render/RenderPhase$WriteMaskState;
accessible field net/minecraft/client/render/RenderPhase ALWAYS_DEPTH_TEST Lnet/minecraft/client/render/RenderPhase$DepthTest;
