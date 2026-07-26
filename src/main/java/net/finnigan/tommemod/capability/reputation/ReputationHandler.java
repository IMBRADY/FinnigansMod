package net.finnigan.tommemod.capability.reputation;

import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReputationHandler implements INBTSerializable<CompoundTag> {

    private final Map<UUID, Integer> reputation = new HashMap<>();
    private Runnable changeListener;

    public int getReputation(UUID villageId) {
        return reputation.getOrDefault(villageId, 0);
    }

    public ReputationTier getTier(UUID villageId) {
        return ReputationTier.fromScore(getReputation(villageId));
    }

    public record ReputationChange(int oldScore, int newScore, ReputationTier oldTier, ReputationTier newTier) {
        public boolean tierChanged() {
            return oldTier != newTier;
        }
    }

    public ReputationChange addReputation(UUID villageId, int delta) {
        int floor = ModConfig.REPUTATION_FLOOR.get();
        int oldScore = getReputation(villageId);
        int newScore = Math.max(floor, oldScore + delta);
        reputation.put(villageId, newScore);
        if (changeListener != null) changeListener.run();
        return new ReputationChange(oldScore, newScore, ReputationTier.fromScore(oldScore), ReputationTier.fromScore(newScore));
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        reputation.forEach((id, score) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putInt("Score", score);
            list.add(e);
        });
        tag.put("Reputation", list);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        reputation.clear();
        ListTag list = tag.getList("Reputation", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            reputation.put(e.getUUID("Id"), e.getInt("Score"));
        }
    }
}
