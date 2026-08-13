package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.skill.SkillTreeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Server -> client: the skill trees themselves, verbatim.
 *
 * Sent on login and again after any datapack reload, because the client cannot draw a tree - let
 * alone tell the player whether they qualify for a node - without the same definitions the server is
 * checking against. Shipping the JSON rather than a parsed form means both sides run the identical
 * parser, so a tree that loads on one loads on the other or fails loudly on both.
 *
 * Deflated on the way out. Fourteen full trees are a few hundred kilobytes of fairly repetitive JSON,
 * which compresses to a small fraction of that - comfortably inside a single packet, which keeps the
 * client from ever having to cope with a half-arrived set of definitions.
 */
public class SyncSkillDefinitionsPacket {

    private final Map<ResourceLocation, String> categories;
    private final Map<ResourceLocation, String> skills;

    public SyncSkillDefinitionsPacket(Map<ResourceLocation, String> categories, Map<ResourceLocation, String> skills) {
        this.categories = categories;
        this.skills = skills;
    }

    public SyncSkillDefinitionsPacket(FriendlyByteBuf buf) {
        byte[] compressed = buf.readByteArray();
        try (DataInputStream in = new DataInputStream(
                new InflaterInputStream(new ByteArrayInputStream(compressed)))) {
            this.categories = readMap(in);
            this.skills = readMap(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Malformed skill definitions payload", e);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            writeMap(out, categories);
            writeMap(out, skills);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress skill definitions", e);
        }
        buf.writeByteArray(bytes.toByteArray());
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> SkillTreeManager.install(categories, skills));
        ctx.setPacketHandled(true);
    }

    // Length-prefixed raw bytes rather than writeUTF: a single tree file runs well past the 64KB
    // ceiling DataOutputStream's modified-UTF8 format imposes.
    private static void writeMap(DataOutputStream out, Map<ResourceLocation, String> map) throws IOException {
        out.writeInt(map.size());
        for (Map.Entry<ResourceLocation, String> entry : map.entrySet()) {
            writeString(out, entry.getKey().toString());
            writeString(out, entry.getValue());
        }
    }

    private static Map<ResourceLocation, String> readMap(DataInputStream in) throws IOException {
        int size = in.readInt();
        Map<ResourceLocation, String> map = new LinkedHashMap<>(Math.max(16, size));
        for (int i = 0; i < size; i++) {
            String id = readString(in);
            String json = readString(in);
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null) map.put(key, json);
        }
        return map;
    }

    private static void writeString(DataOutputStream out, String text) throws IOException {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(raw.length);
        out.write(raw);
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] raw = new byte[in.readInt()];
        in.readFully(raw);
        return new String(raw, StandardCharsets.UTF_8);
    }
}
