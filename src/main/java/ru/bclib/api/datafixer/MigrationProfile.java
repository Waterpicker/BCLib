package ru.bclib.api.datafixer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import ru.bclib.BCLib;
import ru.bclib.api.WorldDataAPI;
import ru.bclib.util.ModUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MigrationProfile {
	final Set<String> mods;
	final Map<String, String> idReplacements;
	final List<PatchFunction<CompoundTag, Boolean>> levelPatchers;
	final List<Patch> worldDataPatchers;
	final Map<String, List<String>> worldDataIDPaths;
	
	private final CompoundTag config;
	private CompoundTag level;
	private File levelBaseDir;
	private boolean prePatchChangedLevelDat;
	private boolean didRunPrePatch;
	private Exception prePatchException;
	
	MigrationProfile(CompoundTag config) {
		this.config = config;
		
		this.mods = Collections.unmodifiableSet(Patch.getALL()
													 .stream()
													 .map(p -> p.modID)
													 .collect(Collectors.toSet()));
		
		HashMap<String, String> replacements = new HashMap<String, String>();
		List<PatchFunction<CompoundTag, Boolean>> levelPatches = new LinkedList<>();
		List<Patch> worldDataPatches = new LinkedList<>();
		HashMap<String, List<String>> worldDataIDPaths = new HashMap<>();
		for (String modID : mods) {

			Patch.getALL()
				 .stream()
				 .filter(p -> p.modID.equals(modID))
				 .forEach(patch -> {
					 List<String> paths = patch.getWorldDataIDPaths();
					 if (paths!=null) worldDataIDPaths.put(modID, paths);

					 if (currentPatchLevel(modID) < patch.level) {
						 replacements.putAll(patch.getIDReplacements());
						 if (patch.getLevelDatPatcher()!=null)
							 levelPatches.add(patch.getLevelDatPatcher());
						 if (patch.getWorldDataPatcher()!=null)
							 worldDataPatches.add(patch);
						 DataFixerAPI.LOGGER.info("Applying " + patch);
					 }
					 else {
						 DataFixerAPI.LOGGER.info("Ignoring " + patch);
					 }
				 });
		}

		this.worldDataIDPaths = Collections.unmodifiableMap(worldDataIDPaths);
		this.idReplacements = Collections.unmodifiableMap(replacements);
		this.levelPatchers = Collections.unmodifiableList(levelPatches);
		this.worldDataPatchers = Collections.unmodifiableList(worldDataPatches);
	}
	
	final public CompoundTag getLevelDat(File levelBaseDir){
		if (level == null || this.levelBaseDir==null || !this.levelBaseDir.equals(levelBaseDir)){
			runPrePatches(levelBaseDir);
		}
		return level;
	}
	
	final public boolean isLevelDatChanged(){
		return prePatchChangedLevelDat;
	}
	
	final public File getLevelDatFile(){
		return new File(levelBaseDir, "level.dat");
	}
	
	final public Exception getPrePatchException(){
		return prePatchException;
	}
	
	
	final public void runPrePatches(File levelBaseDir){
		if (didRunPrePatch){
			BCLib.LOGGER.warning("Already did run PrePatches for " + this.levelBaseDir + ".");
		}
		BCLib.LOGGER.info("Running Pre Patchers on " + levelBaseDir);
		
		this.levelBaseDir = levelBaseDir;
		this.level = null;
		this.prePatchException = null;
		didRunPrePatch = true;
		
		this.prePatchChangedLevelDat = runPreLevelPatches(getLevelDatFile());
	}
	
	private boolean runPreLevelPatches(File levelDat){
		try {
			level = NbtIo.readCompressed(levelDat);
			
			boolean changed = patchLevelDat(level);
			return changed;
		}
		catch (IOException | PatchDidiFailException e) {
			prePatchException = e;
			return false;
		}
	}
	
	final public void markApplied() {
		for (String modID : mods) {
			DataFixerAPI.LOGGER.info("Updating Patch-Level for '{}' from {} to {}", modID, currentPatchLevel(modID), Patch.maxPatchLevel(modID));
			config.putString(modID, Patch.maxPatchVersion(modID));
		}
	}
	
	public String currentPatchVersion(@NotNull String modID) {
		if (!config.contains(modID)) return "0.0.0";
		return config.getString(modID);
	}
	
	public int currentPatchLevel(@NotNull String modID) {
		return ModUtil.convertModVersion(currentPatchVersion(modID));
	}
	
	public boolean hasAnyFixes() {
		boolean hasLevelDatPatches;
		if (didRunPrePatch != false) {
			hasLevelDatPatches = prePatchChangedLevelDat;
		} else {
			hasLevelDatPatches = levelPatchers.size()>0;
		}
		
		return idReplacements.size() > 0 || hasLevelDatPatches || worldDataPatchers.size() > 0;
	}

	public String replaceStringFromIDs(@NotNull String val) {
		final String replace = idReplacements.get(val);
		return replace;
	}
	
	public boolean replaceStringFromIDs(@NotNull CompoundTag tag, @NotNull String key) {
		if (!tag.contains(key)) return false;

		final String val = tag.getString(key);
		final String replace = idReplacements.get(val);
		
		if (replace != null) {
			DataFixerAPI.LOGGER.warning("Replacing ID '{}' with '{}'.", val, replace);
			tag.putString(key, replace);
			return true;
		}
		
		return false;
	}

	private boolean replaceIDatPath(@NotNull ListTag list, @NotNull String[] parts, int level){
		boolean[] changed = {false};
		if (level == parts.length-1) {
			DataFixerAPI.fixItemArrayWithID(list, changed, this, true);
		} else {
			list.forEach(inTag -> changed[0] |= replaceIDatPath((CompoundTag)inTag, parts, level+1));
		}
		return changed[0];
	}

	private boolean replaceIDatPath(@NotNull CompoundTag tag, @NotNull String[] parts, int level){
		boolean changed = false;
		for (int i=level; i<parts.length-1; i++) {
			final String part = parts[i];
			if (tag.contains(part)) {
				final byte type = tag.getTagType(part);
				if (type == Tag.TAG_LIST) {
					ListTag list = tag.getList(part, Tag.TAG_COMPOUND);
					return replaceIDatPath(list, parts, i);
				} else if (type == Tag.TAG_COMPOUND) {
					tag = tag.getCompound(part);
				}
			} else {
				return false;
			}
		}

		if (tag!=null && parts.length>0) {
			final String key = parts[parts.length-1];
			final byte type = tag.getTagType(key);
			if (type == Tag.TAG_LIST) {
				final ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
				final boolean[] _changed = {false};
				if (list.size()==0) {
					_changed[0] = DataFixerAPI.fixStringIDList(tag, key, this);
				} else {
					DataFixerAPI.fixItemArrayWithID(list, _changed, this, true);
				}
				return _changed[0];
			} else  if (type == Tag.TAG_STRING) {
				return replaceStringFromIDs(tag, key);
			} else if (type == Tag.TAG_COMPOUND) {
				final CompoundTag cTag = tag.getCompound(key);
				boolean[] _changed = {false};
				DataFixerAPI.fixID(cTag, _changed, this, true);
				return _changed[0];
			}
		}


		return false;
	}

	public boolean replaceIDatPath(@NotNull CompoundTag root, @NotNull String path){
		String[] parts = path.split("\\.");
		return replaceIDatPath(root, parts, 0);
	}
	
	public boolean patchLevelDat(@NotNull CompoundTag level) throws PatchDidiFailException {
		boolean changed = false;
		for (PatchFunction<CompoundTag, Boolean> f : levelPatchers) {
			changed |= f.apply(level, this);
		}
		return changed;
	}

	public void patchWorldData() throws PatchDidiFailException {
		for (Patch patch : worldDataPatchers) {
			CompoundTag root = WorldDataAPI.getRootTag(patch.modID);
			boolean changed = patch.getWorldDataPatcher().apply(root, this);
			if (changed) {
				WorldDataAPI.saveFile(patch.modID);
			}
		}

		for (Map.Entry<String, List<String>> entry : worldDataIDPaths.entrySet()){
			CompoundTag root = WorldDataAPI.getRootTag(entry.getKey());
			boolean[] changed = {false};
			entry.getValue().forEach(path -> {
				changed[0] |= replaceIDatPath(root, path);
			});

			if (changed[0]){
				WorldDataAPI.saveFile(entry.getKey());
			}
		}
	}
}
