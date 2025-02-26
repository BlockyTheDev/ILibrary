package de.ancash.minecraft.crafting;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.entity.Player;

import de.ancash.ILibrary;
import de.ancash.minecraft.nbt.utils.MinecraftVersion;

public class ICraftingManager {

	private static ICraftingManager singleton = new ICraftingManager();

	public static ICraftingManager getSingleton() {
		return singleton;
	}

	private Class<? extends IContainerWorkbench> clazz;

	private ICraftingManager() {

	}

	public void init(ILibrary il) {
		il.getLogger().info(
				"Init version specific " + getClass().getSimpleName() + " for " + MinecraftVersion.getVersion().name());
		if (!MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_14_R1)) {
			try {
				ContainerWorkbench_1_8_1_13.initReflection();
				il.getLogger().info("Reflection successfull!");
			} catch (ClassNotFoundException | NoSuchFieldException | SecurityException | NoSuchMethodException e) {
				il.getLogger().severe("Reflection failed:");
				e.printStackTrace();
				return;
			}
			clazz = ContainerWorkbench_1_8_1_13.class;
		} else if (!MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_17_R1)) {
			try {
				ContainerWorkbench_1_14_1_16.initReflection();
				il.getLogger().info("Reflection successfull!");
			} catch (IllegalArgumentException | ClassNotFoundException | NoSuchFieldException | SecurityException
					| NoSuchMethodException | IllegalAccessException e) {
				il.getLogger().severe("Reflection failed:");
				e.printStackTrace();
				return;
			}
			clazz = ContainerWorkbench_1_14_1_16.class;
		} else if (!MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_18_R1)) {
			try {
				ContainerWorkbench_1_17.initReflection();
			} catch (ClassNotFoundException | NoSuchFieldException | SecurityException | NoSuchMethodException
					| IllegalArgumentException | IllegalAccessException e) {
				il.getLogger().severe("Reflection failed:");
				e.printStackTrace();
				return;
			}
			clazz = ContainerWorkbench_1_17.class;
		} else if (MinecraftVersion.getVersion().equals(MinecraftVersion.MC1_18_R1)) {
			try {
				ContainerWorkbench_1_18_R1.initReflection();
			} catch (ClassNotFoundException | NoSuchFieldException | SecurityException | NoSuchMethodException
					| IllegalArgumentException | IllegalAccessException e) {
				il.getLogger().severe("Reflection failed:");
				e.printStackTrace();
				return;
			}
			clazz = ContainerWorkbench_1_18_R1.class;
		} else if (MinecraftVersion.getVersion().equals(MinecraftVersion.MC1_18_R2)) {
			try {
				ContainerWorkbench_1_18_R2.initReflection();
			} catch (ClassNotFoundException | NoSuchFieldException | SecurityException | NoSuchMethodException
					| IllegalArgumentException | IllegalAccessException e) {
				il.getLogger().severe("Reflection failed:");
				e.printStackTrace();
				return;
			}
			clazz = ContainerWorkbench_1_18_R2.class;
		} else {
			try {
				ContainerWorkbench_1_19_R1.initReflection();
			} catch (ClassNotFoundException | NoSuchFieldException | SecurityException | NoSuchMethodException
					| IllegalArgumentException | IllegalAccessException e) {
				il.getLogger().severe("Reflection failed:");
				e.printStackTrace();
				return;
			}
			clazz = ContainerWorkbench_1_19_R1.class;
		}

		il.getLogger().info("Using: " + clazz.getTypeName());
	}

	public IContainerWorkbench newInstance(Player player) {
		try {
			return clazz.getDeclaredConstructor(Player.class).newInstance(player);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			return null;
		}
	}
}