package kim.biryeong.minigamelib;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.commands.arguments.EntityArgument.*;

public final class LibraryCommands {
    private LibraryCommands() {}
    @FunctionalInterface private interface Action { int run() throws Exception; }
    private static int checked(Action action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try { return action.run(); }
        catch (Exception e) { throw new SimpleCommandExceptionType(Component.literal(Objects.toString(e.getMessage(),e.getClass().getSimpleName()))).create(); }
    }
    private static MinigameLib lib(CommandContext<CommandSourceStack> c) { return MinigameLib.get(c.getSource().getServer()); }
    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> c, boolean specified) throws Exception {
        return specified ? getPlayers(c,"players") : List.of(c.getSource().getPlayerOrException());
    }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->dispatcher.register(literal("minigamelib").requires(s->s.hasPermission(2))
                .then(literal("cinematic")
                        .then(literal("editor").executes(c->checked(()->{lib(c).editor().open(c.getSource().getPlayerOrException());return 1;})))
                        .then(literal("list").executes(c->checked(()->{for(var kind:kim.biryeong.minigamelib.cinematic.CinematicData.Kind.values()) c.getSource().sendSuccess(()->Component.literal(kind+": "+list(c,kind)),false);return 1;})))
                        .then(literal("play").then(argument("scene",word()).suggests((c,b)->{try {lib(c).cinematics().catalog().list(kim.biryeong.minigamelib.cinematic.CinematicData.Kind.SCENE).forEach(b::suggest);}catch(Exception ignored){}return b.buildFuture();})
                                .executes(c->cinematic(c,false)).then(argument("players",players()).executes(c->cinematic(c,true)))))
                        .then(literal("stop").executes(c->stopCinematic(c,false)).then(argument("players",players()).executes(c->stopCinematic(c,true)))))
                .then(literal("music")
                        .then(literal("list").executes(c->{var catalog=lib(c).music().catalog();c.getSource().sendSuccess(()->Component.literal("Playlists: "+catalog.playlists().keySet()),false);catalog.tracks().values().forEach(t->c.getSource().sendSuccess(()->Component.literal(t.id()+" | "+t.path()+" | "+String.format(Locale.ROOT,"%.2fs",t.seconds())),false));return catalog.tracks().size();}))
                        .then(literal("play").then(argument("music",word()).suggests((c,b)->{lib(c).music().catalog().playlists().keySet().forEach(b::suggest);lib(c).music().catalog().tracks().keySet().forEach(b::suggest);return b.buildFuture();})
                                .executes(c->music(c,false)).then(argument("players",players()).executes(c->music(c,true)))))
                        .then(literal("stop").executes(c->stopMusic(c,false)).then(argument("players",players()).executes(c->stopMusic(c,true))))
                        .then(literal("next").executes(c->nextMusic(c,false)).then(argument("players",players()).executes(c->nextMusic(c,true))))
                        .then(literal("global")
                                .then(literal("play").then(argument("music",word()).executes(c->checked(()->{lib(c).music().playGlobal(getString(c,"music"));return 1;}))))
                                .then(literal("stop").executes(c->{lib(c).music().stopGlobal();return 1;}))
                                .then(literal("next").executes(c->{lib(c).music().nextGlobal();return 1;}))))));
    }
    private static String list(CommandContext<CommandSourceStack> c,kim.biryeong.minigamelib.cinematic.CinematicData.Kind kind) { try{return lib(c).cinematics().catalog().list(kind).toString();}catch(Exception e){return e.getMessage();} }
    private static int cinematic(CommandContext<CommandSourceStack> c,boolean specified) throws com.mojang.brigadier.exceptions.CommandSyntaxException {return checked(()->{lib(c).cinematics().play(getString(c,"scene"),targets(c,specified));return 1;});}
    private static int stopCinematic(CommandContext<CommandSourceStack> c,boolean specified) throws com.mojang.brigadier.exceptions.CommandSyntaxException {return checked(()->{targets(c,specified).forEach(lib(c).cinematics()::stop);return 1;});}
    private static int music(CommandContext<CommandSourceStack> c,boolean specified) throws com.mojang.brigadier.exceptions.CommandSyntaxException {return checked(()->{lib(c).music().play(getString(c,"music"),targets(c,specified));return 1;});}
    private static int stopMusic(CommandContext<CommandSourceStack> c,boolean specified) throws com.mojang.brigadier.exceptions.CommandSyntaxException {return checked(()->{lib(c).music().stop(targets(c,specified));return 1;});}
    private static int nextMusic(CommandContext<CommandSourceStack> c,boolean specified) throws com.mojang.brigadier.exceptions.CommandSyntaxException {return checked(()->{lib(c).music().next(targets(c,specified));return 1;});}
}
