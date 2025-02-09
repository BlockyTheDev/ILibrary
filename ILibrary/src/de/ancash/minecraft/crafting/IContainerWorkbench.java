package de.ancash.minecraft.crafting;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.material.MaterialData;

import de.ancash.datastructures.tuples.Duplet;
import de.ancash.datastructures.tuples.Tuple;
import de.ancash.minecraft.IItemStack;
import de.ancash.minecraft.crafting.recipe.ComplexRecipeWrapper;
import de.ancash.minecraft.crafting.recipe.ComplexRecipeWrapper.ComplexRecipeType;
import de.ancash.minecraft.nbt.utils.MinecraftVersion;

@SuppressWarnings("deprecation")
public abstract class IContainerWorkbench {

	static final ConcurrentHashMap<List<Integer>, Optional<Recipe>> cache = new ConcurrentHashMap<>();

	static final String VERSION = MinecraftVersion.getVersion().getPackageName();
	static final Map<Class<?>, Method> craftRecipeMethods = new ConcurrentHashMap<>();
	static Method playerToEntityHumanMethod;
	static Method iRecipeToBukkitRecipeMethod;
	static Method itemStackAsNMSCopyMethod;
	static Method nmsItemStackAsBukkitCopy;

	static {
		try {
			itemStackAsNMSCopyMethod = getCraftBukkitClass("inventory.CraftItemStack").getDeclaredMethod("asNMSCopy",
					ItemStack.class);
			playerToEntityHumanMethod = getCraftBukkitClass("entity.CraftPlayer").getDeclaredMethod("getHandle");
			if (!MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_17_R1)) {
				iRecipeToBukkitRecipeMethod = getNMSClass("IRecipe").getDeclaredMethod("toBukkitRecipe");
				nmsItemStackAsBukkitCopy = getCraftBukkitClass("inventory.CraftItemStack")
						.getDeclaredMethod("asBukkitCopy", getNMSClass("ItemStack"));

			} else {
				nmsItemStackAsBukkitCopy = getCraftBukkitClass("inventory.CraftItemStack")
						.getDeclaredMethod("asBukkitCopy", Class.forName("net.minecraft.world.item.ItemStack"));
				iRecipeToBukkitRecipeMethod = Class.forName("net.minecraft.world.item.crafting.IRecipe")
						.getDeclaredMethod("toBukkitRecipe");
			}
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
	}

	static Class<?> getCraftBukkitClass(String name) throws ClassNotFoundException {
		return Class.forName("org.bukkit.craftbukkit." + VERSION + "." + name);
	}

	static Class<?> getNMSClass(String name) throws ClassNotFoundException {
		return Class.forName("net.minecraft.server." + VERSION + "." + name);
	}

	static Object playerToEntityHuman(Player player) {
		try {
			return playerToEntityHumanMethod.invoke(player);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
			return null;
		}
	}

	public Recipe getRecipe(ItemStack[] ings) {
		return getRecipe(ings, Stream.of(ings).map(i -> i != null ? i.hashCode() : null).collect(Collectors.toList()));
	}

	public Recipe getRecipe(IItemStack[] ings, List<Integer> key) {
		return getRecipe(Stream.of(ings).map(i -> i != null ? i.getOriginal() : null).toArray(ItemStack[]::new), key);
	}

	public Recipe getRecipe(IItemStack[] ings) {
		return getRecipe(ings, Stream.of(ings).map(i -> i != null ? i.hashCode() : null).collect(Collectors.toList()));
	}

	public Recipe getRecipe(ItemStack[] ings, List<Integer> key) {
		if (!cache.containsKey(key)) {
			for (int i = 0; i < 9; i++)
				setItem(i, ings[i]);
			Recipe r = getCurrentRecipe();
			if (r != null && isComplexRecipe())
				r = craftRecipe(r, ings);
			for (int i = 0; i < 9; i++)
				setItem(i, null);
			cache.put(key, Optional.ofNullable(r));
		}
		return cache.get(key).orElse(null);
	}

	private Recipe craftRecipe(Recipe recipe, ItemStack[] ingredients) {
		ItemStack result = craftRecipe0(getCurrentIRecipe());
		if (result == null || result.getType() == Material.AIR)
			return null;
		ComplexRecipeWrapper complex = ComplexRecipeWrapper.newInstance(result, ComplexRecipeType.matchType(result));
		Duplet<String[], Map<Character, MaterialData>> duplet = mapIngredients(ingredients);
		complex.shape(duplet.getFirst());
		for (Entry<Character, MaterialData> entry : duplet.getSecond().entrySet())
			complex.setIngredient(entry.getKey(), entry.getValue());
		complex.computeIgnoreOnCraft();
		return complex;
	}

	private Duplet<String[], Map<Character, MaterialData>> mapIngredients(ItemStack[] ings) {
		Map<Character, MaterialData> map = new HashMap<>();
		int i = 48;

		StringBuilder builder = new StringBuilder();
		for (int a = 0; a < 3; a++) {
			for (int b = 0; b < 3; b++) {
				ItemStack item = ings[a * 3 + b];
				if (item == null) {
					builder.append(' ');
					continue;
				}
				for (Entry<Character, MaterialData> entry : map.entrySet()) {
					if (entry.getValue().equals(item.getData())) {
						builder.append(entry.getKey());
						item = null;
						break;
					}
				}
				if (item == null)
					continue;
				builder.append((char) i);
				map.put((char) i, item.getData());
				i++;
			}
			if (a < 2)
				builder.append('\n');
		}
		return Tuple.of(builder.toString().split("\n"), map);
	}

	private ItemStack craftRecipe0(Object iComplexRecipe) {
		try {
			Object result = getCraftRecipeMethod(iComplexRecipe).invoke(iComplexRecipe, getInventoryCrafting());
			ItemStack r = (ItemStack) nmsItemStackAsBukkitCopy.invoke(null, result);
			return r;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new IllegalStateException("Could not assemble IRecipeComplex", e);
		}
	}

	private Method getCraftRecipeMethod(Object complexRecipe) {
		Class<?> clazz = complexRecipe.getClass();
		return craftRecipeMethods.computeIfAbsent(clazz, key -> {
			for (Method m : clazz.getDeclaredMethods())
				if (m.getParameterCount() == 1
						&& m.getParameters()[0].getType().getCanonicalName().endsWith("InventoryCrafting")
						&& m.getReturnType().getCanonicalName().endsWith("ItemStack")) {
					m.setAccessible(true);
					return m;
				}
			throw new IllegalArgumentException("Could not find craft method for: " + complexRecipe.getClass());
		});
	}

	private boolean isComplexRecipe() {
		return (!(getCurrentRecipe() instanceof ShapedRecipe) && !(getCurrentRecipe() instanceof ShapelessRecipe))
				|| getCurrentIRecipe().getClass().getSuperclass().getCanonicalName().contains("Shape");
	}

	protected abstract Object getInventoryCrafting();

	public abstract Player getPlayer();

	public abstract Object getCurrentIRecipe();

	public abstract Recipe getCurrentRecipe();

	public abstract void setItem(int i, ItemStack item);

	public abstract ItemStack getItem(int i);
}