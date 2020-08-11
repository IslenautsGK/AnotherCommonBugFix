package com.anotherera.core;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ACBFModContainer extends DummyModContainer {

	private FMLEventChannel loliCardNetwork;

	public ACBFModContainer() {
		super(new ModMetadata());
		ModMetadata meta = getMetadata();
		meta.modId = "anothercommonbugfix";
		meta.name = "Another Common Bug Fix";
		meta.version = "@VERSION@";
		meta.authorList = Arrays.asList("Is_GK");
		meta.description = "Another Common Bug Fix";
		meta.url = "www.⚪⚪⚪.com";
	}

	@Override
	public boolean registerBus(EventBus bus, LoadController controller) {
		bus.register(this);
		return true;
	}

	@Subscribe
	public void modConstruction(FMLConstructionEvent evt) {
		if (ACBFClassTransformer.checkFixPacket) {
			NetworkRegistry.INSTANCE.register(this, this.getClass(), null, evt.getASMHarvestedData());
		}
	}

	@Subscribe
	public void init(FMLPreInitializationEvent event) {
		event.getModLog().info("Another Common Bug Fix Loaded");
		MinecraftForge.EVENT_BUS.register(this);
		loliCardNetwork = NetworkRegistry.INSTANCE.newEventDrivenChannel("checkFixResourceList");
		loliCardNetwork.register(this);
	}

	@SubscribeEvent
	public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
		if (ACBFClassTransformer.checkFixPacket && event.player instanceof EntityPlayerMP) {
			loliCardNetwork.sendTo(new FMLProxyPacket(new PacketBuffer(Unpooled.buffer()), "checkFixResourceList"),
					(EntityPlayerMP) event.player);
		}
	}

	@SubscribeEvent
	public void onServerPacket(FMLNetworkEvent.ServerCustomPacketEvent event) {
		EntityPlayerMP player = ((NetHandlerPlayServer) event.getHandler()).player;
		FMLProxyPacket packet = event.getPacket();
		if (packet != null && packet.channel().equals("checkFixResourceList")) {
			ByteBuf buffer = packet.payload();
			Map<String, String> clientFixPackets = Maps.newHashMap();
			int fixPacketCount = buffer.readInt();
			for (int i = 0; i < fixPacketCount; i++) {
				String fixPacketName = ByteBufUtils.readUTF8String(buffer);
				String md5 = ByteBufUtils.readUTF8String(buffer);
				clientFixPackets.put(fixPacketName, md5);
			}
			Map<String, String> clientEnableTransform = Maps.newHashMap();
			int enableTransformCount = buffer.readInt();
			for (int i = 0; i < fixPacketCount; i++) {
				String fixPacketName = ByteBufUtils.readUTF8String(buffer);
				String md5 = ByteBufUtils.readUTF8String(buffer);
				clientEnableTransform.put(fixPacketName, md5);
			}
			List<String> fieldPacket = Lists.newArrayList();
			for (Entry<String, String> entry : ACBFClassTransformer.fixPacketsMd5.entrySet()) {
				if (!clientFixPackets.containsKey(entry.getKey())
						|| !clientFixPackets.get(entry.getKey()).equals(entry.getValue())) {
					fieldPacket.add(entry.getKey());
				}
			}
			List<String> fieldTransform = Lists.newArrayList();
			for (Entry<String, String> entry : ACBFClassTransformer.enableTransformMd5.entrySet()) {
				if (!clientEnableTransform.containsKey(entry.getKey())
						|| !clientEnableTransform.get(entry.getKey()).equals(entry.getValue())) {
					fieldTransform.add(entry.getKey());
				}
			}
			if (!fieldPacket.isEmpty() || !fieldTransform.isEmpty()) {
				((NetHandlerPlayServer) event.getHandler()).disconnect(new TextComponentString(
						String.format("FixPacket rejections %s, Config rejections %s", fieldPacket, fieldTransform)));
			}
		}
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onClientPacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
		FMLProxyPacket packet = event.getPacket();
		if (packet != null && packet.channel().equals("checkFixResourceList")) {
			ByteBuf buffer = Unpooled.buffer();
			buffer.writeInt(ACBFClassTransformer.fixPacketsMd5.size());
			for (Entry<String, String> entry : ACBFClassTransformer.fixPacketsMd5.entrySet()) {
				ByteBufUtils.writeUTF8String(buffer, entry.getKey());
				ByteBufUtils.writeUTF8String(buffer, entry.getValue());
			}
			buffer.writeInt(ACBFClassTransformer.enableTransformMd5.size());
			for (Entry<String, String> entry : ACBFClassTransformer.enableTransformMd5.entrySet()) {
				ByteBufUtils.writeUTF8String(buffer, entry.getKey());
				ByteBufUtils.writeUTF8String(buffer, entry.getValue());
			}
			loliCardNetwork.sendToServer(new FMLProxyPacket(new PacketBuffer(buffer), "checkFixResourceList"));
		}
	}

	@Override
	public String toString() {
		return "CoreMod:" + getModId() + "{" + getVersion() + "}";
	}

}
