package kim.biryeong.minigamelib;

import kim.biryeong.minigamelib.camera.api.CameraEffects;
import kim.biryeong.minigamelib.cinematic.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static kim.biryeong.minigamelib.cinematic.CinematicData.*;

public final class LibraryGameTests {
    private static final Map<UUID,io.netty.channel.embedded.EmbeddedChannel> CHANNELS=new HashMap<>();
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "MinigameTest");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        var player = new net.minecraft.server.level.ServerPlayer(server, h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        CHANNELS.put(player.getUUID(),new io.netty.channel.embedded.EmbeddedChannel(connection));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, player, cookie);
        connection.setupInboundProtocol(net.minecraft.network.protocol.game.GameProtocols.SERVERBOUND_TEMPLATE.bind(
                net.minecraft.network.RegistryFriendlyByteBuf.decorator(server.registryAccess()), player.connection), player.connection);
        player.setPos(h.absoluteVec(new Vec3(1,2,1)));
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        MinigameLib.get(server).music().joined(player);
        return player;
    }
    private static boolean buildMain(java.nio.file.Path output) {
        try { return !eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.getInstance().build(output).hadIssues(); }
        catch (Exception e) { return false; }
    }
    private static void check(GameTestHelper h, boolean result, String message) { h.assertTrue(result,Component.literal(message)); }
    private static List<net.minecraft.network.protocol.Packet<?>> packets(net.minecraft.server.level.ServerPlayer player) {
        var out=new ArrayList<net.minecraft.network.protocol.Packet<?>>();var channel=CHANNELS.get(player.getUUID());channel.runPendingTasks();Object packet;
        while((packet=channel.readOutbound())!=null)if(packet instanceof net.minecraft.network.protocol.Packet<?> p)flatten(p,out);
        return out;
    }
    private static void flatten(net.minecraft.network.protocol.Packet<?> packet,List<net.minecraft.network.protocol.Packet<?>> out) {
        if(packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle)bundle.subPackets().forEach(p->flatten(p,out));else out.add(packet);
    }
    private static void remove(net.minecraft.server.level.ServerPlayer player) {
        var server=player.level().getServer();MinigameLib.get(server).editor().close(player);MinigameLib.get(server).cinematics().stop(player);
        MinigameLib.get(server).music().left(player);server.getPlayerList().remove(player);server.getPlayerList().deop(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
        var channel=CHANNELS.remove(player.getUUID());if(channel!=null)channel.finishAndReleaseAll();
    }
    @GameTest(structure="minigamelib-test:empty", maxTicks=100)
    public void frozenBodyInputDamageCrossDimensionAndRestore(GameTestHelper h) {
        var p=player(h); var server=h.getLevel().getServer();
        var pig=net.minecraft.world.entity.EntityTypes.PIG.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        pig.setPos(p.position());h.getLevel().addFreshEntity(pig);
        try {
            p.setGameMode(GameType.SURVIVAL);
            Vec3 body=p.position(); var world=p.level(); var mode=p.gameMode.getGameModeForPlayer();
            CameraEffects.setFov(p,75);
            var remote=server.getLevel(Level.NETHER);
            var view=CameraViews.open(p,remote,new Vec3(1500,90,1500),179,15);
            check(h,p.position().equals(body)&&p.level()==world&&p.gameMode.getGameModeForPlayer()==mode,"Opening a view changed the real player");
            p.move(MoverType.SELF,new Vec3(8,1,3));
            check(h,p.position().equals(body),"Body moved during camera view");
            float health=p.getHealth(); p.hurtServer(world,p.damageSources().generic(),8);
            check(h,p.getHealth()==health,"Body took damage during camera view");
            int slot=p.getInventory().getSelectedSlot();
            p.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket((slot+1)%9));
            check(h,p.getInventory().getSelectedSlot()==slot,"Inventory input escaped camera guard");
            view.move(server.getLevel(Level.END),new Vec3(600,90,600),-179,-20,true);
            check(h,p.position().equals(body)&&p.level()==world&&p.gameMode.getGameModeForPlayer()==mode,"Dimension cut moved the real body");
            CameraEffects.setFov(p,40);
            packets(p);
            view.close(); view.close();
            check(h,packets(p).stream().anyMatch(x -> x instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket add && add.getId()==pig.getId()),"Body-world entities were not resent after client-only respawn");
            check(h,CameraViews.get(p)==null&&CameraViews.ticketCount()==0,"View cleanup leaked tickets");
            check(h,Objects.equals(75f,CameraEffects.getFov(p)),"Previous FOV was not restored");
            check(h,p.position().equals(body)&&p.level()==world&&p.gameMode.getGameModeForPlayer()==mode,"Restore overwrote server state");
            p.hurtServer(world,p.damageSources().generic(),8);
            check(h,p.getHealth()<health,"Damage remained blocked after ending view");
            h.succeed();
        } finally { pig.discard(); remove(p); }
    }
    @GameTest(structure="minigamelib-test:empty", maxTicks=100)
    public void replacementSharedTicketsAndExternalModeChange(GameTestHelper h) {
        var p=player(h); var other=player(h); var server=h.getLevel().getServer();
        try {
            var position=new Vec3(1800,100,1800); var remote=server.getLevel(Level.END);
            var a=CameraViews.open(p,remote,position,0,0); var b=CameraViews.open(other,remote,position,0,0);
            check(h,CameraViews.ticketCount()==1,"Shared region should use one ticket");
            var replacement=CameraViews.open(p,remote,position,90,30);
            check(h,a.closed()&&!b.closed()&&!replacement.closed()&&CameraViews.ticketCount()==1,"Replacement leaked or removed a shared ticket");
            p.setGameMode(GameType.CREATIVE);
            check(h,replacement.closed()&&p.gameMode.getGameModeForPlayer()==GameType.CREATIVE,"External game mode update did not cancel before changing");
            check(h,CameraViews.ticketCount()==1,"Another viewer's ticket was removed");
            b.close(); check(h,CameraViews.ticketCount()==0,"Last viewer did not release ticket");
            var view=CameraViews.open(p,remote,position,0,0);
            p.teleportTo(10,100,10);
            check(h,view.closed()&&p.position().equals(new Vec3(10,100,10)),"External teleport was overwritten by restore");
            var direct=CameraViews.open(p,remote,position,0,0);
            p.connection.teleport(20,100,20,0,0);
            check(h,direct.closed()&&p.position().equals(new Vec3(20,100,20)),"Direct connection teleport did not cancel view");
            h.succeed();
        } finally { for(var player:List.of(p,other)) remove(player); }
    }
    @GameTest(structure="minigamelib-test:empty", maxTicks=100)
    public void validatesAllViewersBeforeReplacingAndCompletesCancelledHandle(GameTestHelper h) {
        var p=player(h);var other=player(h);var server=h.getLevel().getServer();
        var service=MinigameLib.get(server).cinematics();
        try {
            var camera=new CameraTrack(1,"test","test",h.getLevel().dimension().identifier().toString(),List.of(new Keyframe(0,0,100,0,0,0)));
            var scene=new ResolvedScene(new Scene(1,"test","test",20,List.of(new Clip("test",0)),List.of(),List.of()),Map.of("test",camera),Map.of());
            var first=service.play(scene,List.of(p));
            other.setHealth(0);
            boolean rejected=false;
            try { service.play(scene,List.of(p,other)); } catch(IllegalArgumentException expected) { rejected=true; }
            check(h,rejected&&!first.done()&&CameraViews.get(p)!=null,"Invalid group replaced a valid existing session");
            service.stop(p);
            check(h,first.completion().toCompletableFuture().join().status()==Playback.Status.CANCELLED,"Cancelled playback did not complete");
            check(h,CameraViews.ticketCount()==0,"Playback cleanup leaked a region");
            h.succeed();
        } finally { for(var player:List.of(p,other)) remove(player); }
    }

    @GameTest(structure="minigamelib-test:empty",maxTicks=100)
    public void editorUsesPrivatePanelsAndOwnerBoundDialogResponses(GameTestHelper h) throws Exception {
        var p=player(h);var other=player(h);var server=h.getLevel().getServer();var editor=MinigameLib.get(server).editor();
        try {
            server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(new net.minecraft.server.players.NameAndId(p.getGameProfile()),net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER,false));
            server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(new net.minecraft.server.players.NameAndId(other.getGameProfile()),net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER,false));
            packets(p);packets(other);editor.open(p);
            var output=packets(p);
            check(h,output.stream().anyMatch(x->x instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket),"Editor sent no panel entities");
            check(h,packets(other).stream().noneMatch(x->x instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket),"Personal panel leaked to another admin");
            var panel=(eu.pb4.polymer.virtualentity.api.ElementHolder)QAControls.field(QAControls.session(p),"panel");
            var hit=(eu.pb4.polymer.virtualentity.api.elements.InteractionElement)null;String label="";
            for(var element:panel.getElements()) {
                if(element instanceof eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement text)label=text.getText().getString();
                if(element instanceof eu.pb4.polymer.virtualentity.api.elements.InteractionElement button&&label.equals("+ NEW")){hit=button;break;}
            }
            check(h,hit!=null,"Camera browser has no create button");
            var target=new net.minecraft.world.entity.Interaction(net.minecraft.world.entity.EntityTypes.INTERACTION,p.level());target.setId(hit.getEntityId());
            var click=new net.minecraft.network.protocol.game.ServerboundInteractPacket(target.getId(),net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.phys.Vec3.ZERO,false);
            p.connection.handleInteract(click);
            check(h,QAControls.field(QAControls.session(p),"dialogId")==null,"Off-target click bypassed the panel aim check");
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,hit.getCurrentPos().add(0,hit.getHeight()/2,0));
            p.connection.handleInteract(click);
            var session=QAControls.session(p);var action=(net.minecraft.resources.Identifier)QAControls.field(session,"dialogId");
            check(h,action!=null,"Native panel click did not open a dialog");
            var payload=new net.minecraft.nbt.CompoundTag();payload.putString("name","테스트 카메라");payload.putString("dimension",h.getLevel().dimension().identifier().toString());
            editor.response(other,action,payload);
            check(h,Objects.equals(action,QAControls.field(session,"dialogId")),"Another admin consumed the owner's dialog");
            p.connection.handleCustomClickAction(new net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket(action,Optional.of(payload)));
            check(h,QAControls.field(session,"dialogId")==null,"Dialog response did not consume its token");
            String selected=(String)QAControls.field(session,"selected");
            var draft=(CinematicCatalog.Draft)((Map<?,?>)QAControls.field(session,"drafts")).get(selected);
            check(h,((CameraTrack)draft.value()).name().equals("테스트 카메라"),"Dialog response did not update the draft");
            var saved=MinigameLib.get(server).cinematics().catalog().save(draft,draft.value());
            editor.close(p);editor.open(p);
            check(h,MinigameLib.get(server).cinematics().catalog().read(Kind.CAMERA,saved.id()).value().equals(saved.value()),"Saved definition did not survive reopening");
            h.succeed();
        } finally {remove(p);remove(other);}
    }

    @GameTest(structure="minigamelib-test:empty",maxTicks=100)
    public void failedPackBuildPreservesCatalogAndLateMusicJoinsAtNextSong(GameTestHelper h) throws Exception {
        var p=player(h);var other=player(h);var server=h.getLevel().getServer();var music=MinigameLib.get(server).music();
        var file=MinigameLib.CONFIG.resolve("music/qa.ogg");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            try(var input=getClass().getResourceAsStream("/music/tone4.ogg")){java.nio.file.Files.copy(input,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            var output=MinigameLib.CONFIG.resolve("test-pack.zip");
            check(h,buildMain(output),"Music pack build failed");
            var published=kim.biryeong.minigamelib.music.MusicPacks.published();
            check(h,published.catalog().tracks().size()==1,"Successful build did not publish music");
            check(h,!buildMain(file.getParent()),"Writing ZIP over a directory must fail");
            check(h,published==kim.biryeong.minigamelib.music.MusicPacks.published(),"Failed build replaced active catalog");
            var id=eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.getMainUuid();
            var push = new net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket(id,"http://localhost/test",published.hash(),false,Optional.empty());
            var config = new net.minecraft.server.network.ServerConfigurationPacketListenerImpl(server,
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                    net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false));
            var configured = kim.biryeong.minigamelib.music.MusicPacks.sent(config,push);
            kim.biryeong.minigamelib.music.MusicPacks.response(config,new net.minecraft.network.protocol.common.ServerboundResourcePackPacket(configured.id(),net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            check(h,kim.biryeong.minigamelib.music.MusicPacks.ready(p),"Configuration-phase load was not retained when the player joined");
            p.connection.send(push);
            var old = packets(p).stream().filter(x -> x instanceof net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket).map(x -> (net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket)x).findFirst().orElseThrow();
            p.connection.send(push);other.connection.send(push);
            var sent = packets(p).stream().filter(x -> x instanceof net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket).map(x -> (net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket)x).findFirst().orElseThrow();
            var otherSent = packets(other).stream().filter(x -> x instanceof net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket).map(x -> (net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket)x).findFirst().orElseThrow();
            check(h,!old.id().equals(sent.id()),"Pack re-send reused its wire UUID");
            p.connection.handleResourcePackResponse(new net.minecraft.network.protocol.common.ServerboundResourcePackPacket(old.id(),net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            check(h,!kim.biryeong.minigamelib.music.MusicPacks.ready(p),"Old load response approved a newer pack send");
            p.connection.handleResourcePackResponse(new net.minecraft.network.protocol.common.ServerboundResourcePackPacket(sent.id(),net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            check(h,eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.hasMainPack(p),"Wire UUID mapping broke Polymer main-pack tracking");
            packets(p);packets(other);var handle=music.playGlobal("all");
            check(h,sounds(packets(p))==1&&sounds(packets(other))==0,"Music played before pack load completion");
            other.connection.handleResourcePackResponse(new net.minecraft.network.protocol.common.ServerboundResourcePackPacket(otherSent.id(),net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            check(h,sounds(packets(other))==0,"Late listener joined mid-song");music.nextGlobal();
            check(h,sounds(packets(p))==1&&sounds(packets(other))==1,"Ready listener did not join next song");
            music.reserve(p,true);music.play("all",List.of(p),true);packets(p);music.nextGlobal();
            check(h,sounds(packets(p))==0&&sounds(packets(other))==1,"Global BGM overrode cinematic music");
            music.reserve(p,false);music.nextGlobal();check(h,sounds(packets(p))==1,"Cinematic listener did not return on the next BGM song");
            handle.stop();check(h,handle.done(),"Music stop did not complete its handle");
            var privateMusic=music.play("all",List.of(p));music.left(p);
            check(h,privateMusic.done(),"Disconnected listener left a repeating private channel running");h.succeed();
        } finally {music.stopGlobal();music.stop(List.of(p,other));remove(p);remove(other);java.nio.file.Files.deleteIfExists(file);}
    }
    @GameTest(structure="minigamelib-test:empty",maxTicks=100)
    public void pausedPreviewRebuildsActorsAndActiveEffectWithoutMusic(GameTestHelper h) throws Exception {
        var p=player(h);var service=MinigameLib.get(p.level().getServer()).cinematics();
        try {
            var pos=p.position();var key=new Keyframe(0,pos.x,pos.y,pos.z,0,0);
            String dimension=h.getLevel().dimension().identifier().toString();
            var camera=new CameraTrack(1,"preview","preview",dimension,List.of(key));
            var actor=new EntityTrack(1,"actor","actor",dimension,"minecraft:pig",null,null,null,List.of(key));
            var scene=new Scene(1,"preview","preview",40,List.of(new Clip("preview",0)),List.of(new Clip("actor",0)),
                    List.of(new Cue(0,CueType.TRANSITION,"minigamelib:transition-hbars",20,0x123456)));
            var run=service.preview(new ResolvedScene(scene,Map.of("preview",camera),Map.of("actor",actor)),p,10,true);
            // This test targets rendering after preparation; chunk/ACK handling is exercised with the real client.
            var ready=CameraViewSession.class.getDeclaredField("ready");ready.setAccessible(true);ready.setBoolean(CameraViews.get(p),true);
            packets(p);service.tick();service.tick();var output=packets(p);
            check(h,output.stream().anyMatch(x->x instanceof net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket data && data.packedItems().stream().anyMatch(v -> v.value() instanceof Component text && !text.getString().isEmpty())),"Scrubbing lost an effect started before the cursor");
            int effectId=((eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement)QAControls.field(CameraViews.get(p),"effect")).getEntityId();
            check(h,output.stream().anyMatch(x -> x instanceof net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket data
                    && data.id()==effectId && data.packedItems().stream().anyMatch(v -> v.value().equals((byte)128))),"Paused transition did not preserve 50 percent native text opacity");
            var pig=output.stream().filter(x->x instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket add && add.getType()==net.minecraft.world.entity.EntityTypes.PIG)
                    .map(x->(net.minecraft.network.protocol.game.ClientboundAddEntityPacket)x).findFirst().orElseThrow();
            check(h,h.getLevel().getEntity(pig.getId())==null,"Preview actor was added to the real world");
            check(h,sounds(output)==0&&!run.done(),"Paused preview played music or advanced to completion");
            run.stop();check(h,CameraViews.ticketCount()==0,"Preview cleanup leaked tickets");h.succeed();
        } finally { remove(p); }
    }
    private static long sounds(List<net.minecraft.network.protocol.Packet<?>> packets){return packets.stream().filter(p->p instanceof net.minecraft.network.protocol.game.ClientboundSoundEntityPacket).count();}
}
