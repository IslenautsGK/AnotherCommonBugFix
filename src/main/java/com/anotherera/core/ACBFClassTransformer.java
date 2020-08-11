package com.anotherera.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class ACBFClassTransformer implements IClassTransformer {

	private static Logger log;
	private static ScriptEngine scriptEngine;
	private static Map<String, TransformResource> classTransform;
	private static Map<String, List<String>> enableTransform;
	private static Map<String, List<ConfigComment>> configMap;
	public static boolean checkFixPacket;
	public static Map<String, String> fixPacketsMd5;
	public static Map<String, String> enableTransformMd5;
	private static boolean inTransform;
	private static MethodHandles.Lookup lookup;
	private static boolean initSuccess;

	public static void init() {
		initField();
		File resourceDir;
		File configFile;
		try {
			resourceDir = getResourceDir();
			configFile = getConfigFile();
		} catch (IOException e) {
			log.error("Init failed");
			return;
		}
		loadConfig(configFile);
		boolean configChange = loadFixPackets(resourceDir);
		if (configChange) {
			saveConfig(configFile);
		}
		grnerateConfigMd5();
		initSuccess = true;
	}

	private static void initField() {
		log = LogManager.getLogger("anothercommonbugfix");
		scriptEngine = new ScriptEngineManager(null).getEngineByName("nashorn");
		classTransform = Maps.newHashMap();
		enableTransform = Maps.newHashMap();
		configMap = Maps.newHashMap();
		fixPacketsMd5 = Maps.newHashMap();
		enableTransformMd5 = Maps.newHashMap();
		inTransform = false;
		lookup = MethodHandles.lookup();
		initSuccess = false;
	}

	private static File getResourceDir() throws IOException {
		File resourceDir = new File(AnotherCommonBugFix.mcLocation, "fixResource");
		if (resourceDir.isFile()) {
			resourceDir.delete();
		}
		if (!resourceDir.exists()) {
			resourceDir.mkdir();
		}
		if (resourceDir.isFile()) {
			throw new IOException("fixResource not a dir");
		}
		if (!resourceDir.exists()) {
			throw new IOException("fixResource not exists");
		}
		return resourceDir;
	}

	private static File getConfigFile() throws IOException {
		File configFile = new File(AnotherCommonBugFix.mcLocation, "config/AnotherCommonBugFix.cfg");
		if (configFile.exists() && configFile.isDirectory()) {
			configFile.delete();
		}
		if (!configFile.exists()) {
			configFile.createNewFile();
		}
		return configFile;
	}

	private static void loadConfig(File configFile) {
		log.info("Load config");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), "utf-8"));) {
			String line = null;
			String comment = null;
			String curPacketFileName = null;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.charAt(0) == 0xFEFF) {
					line = line.substring(1);
				}
				if (line.isEmpty()) {
					continue;
				}
				int index = line.indexOf('#');
				if (index == 0) {
					comment = line;
					continue;
				} else if (index > 0) {
					line = line.substring(0, index).trim();
				}
				if (line.equals("checkFixPacket")) {
					comment = null;
					checkFixPacket = true;
					log.info("enable checkFixPacket");
				} else if (line.startsWith("-")) {
					if (curPacketFileName == null) {
						comment = null;
						log.warn("Unknow group transform:" + line.substring(1));
					} else {
						enableTransform.get(curPacketFileName).add(line.substring(1));
						configMap.get(curPacketFileName).add(new ConfigComment(comment, line.substring(1)));
						log.info("Enable transform:" + curPacketFileName + ":" + line.substring(1));
						comment = null;
					}
				} else if (line.endsWith(".zip")) {
					comment = null;
					curPacketFileName = line;
					enableTransform.put(curPacketFileName, Lists.newArrayList());
					configMap.put(curPacketFileName, Lists.newArrayList());
				}
			}
		} catch (IOException e) {
			log.warn("Load config failed");
		}
	}

	private static void saveConfig(File configFile) {
		log.info("Save config");
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(configFile), "utf-8"));) {
			for (Entry<String, List<ConfigComment>> entry : configMap.entrySet()) {
				bw.write(entry.getKey());
				bw.newLine();
				for (ConfigComment name : entry.getValue()) {
					if (name.comment != null) {
						bw.write(name.comment);
						bw.newLine();
					}
					bw.write("-");
					bw.write(name.config);
					bw.newLine();
				}
				bw.newLine();
			}
		} catch (IOException e) {
			log.warn("Save config failed");
		}
	}

	private static boolean loadFixPackets(File resourceDir) {
		log.info("Load fix packet");
		boolean configChange = false;
		for (File file : resourceDir.listFiles(file -> file.isFile() && file.getName().endsWith(".zip"))) {
			ZipFile zipFile;
			try {
				zipFile = new ZipFile(file);
			} catch (IOException e) {
				log.warn("Failed to open packet:" + file.getName());
				continue;
			}
			ZipEntry entry = zipFile.getEntry("transform.cfg");
			if (entry == null) {
				log.warn("Not a fix packet:" + file.getName());
				continue;
			}
			try (FileInputStream fis = new FileInputStream(file)) {
				fixPacketsMd5.put(file.getName(), DigestUtils.md5Hex(fis));
			} catch (IOException e) {
				fixPacketsMd5.put(file.getName(), "ERROR");
				log.warn("Failed to grnerate Packet Md5:" + file.getName());
			}
			try {
				((LaunchClassLoader) Thread.currentThread().getContextClassLoader()).addURL(file.toURI().toURL());
			} catch (MalformedURLException e1) {
				log.warn("Failed to add url:" + file.getName());
			}
			boolean isNew = !enableTransform.containsKey(file.getName());
			if (isNew) {
				log.info("Find new packet:" + file.getName());
				log.info("Add this packet to config:" + file.getName());
				enableTransform.put(file.getName(), Lists.newArrayList());
				configMap.put(file.getName(), Lists.newArrayList());
				configChange = true;
			} else {
				log.info("Find packet:" + file.getName());
			}
			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(zipFile.getInputStream(entry), "utf-8"));) {
				String line = null;
				String comment = null;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty()) {
						continue;
					}
					if (line.charAt(0) == 0xFEFF) {
						line = line.substring(1);
					}
					if (line.isEmpty()) {
						continue;
					}
					int index = line.indexOf('#');
					if (index == 0) {
						comment = line;
						continue;
					} else if (index > 0) {
						line = line.substring(0, index).trim();
					}
					if (line.startsWith("info:")) {
						comment = null;
						log.info(line.substring(5));
					} else {
						String[] map = line.split(" +");
						if (map.length == 2) {
							if (isNew) {
								log.info("Load new transform:" + line);
								enableTransform.get(file.getName()).add(map[0]);
								configMap.get(file.getName()).add(new ConfigComment(comment, map[0]));
								classTransform.put(map[0], new TransformResource(zipFile, map[1]));
							} else {
								if (enableTransform.get(file.getName()).contains(map[0])) {
									if (classTransform.containsKey(map[0])) {
										log.warn("Repeat transform" + line + ", use transform in " + file.getName());
									} else {
										log.info("Load transform:" + line);
									}
									classTransform.put(map[0], new TransformResource(zipFile, map[1]));
								} else {
									log.info("Disable transform:" + line);
								}
							}
							comment = null;
						} else if (map.length == 1) {
							try {
								Class<?> transformClazz = Class.forName(line);
								Object instance = transformClazz.newInstance();
								boolean enable = false;
								if (isNew) {
									log.info("Load new transform:" + line);
									enableTransform.get(file.getName()).add(line);
									configMap.get(file.getName()).add(new ConfigComment(comment, line));
									enable = true;
								} else {
									if (enableTransform.get(file.getName()).contains(line)) {
										log.info("Load transform:" + line);
										enable = true;
									} else {
										log.info("Disable transform:" + line);
										enable = false;
									}
								}
								if (enable) {
									for (Method method : transformClazz.getMethods()) {
										Transform transform = method.getAnnotation(Transform.class);
										if (transform != null) {
											String name = transform.value();
											if (classTransform.containsKey(name)) {
												log.warn("Repeat transform" + line + ", use transform in "
														+ file.getName());
											}
											classTransform.put(name, new TransformResource(zipFile, lookup,
													transformClazz, method, instance));
										}
									}
								}
								comment = null;
							} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
							}
						}
					}

				}
			} catch (IOException e) {
				log.warn("Load packet config failed:" + file.getName());
			}

		}
		return configChange;
	}

	private static void grnerateConfigMd5() {
		for (Entry<String, List<String>> entry : enableTransform.entrySet()) {
			StringBuilder sb = new StringBuilder();
			for (String name : entry.getValue()) {
				sb.append(name).append(";");
			}
			enableTransformMd5.put(entry.getKey(), DigestUtils.md5Hex(sb.toString()));
		}
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (initSuccess) {
			byte[] ofBytes = getClassBytes(name, transformedName, basicClass);
			if (ofBytes != null) {
				log.info("Transform:" + transformedName);
				return ofBytes;
			}
		}
		return basicClass;
	}

	private synchronized byte[] getClassBytes(String name, String transformedName, byte[] basicClass) {
		if (inTransform) {
			return null;
		}
		inTransform = true;
		try {
			if (classTransform.containsKey(transformedName)) {
				if (classTransform.get(transformedName).getHandle() != null) {
					try {
						return (byte[]) classTransform.get(transformedName).getHandle().invokeExact(basicClass);
					} catch (Throwable e) {
						log.warn("Transform fail:" + transformedName);
						e.printStackTrace();
					}
				} else if (classTransform.get(transformedName).name != null) {
					ZipEntry ze = classTransform.get(transformedName).zipFile
							.getEntry(classTransform.get(transformedName).name);
					if (ze != null) {
						if (classTransform.get(transformedName).name.endsWith(".js")) {
							try (InputStreamReader isr = new InputStreamReader(
									classTransform.get(transformedName).zipFile.getInputStream(ze));) {
								scriptEngine.put("basicClass", basicClass);
								scriptEngine.eval(isr);
								return (byte[]) scriptEngine.get("basicClass");
							} catch (ClassCastException | IOException | ScriptException e) {
								log.warn("Transform fail:" + transformedName);
								e.printStackTrace();
							}
						} else {
							try (InputStream in = classTransform.get(transformedName).zipFile.getInputStream(ze);) {
								return getTransformingBytes(in, ze.getSize(), name, transformedName);
							} catch (IOException e) {
								log.warn("Transform fail:" + transformedName);
								e.printStackTrace();
							}
						}
					}
				}
			}
			return null;
		} finally {
			inTransform = false;
		}
	}

	/**
	 * @deprecated 用这种方法修复有各种问题，而且对不用版本的mod的兼容性也不好，等时机成熟了就移除这个方法
	 */
	@Deprecated
	private byte[] getTransformingBytes(InputStream in, long size, String name, String transformedName)
			throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[2048];
		int len = 0;
		while ((len = in.read(buf)) > 0) {
			baos.write(buf, 0, len);
		}
		byte[] bytes = baos.toByteArray();
		baos.close();
		if (bytes.length != size) {
			return null;
		}
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl instanceof LaunchClassLoader) {
			@SuppressWarnings("resource")
			LaunchClassLoader lcl = (LaunchClassLoader) cl;
			for (IClassTransformer ict : lcl.getTransformers()) {
				if (ict instanceof ACBFClassTransformer) {
					break;
				}
				bytes = ict.transform(name, transformedName, bytes);
			}
			return bytes;
		}
		return null;
	}

	public static class TransformResource {

		public static MethodType TRANSFORM_METHOD_TYPE = MethodType.methodType(byte[].class, byte[].class);
		public static String TRANSFORM_METHOD_NAME = "transform";

		public ZipFile zipFile;
		public String name;
		private MethodHandle handle;
		private boolean cached;

		public TransformResource(ZipFile zipFile, String name) {
			this.zipFile = zipFile;
			this.name = name;
			this.handle = null;
			this.cached = false;
		}

		public TransformResource(ZipFile zipFile, MethodHandles.Lookup lookup, Class<?> clazz, Method method,
				Object source) {
			this.zipFile = zipFile;
			this.name = null;
			try {
				Class<?>[] parameters = method.getParameterTypes();
				if (parameters.length == 1 && parameters[0] == byte[].class && method.getReturnType() == byte[].class) {
					if ((method.getModifiers() & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC) {
						handle = lookup.findStatic(clazz, method.getName(), TRANSFORM_METHOD_TYPE);
					} else {
						if (source == null) {
							source = clazz.newInstance();
						}
						handle = lookup.findVirtual(clazz, method.getName(), TRANSFORM_METHOD_TYPE).bindTo(source);
					}
				}
			} catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
				e.printStackTrace();
			}
			this.cached = true;
		}

		public MethodHandle getHandle() {
			if (!cached && !name.contains("/") && !name.endsWith(".class") && !name.endsWith(".js")) {
				try {
					handle = lookup.findStatic(Class.forName(name), TRANSFORM_METHOD_NAME, TRANSFORM_METHOD_TYPE);
				} catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
				}
			}
			return handle;
		}

	}

	public static class ConfigComment {

		public String comment;
		public String config;

		public ConfigComment(String comment, String config) {
			if (comment != null && comment.isEmpty()) {
				comment = null;
			}
			this.comment = comment;
			this.config = config;
		}

	}

}
