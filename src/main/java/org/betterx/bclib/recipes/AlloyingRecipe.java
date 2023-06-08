package org.betterx.bclib.recipes;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.config.PathConfig;
import org.betterx.bclib.interfaces.AlloyingRecipeWorkstation;
import org.betterx.bclib.interfaces.UnknownReceipBookCategory;
import org.betterx.bclib.util.ItemUtil;
import org.betterx.bclib.util.RecipeHelper;

import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AlloyingRecipe implements Recipe<Container>, UnknownReceipBookCategory {
    public final static String GROUP = "alloying";
    public final static RecipeType<AlloyingRecipe> TYPE = BCLRecipeManager.registerType(BCLib.MOD_ID, GROUP);
    public final static Serializer SERIALIZER = BCLRecipeManager.registerSerializer(
            BCLib.MOD_ID,
            GROUP,
            new Serializer()
    );

    protected final RecipeType<?> type;
    protected final ResourceLocation id;
    protected final Ingredient primaryInput;
    protected final Ingredient secondaryInput;
    protected final ItemStack output;
    protected final String group;
    protected final float experience;
    protected final int smeltTime;

    public AlloyingRecipe(
            ResourceLocation id,
            String group,
            Ingredient primaryInput,
            Ingredient secondaryInput,
            ItemStack output,
            float experience,
            int smeltTime
    ) {
        this.group = group;
        this.id = id;
        this.primaryInput = primaryInput;
        this.secondaryInput = secondaryInput;
        this.output = output;
        this.experience = experience;
        this.smeltTime = smeltTime;
        this.type = TYPE;
    }

    public float getExperience() {
        return this.experience;
    }

    public int getSmeltTime() {
        return this.smeltTime;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> defaultedList = NonNullList.create();
        defaultedList.add(primaryInput);
        defaultedList.add(secondaryInput);

        return defaultedList;
    }

    @Override
    public boolean matches(Container inv, Level world) {
        return this.primaryInput.test(inv.getItem(0)) && this.secondaryInput.test(inv.getItem(1)) || this.primaryInput.test(
                inv.getItem(1)) && this.secondaryInput.test(inv.getItem(0));
    }

    @Override
    public ItemStack assemble(Container inv) {
        return this.output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem() {
        return this.output;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return this.type;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public String getGroup() {
        return this.group;
    }

    @OnlyIn(Dist.CLIENT)
    public ItemStack getToastSymbol() {
        return AlloyingRecipeWorkstation.getWorkstationIcon();
    }

    public static class Builder {
        private final static Builder INSTANCE = new Builder();
        private static boolean exist;

        static Builder create(ResourceLocation id, ItemLike output) {
            INSTANCE.id = id;
            INSTANCE.group = String.format("%s_%s", GROUP, id);
            INSTANCE.primaryInput = null;
            INSTANCE.secondaryInput = null;
            INSTANCE.output = output;
            INSTANCE.experience = 0.0F;
            INSTANCE.smeltTime = 350;
            INSTANCE.count = 1;
            INSTANCE.alright = RecipeHelper.exists(output);
            exist = true;

            return INSTANCE;
        }

        private int count = 1;
        private ResourceLocation id;
        private Ingredient primaryInput;
        private Ingredient secondaryInput;
        private ItemLike output;
        private String group;
        private float experience;
        private int smeltTime;
        private boolean alright = true;

        protected Builder() {
        }

        public Builder checkConfig(PathConfig config) {
            exist &= config.getBoolean("alloying", id.getPath(), true);
            return this;
        }

        public Builder setGroup(String group) {
            this.group = group;
            return this;
        }

        public Builder setOutputCount(int count) {
            this.count = count;
            return this;
        }

        public Builder setPrimaryInput(ItemLike... inputs) {
            for (ItemLike item : inputs) {
                this.alright &= RecipeHelper.exists(item);
            }
            this.primaryInput = Ingredient.of(inputs);
            return this;
        }

        public Builder setSecondaryInput(ItemLike... inputs) {
            for (ItemLike item : inputs) {
                this.alright &= RecipeHelper.exists(item);
            }
            this.secondaryInput = Ingredient.of(inputs);
            return this;
        }

        public Builder setPrimaryInput(TagKey<Item> input) {
            this.primaryInput = Ingredient.of(input);
            return this;
        }

        public Builder setSecondaryInput(TagKey<Item> input) {
            this.secondaryInput = Ingredient.of(input);
            return this;
        }

        public Builder setInput(ItemLike primaryInput, ItemLike secondaryInput) {
            this.setPrimaryInput(primaryInput);
            this.setSecondaryInput(secondaryInput);
            return this;
        }

        public Builder setInput(TagKey<Item> primaryInput, TagKey<Item> secondaryInput) {
            this.setPrimaryInput(primaryInput);
            this.setSecondaryInput(secondaryInput);
            return this;
        }

        public Builder setExperience(float amount) {
            this.experience = amount;
            return this;
        }

        public Builder setSmeltTime(int time) {
            this.smeltTime = time;
            return this;
        }

        public void build() {
            if (exist) {
                if (primaryInput == null) {
                    BCLib.LOGGER.warning(
                            "Primary input for Alloying recipe can't be 'null', recipe {} will be ignored!",
                            id
                    );
                    return;
                }
                if (secondaryInput == null) {
                    BCLib.LOGGER.warning(
                            "Secondary input for Alloying can't be 'null', recipe {} will be ignored!",
                            id
                    );
                    return;
                }
                if (output == null) {
                    BCLib.LOGGER.warning("Output for Alloying can't be 'null', recipe {} will be ignored!", id);
                    return;
                }
                if (BCLRecipeManager.getRecipe(TYPE, id) != null) {
                    BCLib.LOGGER.warning("Can't add Alloying recipe! Id {} already exists!", id);
                    return;
                }
                if (!alright) {
                    BCLib.LOGGER.debug("Can't add Alloying recipe {}! Ingeredient or output not exists.", id);
                    return;
                }
                BCLRecipeManager.addRecipe(
                        TYPE,
                        new AlloyingRecipe(
                                id,
                                group,
                                primaryInput,
                                secondaryInput,
                                new ItemStack(output, count),
                                experience,
                                smeltTime
                        )
                );
            }
        }
    }

    public static class Serializer implements RecipeSerializer<AlloyingRecipe> {
        @Override
        public AlloyingRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
            Ingredient primaryInput = Ingredient.fromJson(ingredients.get(0));
            Ingredient secondaryInput = Ingredient.fromJson(ingredients.get(1));
            String group = GsonHelper.getAsString(json, "group", "");
            JsonObject result = GsonHelper.getAsJsonObject(json, "result");
            ItemStack output = ItemUtil.fromJsonRecipe(result);
            if (output == null) {
                throw new IllegalStateException("Output item does not exists!");
            }
            float experience = GsonHelper.getAsFloat(json, "experience", 0.0F);
            int smeltTime = GsonHelper.getAsInt(json, "smelttime", 350);

            return new AlloyingRecipe(id, group, primaryInput, secondaryInput, output, experience, smeltTime);
        }

        @Override
        public AlloyingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf packetBuffer) {
            String group = packetBuffer.readUtf(32767);
            Ingredient primary = Ingredient.fromNetwork(packetBuffer);
            Ingredient secondary = Ingredient.fromNetwork(packetBuffer);
            ItemStack output = packetBuffer.readItem();
            float experience = packetBuffer.readFloat();
            int smeltTime = packetBuffer.readVarInt();

            return new AlloyingRecipe(id, group, primary, secondary, output, experience, smeltTime);
        }

        @Override
        public void toNetwork(FriendlyByteBuf packetBuffer, AlloyingRecipe recipe) {
            packetBuffer.writeUtf(recipe.group);
            recipe.primaryInput.toNetwork(packetBuffer);
            recipe.secondaryInput.toNetwork(packetBuffer);
            packetBuffer.writeItem(recipe.output);
            packetBuffer.writeFloat(recipe.experience);
            packetBuffer.writeVarInt(recipe.smeltTime);
        }
    }
}
