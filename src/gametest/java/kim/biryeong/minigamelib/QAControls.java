package kim.biryeong.minigamelib;

import net.minecraft.commands.Commands;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import java.util.*;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;

/** Test mod only: the server harness drives the same packet handlers as a client click. */
public final class QAControls implements ModInitializer {
    static Object field(Object owner,String name) throws ReflectiveOperationException {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    static Object session(ServerPlayer player) throws ReflectiveOperationException {
        var editor=MinigameLib.get(player.level().getServer()).editor();
        return ((Map<?,?>)field(editor,"sessions")).get(player.getUUID());
    }
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->dispatcher.register(literal("minigameqa").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("status").executes(c->run(c.getSource().getPlayerOrException(),p->{
                    var view=kim.biryeong.minigamelib.cinematic.CameraViews.get(p);
                    p.level().getServer().sendSystemMessage(Component.literal("packReady="+kim.biryeong.minigamelib.music.MusicPacks.ready(p)
                            +" polymerMain="+eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.hasMainPack(p)
                            +" view="+(view==null?"none":view.level().dimension().identifier()+" ready="+view.ready())
                            +" tickets="+kim.biryeong.minigamelib.cinematic.CameraViews.ticketCount()));
                })))
                .then(literal("panel").executes(c->run(c.getSource().getPlayerOrException(),p->{
                    var panel=(ElementHolder)field(session(p),"panel");String label="";
                    for(var element:panel.getElements()) {
                        if(element instanceof TextDisplayElement text)label=text.getText().getString();
                        if(element instanceof InteractionElement hit) p.level().getServer().sendSystemMessage(Component.literal(hit.getEntityId()+" | "+label));
                    }
                })))
                .then(literal("click").then(argument("entity",integer()).executes(c->run(c.getSource().getPlayerOrException(),p->{
                    var panel=(ElementHolder)field(session(p),"panel");
                    var hit=panel.getElements().stream().filter(e->e instanceof InteractionElement h&&h.getEntityId()==getInteger(c,"entity"))
                            .map(e->(InteractionElement)e).findFirst().orElseThrow();
                    p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,hit.getCurrentPos().add(0,hit.getHeight()/2,0));
                    var entity=new Interaction(net.minecraft.world.entity.EntityTypes.INTERACTION,p.level());entity.setId(hit.getEntityId());
                    p.connection.handleInteract(new net.minecraft.network.protocol.game.ServerboundInteractPacket(entity.getId(),net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.phys.Vec3.ZERO,false));
                }))))
                .then(literal("dialog").then(argument("payload",greedyString()).executes(c->run(c.getSource().getPlayerOrException(),p->{
                    var id=(Identifier)field(session(p),"dialogId");
                    if(id==null)throw new IllegalStateException("No open editor dialog");
                    var tag=net.minecraft.nbt.TagParser.parseCompoundFully(getString(c,"payload"));
                    p.connection.handleCustomClickAction(new ServerboundCustomClickActionPacket(id,Optional.of(tag)));
                }))))));
    }
    interface Action {void run(ServerPlayer player)throws Exception;}
    static int run(ServerPlayer player,Action action)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try{action.run(player);return 1;}catch(Exception e){throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal(e.toString())).create();}
    }
}
