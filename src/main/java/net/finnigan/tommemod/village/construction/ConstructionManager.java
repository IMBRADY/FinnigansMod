package net.finnigan.tommemod.village.construction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every construction site in one dimension, saved with the world. Sites are kept in the order they
 * were started, which is also the order builders prioritise them in. */
public class ConstructionManager extends SavedData {

    private static final String DATA_NAME = "tommemod_construction";

    private final Map<UUID, ConstructionSite> sites = new LinkedHashMap<>();

    public static ConstructionManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag), ConstructionManager::new, DATA_NAME);
    }

    public boolean isEmpty() {
        return sites.isEmpty();
    }

    public Collection<ConstructionSite> all() {
        return Collections.unmodifiableCollection(sites.values());
    }

    @Nullable
    public ConstructionSite get(UUID id) {
        return sites.get(id);
    }

    public List<ConstructionSite> inVillage(UUID villageId) {
        List<ConstructionSite> list = new ArrayList<>();
        for (ConstructionSite s : sites.values()) {
            if (s.villageId().equals(villageId)) list.add(s);
        }
        return list;
    }

    public boolean overlaps(BoundingBox box) {
        for (ConstructionSite s : sites.values()) {
            if (s.bounds().intersects(box)) return true;
        }
        return false;
    }

    public void add(ConstructionSite site) {
        sites.put(site.id(), site);
        setDirty();
    }

    @Nullable
    public ConstructionSite remove(UUID id) {
        ConstructionSite removed = sites.remove(id);
        if (removed != null) setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ConstructionSite s : sites.values()) list.add(s.save());
        tag.put("Sites", list);
        return tag;
    }

    private static ConstructionManager load(ServerLevel level, CompoundTag tag) {
        ConstructionManager m = new ConstructionManager();
        ListTag list = tag.getList("Sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ConstructionSite s = ConstructionSite.load(level, list.getCompound(i));
            m.sites.put(s.id(), s);
        }
        return m;
    }
}
