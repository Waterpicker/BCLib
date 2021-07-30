package ru.bclib.api.datafixer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jetbrains.annotations.NotNull;
import ru.bclib.BCLib;
import ru.bclib.api.WorldDataAPI;
import ru.bclib.config.Configs;
import ru.bclib.gui.screens.ConfirmFixScreen;
import ru.bclib.util.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API to manage Patches that need to get applied to a world
 */
public class DataFixerAPI {
	static final Logger LOGGER = new Logger("DataFixerAPI");
	static class State {
		public boolean didFail = false;
	}
	
	@FunctionalInterface
	public static interface Callback {
		public void call();
	}
	
	private static boolean wrapCall(LevelStorageSource levelSource, String levelID, Function<LevelStorageAccess, Boolean> runWithLevel) {
		
		LevelStorageSource.LevelStorageAccess levelStorageAccess;
		try {
			levelStorageAccess = levelSource.createAccess(levelID);
		} catch (IOException e) {
			BCLib.LOGGER.warning((String)"Failed to read level {} data", levelID, e);
			SystemToast.onWorldAccessFailure(Minecraft.getInstance(), levelID);
			Minecraft.getInstance().setScreen((Screen)null);
			return true;
		}
		
		boolean returnValue = runWithLevel.apply(levelStorageAccess);
		
		try {
			levelStorageAccess.close();
		} catch (IOException e) {
			BCLib.LOGGER.warning((String)"Failed to unlock access to level {}", levelID, e);
		}
		
		return returnValue;
	}
	
	/**
	 * Will apply necessary Patches to the world.
	 *
	 * @param levelSource The SourceStorage for this Minecraft instance, You can get this using
	 * {@code Minecraft.getInstance().getLevelSource()}
	 * @param levelID The ID of the Level you want to patch
	 * @param showUI {@code true}, if you want to present the user with a Screen that offers to backup the world
	 *                              before applying the patches
	 * @param onResume When this method retursn {@code true}, this function will be called when the world is ready
	 * @return {@code true} if the UI was displayed. The UI is only displayed if {@code showUI} was {@code true} and
	 * patches were enabled in the config and the Guardian did find any patches that need to be applied to the world.
	 *
	 */
	public static boolean fixData(LevelStorageSource levelSource, String levelID, boolean showUI, Consumer<Boolean> onResume) {
		return wrapCall(levelSource, levelID, (levelStorageAccess) -> fixData(levelStorageAccess, showUI, onResume));
	}
	
	/**
	 * Will apply necessary Patches to the world.
	 *
	 * @param levelStorageAccess The access class of the level you want to patch
	 * @param showUI {@code true}, if you want to present the user with a Screen that offers to backup the world
	 *                              before applying the patches
	 * @param onResume When this method retursn {@code true}, this function will be called when the world is ready
	 * @return {@code true} if the UI was displayed. The UI is only displayed if {@code showUI} was {@code true} and
	 * patches were enabled in the config and the Guardian did find any patches that need to be applied to the world.
	 *
	 */
	public static boolean fixData(LevelStorageSource.LevelStorageAccess levelStorageAccess, boolean showUI, Consumer<Boolean> onResume){
		File levelPath = levelStorageAccess.getLevelPath(LevelResource.ROOT).toFile();
		
		boolean newWorld = false;
		if (!levelPath.exists()) {
			BCLib.LOGGER.info("Creating a new World, no fixes needed");
			newWorld = true;
		}
		
		initializeWorldData(levelPath, newWorld);
		if (newWorld) return false;
		
		return fixData(levelPath, levelStorageAccess.getLevelId(), showUI, onResume);
	}
	/**
	 * Initializes the DataStorage for this world. If the world is new, the patch registry is initialized to the
	 * current versions of the plugins.
	 * <p>
	 * This implementation will create a new  {@link LevelStorageAccess} and call {@link #initializeWorldData(File, boolean)}
	 * using the provided root path.
	 * @param levelSource The SourceStorage for this Minecraft instance, You can get this using
	 * {@code Minecraft.getInstance().getLevelSource()}
	 * @param levelID The ID of the Level you want to patch
	 * @param newWorld {@code true} if this is a fresh world
	 */
	public static void initializeWorldData(LevelStorageSource levelSource, String levelID, boolean newWorld) {
		wrapCall(levelSource, levelID, (levelStorageAccess) -> {
			initializeWorldData(levelStorageAccess.getLevelPath(LevelResource.ROOT).toFile(), newWorld);
			return true;
		});
	}
	
	/**
	 * Initializes the DataStorage for this world. If the world is new, the patch registry is initialized to the
	 * current versions of the plugins.
	 * @param levelPath Folder of the world
	 * @param newWorld {@code true} if this is a fresh world
	 *
	 */
	public static void initializeWorldData(File levelPath, boolean newWorld){
		WorldDataAPI.load(new File(levelPath, "data"));
		
		if (newWorld){
			getMigrationProfile().markApplied();
			WorldDataAPI.saveFile(BCLib.MOD_ID);
		}
	}
	
	private static boolean fixData(File dir, String levelID, boolean showUI, Consumer<Boolean> onResume) {
		MigrationProfile profile = loadProfileIfNeeded();
		
		Consumer<Boolean> runFixes = (applyFixes) -> {
			if (applyFixes) {
				runDataFixes(dir, profile, new ProgressListener() {
					private long timeStamp = Util.getMillis();

					public void progressStartNoAbort(Component component) {
					}

					public void progressStart(Component component) {
					}

					public void progressStagePercentage(int i) {
						if (Util.getMillis() - this.timeStamp >= 1000L) {
							this.timeStamp = Util.getMillis();
							BCLib.LOGGER.info((String) "Patching... {}%", (Object) i);
						}
					}

					public void stop() {
					}

					public void progressStage(Component component) {
					}
				});
			} else {
				//System.out.println("NO FIXES");
			}
			
			//UI is asynchronous, so we need to send the callback now
			if (profile != null && showUI) {
				onResume.accept(applyFixes);
			}
		};
		
		//we have some migrations
		if (profile != null) {
			//display the confirm UI.
			if (showUI){
				showBackupWarning(levelID, runFixes);
				return true;
			} else {
				BCLib.LOGGER.warning("Applying Fixes on Level");
				runFixes.accept(true);
			}
		}
		return false;
	}
	
	private static MigrationProfile loadProfileIfNeeded(){
		if (!Configs.MAIN_CONFIG.getBoolean(Configs.MAIN_PATCH_CATEGORY, "applyPatches", true)) {
			LOGGER.info("World Patches are disabled");
			return null;
		}
		
		MigrationProfile profile = getMigrationProfile();
		
		if (!profile.hasAnyFixes()) {
			LOGGER.info("Everything up to date");
			return null;
		 }
		
		return profile;
	}
	
	@NotNull
	private static MigrationProfile getMigrationProfile() {
		final CompoundTag patchConfig = WorldDataAPI.getCompoundTag(BCLib.MOD_ID, Configs.MAIN_PATCH_CATEGORY);
		MigrationProfile profile = Patch.createMigrationData(patchConfig);
		return profile;
	}
	
	@Environment(EnvType.CLIENT)
	static void showBackupWarning(String levelID, Consumer<Boolean> whenFinished){
		TranslatableComponent promptText = new TranslatableComponent("bclib.datafixer.backupWarning.prompt");
		TranslatableComponent buttonTitle = new TranslatableComponent("bclib.datafixer.backupWarning.button");
		
		Minecraft.getInstance().setScreen(new ConfirmFixScreen((Screen) null, (createBackup, applyFixes) -> {
			if (createBackup) {
				EditWorldScreen.makeBackupAndShowToast(Minecraft.getInstance().getLevelSource(), levelID);
			}
			
			Minecraft.getInstance().setScreen((Screen)null);
			whenFinished.accept(applyFixes);
		}));
		
	}

	private static void runDataFixes(File dir, MigrationProfile profile, ProgressListener progress) {
		State state = new State();
		
		List<File> regions = getAllRegions(dir, null);
		progress.progressStagePercentage(0);
		int[] count = {0};
		regions.parallelStream().forEach((file) -> {
			fixRegion(profile, state, file);
			count[0]++;
			progress.progressStagePercentage((100 * count[0])/regions.size());
		});
		progress.stop();

		List<File> players = getAllPlayers(dir);
		players.parallelStream().forEach((file) -> fixPlayer(profile, state, file));

		fixLevel(profile, state, new File(dir, "level.dat"));
		
		if (!state.didFail) {
			profile.markApplied();
			WorldDataAPI.saveFile(BCLib.MOD_ID);
		}
	}
	
	private static void fixLevel(MigrationProfile data, State state, File file) {
		try {
			LOGGER.info("Inspecting " + file);
			CompoundTag level = NbtIo.readCompressed(file);
			boolean[] changed = { false };

			if (level.contains("Data")) {
				CompoundTag dataTag = (CompoundTag)level.get("Data");
				if (dataTag.contains("Player")) {
					CompoundTag player = (CompoundTag)dataTag.get("Player");
					fixPlayerNbt(player, changed, data);
				}
			}
			
			try {
				changed[0] |= data.patchLevelDat(level);
			} catch (PatchDidiFailException e){
				state.didFail = true;
				BCLib.LOGGER.error(e.getMessage());
			}

			if (changed[0]) {
				LOGGER.warning("Writing '{}'", file);
				NbtIo.writeCompressed(level, file);
			}
		}
		catch (Exception e) {
			BCLib.LOGGER.error("Failed fixing Level-Data.");
			state.didFail = true;
			e.printStackTrace();
		}
	}

	private static void fixPlayer(MigrationProfile data, State state, File file) {
		try {
			LOGGER.info("Inspecting " + file);
			CompoundTag player = NbtIo.readCompressed(file);
			boolean[] changed = { false };
			fixPlayerNbt(player, changed, data);

			if (changed[0]) {
				LOGGER.warning("Writing '{}'", file);
				NbtIo.writeCompressed(player, file);
			}
		}
		catch (Exception e) {
			BCLib.LOGGER.error("Failed fixing Player-Data.");
			state.didFail = true;
			e.printStackTrace();
		}
	}

	private static void fixPlayerNbt(CompoundTag player, boolean[] changed, MigrationProfile data) {
		//Checking Inventory
		ListTag inventory = player.getList("Inventory", 10);
		fixInventory(inventory, changed, data, true);

		//Checking EnderChest
		ListTag enderitems = player.getList("EnderItems", 10);
		fixInventory(enderitems, changed, data, true);
	}

	private static void fixRegion(MigrationProfile data, State state, File file) {
		try {
			LOGGER.info("Inspecting " + file);
			boolean[] changed = new boolean[1];
			RegionFile region = new RegionFile(file, file.getParentFile(), true);
			
			for (int x = 0; x < 32; x++) {
				for (int z = 0; z < 32; z++) {
					ChunkPos pos = new ChunkPos(x, z);
					changed[0] = false;
					if (region.hasChunk(pos)) {
						DataInputStream input = region.getChunkDataInputStream(pos);
						CompoundTag root = NbtIo.read(input);
						// if ((root.toString().contains("betternether:chest") || root.toString().contains("bclib:chest"))) {
						//   NbtIo.write(root, new File(file.toString() + "-" + x + "-" + z + ".nbt"));
						// }
						input.close();

						//Checking TileEntities
						ListTag tileEntities = root.getCompound("Level")
												   .getList("TileEntities", 10);
						fixItemArrayWithID(tileEntities, changed, data, true);

						//Checking Entities
						ListTag entities = root.getList("Entities", 10);
						fixItemArrayWithID(entities, changed, data, true);

						//Checking Block Palette
						ListTag sections = root.getCompound("Level")
											   .getList("Sections", 10);
						sections.forEach((tag) -> {
							ListTag palette = ((CompoundTag) tag).getList("Palette", 10);
							palette.forEach((blockTag) -> {
								CompoundTag blockTagCompound = ((CompoundTag) blockTag);
								changed[0] |= data.replaceStringFromIDs(blockTagCompound, "Name");
							});
						});

						if (changed[0]) {
							LOGGER.warning("Writing '{}': {}/{}", file, x, z);
							// NbtIo.write(root, new File(file.toString() + "-" + x + "-" + z + "-changed.nbt"));
							DataOutputStream output = region.getChunkDataOutputStream(pos);
							NbtIo.write(root, output);
							output.close();

						}
					}
				}
			}
			region.close();
		}
		catch (Exception e) {
			BCLib.LOGGER.error("Failed fixing Player Data.");
			state.didFail = true;
			e.printStackTrace();
		}
	}

	static CompoundTag patchConfTag = null;
	static CompoundTag getPatchData(){
		if (patchConfTag==null) {
			patchConfTag = WorldDataAPI.getCompoundTag(BCLib.MOD_ID, Configs.MAIN_PATCH_CATEGORY);
		}
		return patchConfTag;
	}

	private static void fixInventory(ListTag inventory, boolean[] changed, MigrationProfile data, boolean recursive) {
		inventory.forEach(item -> {
			changed[0] |= data.replaceStringFromIDs((CompoundTag)item, "id");

			if (((CompoundTag) item).contains("tag")) {
				CompoundTag tag = (CompoundTag)((CompoundTag) item).get("tag");
				if (tag.contains("BlockEntityTag")){
					CompoundTag blockEntityTag = (CompoundTag)((CompoundTag) tag).get("BlockEntityTag");
					ListTag items = blockEntityTag.getList("Items", 10);
					fixItemArrayWithID(items, changed, data, recursive);
				}
			}
		});
	}

	private static void fixItemArrayWithID(ListTag items, boolean[] changed, MigrationProfile data, boolean recursive) {
		items.forEach(inTag -> {
			fixID((CompoundTag) inTag, changed, data, recursive);
		});
	}


	private static void fixID(CompoundTag inTag, boolean[] changed, MigrationProfile data, boolean recursive) {
		final CompoundTag tag = inTag;

		changed[0] |= data.replaceStringFromIDs(tag, "id");
		if (tag.contains("Item")) {
			CompoundTag item = (CompoundTag)tag.get("Item");
			fixID(item, changed, data, recursive);
		}

		if (recursive && tag.contains("Items")) {
			fixItemArrayWithID(tag.getList("Items", 10), changed, data, true);
		}
		if (recursive && tag.contains("Inventory")) {
			ListTag inventory = tag.getList("Inventory", 10);
			fixInventory(inventory, changed, data, recursive);
		}
	}

	private static List<File> getAllPlayers(File dir) {
		List<File> list = new ArrayList<>();
		dir = new File(dir, "playerdata");
		for (File file : dir.listFiles()) {
			if (file.isFile() && file.getName().endsWith(".dat")) {
				list.add(file);
			}
		}
		return list;
	}
	
	private static List<File> getAllRegions(File dir, List<File> list) {
		if (list == null) {
			list = new ArrayList<>();
		}
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				getAllRegions(file, list);
			} else if (file.isFile() && file.getName().endsWith(".mca")) {
				list.add(file);
			}
		}
		return list;
	}
	
	/**
	 * register a new Patch
	 *
	 * @param patch A #Supplier that will instantiate the new Patch Object
	 */
	public static void registerPatch(Supplier<Patch> patch) {
		Patch.getALL().add(patch.get());
	}
	
	/**
	 * Get mod version from string. String should be in format: %d.%d.%d
	 *
	 * @param version - {@link String} mod version.
	 * @return int mod version.
	 */
	public static int getModVersion(String version) {
		if (version.isEmpty()) {
			return 0;
		}
		try {
			int res = 0;
			final String semanticVersionPattern = "(\\d+)\\.(\\d+)\\.(\\d+)\\D*";
			final Matcher matcher = Pattern.compile(semanticVersionPattern)
										   .matcher(version);
			if (matcher.find()) {
				if (matcher.groupCount() > 0) res = (Integer.parseInt(matcher.group(1)) & 0xFF) << 22;
				if (matcher.groupCount() > 1) res |= (Integer.parseInt(matcher.group(2)) & 0xFF) << 14;
				if (matcher.groupCount() > 2) res |= Integer.parseInt(matcher.group(3)) & 0x3FFF;
			}
			
			return res;
		}
		catch (Exception e) {
			return 0;
		}
	}
	
	/**
	 * Get mod version from integer. String will be in format %d.%d.%d
	 *
	 * @param version - mod version in integer form.
	 * @return {@link String} mod version.
	 */
	public static String getModVersion(int version) {
		int a = (version >> 22) & 0xFF;
		int b = (version >> 14) & 0xFF;
		int c = version & 0x3FFF;
		return String.format(Locale.ROOT, "%d.%d.%d", a, b, c);
	}
	
}