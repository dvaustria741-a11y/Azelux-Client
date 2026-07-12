package com.azeluxclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.Deflater;

/**
 * World Downloader — copies all loaded server chunks into a singleplayer-openable
 * Minecraft world saved under .minecraft/saves/capture_<server>.
 *
 * Usage: call start(mc) from the main thread. Chunk reading happens on the main
 * thread (required by MC), then serialization + disk I/O run on a background thread.
 */
public class WorldDownloader {

    public enum State { IDLE, RUNNING, DONE, ERROR }

    private static volatile State  state    = State.IDLE;
    private static volatile String status   = "Ready";
    private static volatile int    saved    = 0;
    private static volatile int    total    = 0;
    private static volatile String lastPath = "";

    public static State  getState()    { return state;    }
    public static String getStatus()   { return status;   }
    public static int    getSaved()    { return saved;    }
    public static int    getTotal()    { return total;    }
    public static String getLastPath() { return lastPath; }
    public static boolean isRunning()  { return state == State.RUNNING; }

    // ── Public entry point ────────────────────────────────────────────────────

    public static void start(MinecraftClient mc) {
        if (state == State.RUNNING) return;
        if (mc.world == null || mc.player == null) {
            status = "Not in a world"; state = State.ERROR; return;
        }

        state  = State.RUNNING;
        status = "Collecting chunks...";
        saved  = 0;
        total  = 0;

        // All MC world access must happen on the main thread
        int     range    = mc.options.getViewDistance().getValue() + 2;
        int     pcx      = mc.player.getBlockX() >> 4;
        int     pcz      = mc.player.getBlockZ() >> 4;
        int     bottomY  = mc.world.getBottomSectionCoord();
        int     secCount = mc.world.countVerticalSections();
        long    time     = mc.world.getTime();
        long    dayTime  = mc.world.getTimeOfDay();
        BlockPos spawn   = mc.world.getLevelProperties().getSpawnPoint().getPos();
        String  addr     = mc.getCurrentServerEntry() != null
                ? mc.getCurrentServerEntry().address.replace(":", "-").replace("/", "-")
                : "singleplayer";
        Path    base     = mc.runDirectory.toPath().resolve("saves").resolve("capture_" + addr);

        // Snapshot all loaded chunks on main thread
        List<ChunkSnapshot> snapshots = new ArrayList<>();
        for (int x = pcx - range; x <= pcx + range; x++) {
            for (int z = pcz - range; z <= pcz + range; z++) {
                Chunk c = mc.world.getChunk(x, z, ChunkStatus.FULL, false);
                if (c instanceof WorldChunk wc) snapshots.add(snapshot(wc, bottomY, secCount));
            }
        }
        total  = snapshots.size();
        status = "Saving " + total + " chunks...";

        // Write to disk on a background thread
        new Thread(() -> {
            try {
                Path region = base.resolve("region");
                Files.createDirectories(region);
                lastPath = base.toString();

                writeLevelDat(base, addr, time, dayTime, spawn);

                // Group snapshots by region file (each region = 32×32 chunks)
                Map<Long, List<ChunkSnapshot>> byRegion = new LinkedHashMap<>();
                for (ChunkSnapshot s : snapshots) {
                    int rx = s.cx >> 5, rz = s.cz >> 5;
                    byRegion.computeIfAbsent(ChunkPos.toLong(rx, rz),
                            k -> new ArrayList<>()).add(s);
                }
                for (Map.Entry<Long, List<ChunkSnapshot>> e : byRegion.entrySet()) {
                    ChunkPos rp  = new ChunkPos(e.getKey());
                    Path     mca = region.resolve("r." + rp.x + "." + rp.z + ".mca");
                    writeRegion(mca, e.getValue(), bottomY);
                }

                state  = State.DONE;
                status = "Done! " + saved + " chunks saved";
            } catch (Exception ex) {
                state  = State.ERROR;
                status = "Error: " + ex.getMessage();
            }
        }, "WorldDownloader").start();
    }

    public static void reset() {
        if (state != State.RUNNING) { state = State.IDLE; status = "Ready"; }
    }

    // ── Chunk snapshot (immutable copy of block data) ─────────────────────────

    record SectionSnap(byte y, String[] palette, int[] indices) {}
    record ChunkSnapshot(int cx, int cz, List<SectionSnap> sections) {}

    private static ChunkSnapshot snapshot(WorldChunk wc, int bottomY, int secCount) {
        List<SectionSnap> secs = new ArrayList<>();
        ChunkSection[] arr = wc.getSectionArray();
        for (int i = 0; i < arr.length && i < secCount; i++) {
            ChunkSection cs = arr[i];
            if (cs == null || cs.isEmpty()) continue;
            byte secY = (byte)(bottomY + i);

            List<String> palette = new ArrayList<>();
            int[] indices = new int[4096];
            for (int by = 0; by < 16; by++) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int bx = 0; bx < 16; bx++) {
                        var bs   = cs.getBlockState(bx, by, bz);
                        String name = Registries.BLOCK.getId(bs.getBlock()).toString();

                        // Append block state properties (facing, half, etc.)
                        if (!bs.getEntries().isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            bs.getEntries().forEach((prop, val) ->
                                    sb.append(sb.length() == 0 ? "[" : ",")
                                      .append(prop.getName()).append("=").append(val.toString().toLowerCase()));
                            sb.append("]");
                            name += sb.toString();
                        }

                        int idx = palette.indexOf(name);
                        if (idx < 0) { idx = palette.size(); palette.add(name); }
                        indices[bx + bz * 16 + by * 256] = idx;
                    }
                }
            }
            secs.add(new SectionSnap(secY, palette.toArray(new String[0]), indices));
        }
        return new ChunkSnapshot(wc.getPos().x, wc.getPos().z, secs);
    }

    // ── level.dat ─────────────────────────────────────────────────────────────

    private static void writeLevelDat(Path base, String name,
                                       long time, long dayTime, BlockPos spawn) throws IOException {
        NbtCompound data = new NbtCompound();
        data.putInt("DataVersion", 4189);
        data.putString("LevelName", name);
        data.putLong("Time",     time);
        data.putLong("DayTime",  dayTime);
        data.putInt("GameType",  1);
        data.putByte("Difficulty", (byte) 2);
        data.putInt("SpawnX",    spawn.getX());
        data.putInt("SpawnY",    spawn.getY());
        data.putInt("SpawnZ",    spawn.getZ());
        NbtCompound root = new NbtCompound();
        root.put("Data", data);
        try (OutputStream os = Files.newOutputStream(base.resolve("level.dat"))) {
            NbtIo.writeCompressed(root, os);
        }
    }

    // ── Anvil region file (.mca) writer ───────────────────────────────────────

    private static void writeRegion(Path mca, List<ChunkSnapshot> chunks,
                                     int bottomY) throws Exception {
        byte[][] chunkBytes = new byte[1024][];
        for (ChunkSnapshot s : chunks) {
            int lx  = s.cx & 31, lz = s.cz & 31;
            int idx = lx + lz * 32;
            chunkBytes[idx] = zlibCompress(serializeNbt(buildChunkNbt(s, bottomY)));
        }

        try (RandomAccessFile raf = new RandomAccessFile(mca.toFile(), "rw")) {
            raf.write(new byte[8192]); // reserve 2-sector header
            int[] offsets = new int[1024], counts = new int[1024];
            int sector = 2;

            for (int i = 0; i < 1024; i++) {
                if (chunkBytes[i] == null) continue;
                byte[] data    = chunkBytes[i];
                int    payLen  = data.length + 1;       // +1 for compression-type byte
                int    sectors = (payLen + 4 + 4095) / 4096;
                offsets[i] = sector;
                counts[i]  = sectors;
                raf.seek((long) sector * 4096);
                writeInt(raf, payLen);                   // chunk length (includes type byte)
                raf.write(2);                            // compression type: zlib
                raf.write(data);
                int pad = sectors * 4096 - (payLen + 4);
                if (pad > 0) raf.write(new byte[pad]);
                sector += sectors;
                saved++;
            }

            // Write location table back to header
            raf.seek(0);
            for (int i = 0; i < 1024; i++) {
                writeInt(raf, (offsets[i] << 8) | (counts[i] & 0xFF));
            }
            // Timestamp table stays zero (already written as header padding)
        }
    }

    // ── Chunk NBT builder ─────────────────────────────────────────────────────

    private static NbtCompound buildChunkNbt(ChunkSnapshot s, int bottomY) {
        NbtCompound chunk = new NbtCompound();
        chunk.putInt("DataVersion", 4189);
        chunk.putInt("xPos",  s.cx);
        chunk.putInt("yPos",  bottomY);
        chunk.putInt("zPos",  s.cz);
        chunk.putString("Status", "minecraft:full");

        NbtList sections = new NbtList();
        for (SectionSnap sec : s.sections) {
            NbtCompound secNbt = new NbtCompound();
            secNbt.putByte("Y", sec.y);

            NbtList paletteNbt = new NbtList();
            for (String entry : sec.palette) {
                NbtCompound pe = new NbtCompound();
                // Split "minecraft:block[prop=val,...]" into Name + Properties
                int bracket = entry.indexOf('[');
                if (bracket < 0) {
                    pe.putString("Name", entry);
                } else {
                    pe.putString("Name", entry.substring(0, bracket));
                    NbtCompound props = new NbtCompound();
                    String propStr = entry.substring(bracket + 1, entry.length() - 1);
                    for (String kv : propStr.split(",")) {
                        String[] pair = kv.split("=", 2);
                        if (pair.length == 2) props.putString(pair[0], pair[1]);
                    }
                    pe.put("Properties", props);
                }
                paletteNbt.add(pe);
            }

            NbtCompound blockStates = new NbtCompound();
            blockStates.put("palette", paletteNbt);
            if (sec.palette.length > 1) {
                blockStates.putLongArray("data", packIndices(sec.indices, sec.palette.length));
            }
            secNbt.put("block_states", blockStates);
            sections.add(secNbt);
        }
        chunk.put("sections", sections);
        return chunk;
    }

    private static long[] packIndices(int[] indices, int paletteSize) {
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int vpl  = 64 / bits;
        long[] longs = new long[(4096 + vpl - 1) / vpl];
        for (int i = 0; i < 4096; i++) {
            longs[i / vpl] |= ((long) indices[i]) << ((i % vpl) * bits);
        }
        return longs;
    }

    // ── NBT serialization ─────────────────────────────────────────────────────

    private static byte[] serializeNbt(NbtCompound nbt) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(nbt.getType()); // TAG_Compound = 10
        dos.writeShort(0);            // empty root name
        nbt.write(dos);               // compound body + TAG_End
        dos.flush();
        return baos.toByteArray();
    }

    private static byte[] zlibCompress(byte[] input) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 2);
        byte[] buf = new byte[8192];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    private static void writeInt(RandomAccessFile raf, int v) throws IOException {
        raf.write((v >> 24) & 0xFF);
        raf.write((v >> 16) & 0xFF);
        raf.write((v >>  8) & 0xFF);
        raf.write(v         & 0xFF);
    }
}
