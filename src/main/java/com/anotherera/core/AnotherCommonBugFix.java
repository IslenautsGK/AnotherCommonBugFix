package com.anotherera.core;

import java.io.File;
import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

public class AnotherCommonBugFix implements IFMLLoadingPlugin {

	public static File coremodLocation = null;
	public static File mcLocation = null;

	@Override
	public String[] getASMTransformerClass() {
		return new String[] { "com.anotherera.core.ACBFClassTransformer" };
	}

	@Override
	public String getModContainerClass() {
		return "com.anotherera.core.ACBFModContainer";
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
		coremodLocation = (File) data.get("coremodLocation");
		mcLocation = (File) data.get("mcLocation");
		ACBFClassTransformer.init();
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
