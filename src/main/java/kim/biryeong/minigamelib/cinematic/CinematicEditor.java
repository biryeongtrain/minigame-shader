package kim.biryeong.minigamelib.cinematic;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.*;
import kim.biryeong.minigamelib.Playback;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import static kim.biryeong.minigamelib.cinematic.CinematicData.*;

/** Personal world-space controls. Every callback is bound to its owner and editor generation. */
public final class CinematicEditor implements AutoCloseable {
    private final MinecraftServer server;
    private final CinematicService service;
    private final Map<UUID, Session> sessions = new HashMap<>();
    public CinematicEditor(MinecraftServer server, CinematicService service) { this.server = server; this.service = service; }
    public void open(ServerPlayer player) {
        if (!player.hasPermissions(2)) throw new IllegalArgumentException("Permission level 2 is required");
        close(player); var session = new Session(player); sessions.put(player.getUUID(), session); session.refresh();
    }
    public void close(ServerPlayer player) { var session = sessions.remove(player.getUUID()); if (session != null) session.close(); }
    public void tick() {
        for (var session : List.copyOf(sessions.values())) {
            if (session.player.hasDisconnected() || !session.player.hasPermissions(2) || session.player.level() != session.world) close(session.player);
            else {
                if (session.preview != null && session.preview.done()) { session.preview = null; session.refresh(); }
                if (CameraViews.get(session.player) != null) {
                    if (!session.viewing) CameraViews.sending(() -> session.panel.stopWatching(session.player));
                    session.viewing = true;
                } else {
                    if (session.viewing) { session.viewing = false; session.refresh(); }
                    session.updateHover(); session.panel.tick();
                }
            }
        }
    }
    public boolean response(ServerPlayer player, ResourceLocation action, CompoundTag payload) {
        if (!action.getNamespace().equals("minigamelib") || !action.getPath().startsWith("editor/")) return false;
        var session = sessions.get(player.getUUID());
        if (session != null && session.valid(player) && action.equals(session.dialogId)) {
            var callback = session.dialog; session.dialog = null; session.dialogId = null;
            if (callback != null) session.tryAction(() -> callback.accept(payload));
        }
        return true;
    }
    @Override public void close() { for (var p : List.copyOf(sessions.values())) close(p.player); }
    @FunctionalInterface private interface Edit { void run() throws Exception; }

    private final class Session {
        final ServerPlayer player;
        final net.minecraft.server.level.ServerLevel world;
        final Vec3 anchor, right, forward;
        final float panelYaw;
        final Set<String> shown = new LinkedHashSet<>();
        final List<Hover> buttons = new ArrayList<>();
        Kind browsing = Kind.CAMERA;
        int hovered = -1;
        static final int PANEL = 0xff100805, ORANGE = 0xffe58225, TAN = 0xffe5bd87;
        static final int[] TRACK_COLORS = {0xff596ade,0xffba58d8,0xffdad83a,0xff63a52b,0xffd61689,0xff559ddd};
        record Hover(double x,double y,double w,double h,int background,int foreground,TextDisplayElement surface,TextDisplayElement label) {}
        final Map<String, CinematicCatalog.Draft> drafts = new LinkedHashMap<>();
        ElementHolder panel = new ElementHolder();
        String selected;
        int cursor, page, trackPage, generation, lastClick = -1;
        boolean closed, viewing;
        Playback preview;
        ResourceLocation dialogId;
        Consumer<CompoundTag> dialog;
        Session(ServerPlayer player) {
            this.player = player; world = player.level();
            double yaw = Math.toRadians(player.getYRot());
            right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            forward = new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
            panelYaw = player.getYRot()+180;
            anchor = player.getEyePosition().add(forward.scale(3.4)).add(0,1.15,0);
        }
        boolean valid(ServerPlayer p) { return !closed && p == player && !p.hasDisconnected() && p.hasPermissions(2) && sessions.get(p.getUUID()) == this; }
        void tryAction(Edit action) {
            if (!valid(player)) return;
            try { action.run(); refresh(); }
            catch (Exception e) { player.sendSystemMessage(Component.literal("편집 실패: " + e.getMessage())); }
        }
        CinematicCatalog.Draft current() { if (selected == null) throw new IllegalArgumentException("항목을 먼저 선택하세요."); return drafts.get(selected); }
        void select(Kind kind, String id) throws IOException {
            String key = kind + "/" + id;
            if (!drafts.containsKey(key)) drafts.put(key, service.catalog().read(kind, id));
            selected = key; browsing = kind;
            cursor = 0; trackPage = 0;
        }
        void update(Object value) {
            var draft = current(); drafts.put(selected, new CinematicCatalog.Draft(draft.kind(), draft.id(), value, draft.revision()));
        }
        void refresh() {
            if (closed || CameraViews.get(player) != null) return;
            panel.destroy(); panel = new ElementHolder(); generation++;
            buttons.clear(); hovered = -1;
            new ManualAttachment(panel, world, () -> Vec3.ZERO);
            try {
                navigation(); browser(); timeline(); drawPaths();
                updateHover(); panel.tick(); panel.startWatching(player);
            } catch (Exception e) { player.sendSystemMessage(Component.literal("패널: " + e.getMessage())); }
        }
        void navigation() {
            board(-3.82,-1.71,1.47,2.06);
            label(-3.74,.075,1.31,.23,"CINEMATICS",ORANGE,.75f,true);
            rect(-3.74,.025,1.31,.012,ORANGE,.005);
            int index=0;
            for (var kind:Kind.values()) {
                double y=-.36-index++*.35;
                button(-3.74,y,1.31,.30,kind==Kind.CAMERA?"Camera":kind==Kind.ENTITY?"Entity":"Scenes",
                        browsing==kind?TAN:PANEL,browsing==kind?ORANGE:0xffffffff,1.02f,true,()->{browsing=kind;selected=null;page=0;cursor=0;});
            }
            button(-3.74,-1.45,1.31,.30,"Exit",PANEL,0xffffffff,1.02f,true,()->CinematicEditor.this.close(player));
        }
        void browser() throws IOException {
            board(-2.15,-2.98,4.45,3.33);
            var ids = new TreeSet<>(service.catalog().list(browsing));
            drafts.values().stream().filter(d->d.kind()==browsing).forEach(d->ids.add(d.id()));
            int pages=Math.max(1,(ids.size()+7)/8);page=Math.min(page,pages-1);
            int rowIndex=0;
            for (String id:ids.stream().skip(page*8L).limit(8).toList()) {
                Kind kind=browsing;String key=kind+"/"+id;var draft=drafts.get(key);
                String title;
                try {title=name(draft==null?service.catalog().read(kind,id).value():draft.value());}
                catch(IOException e){title="! "+id;}
                double y=.005-rowIndex*.29;
                int stripe=key.equals(selected)?0xff77624b:(rowIndex%2==0?0xff585858:0xff242424);rowIndex++;
                button(-2.03,y,2.09,.235,title,stripe,0xffffffff,.93f,false,()->select(kind,id));
                button(.06,y,.62,.235,"PLAY",0xff368600,0xffffffff,.86f,true,()->{select(kind,id);preview(false);});
                button(.68,y,.62,.235,shown.contains(key)?"HIDE":"SHOW",shown.contains(key)?0xffae710b:0xff55442e,0xffffffff,.83f,true,()->{
                    if(!shown.remove(key)){if(!drafts.containsKey(key))drafts.put(key,service.catalog().read(kind,id));shown.add(key);}
                });
                button(1.30,y,.87,.235,"DELETE",0xff970a08,0xffffffff,.83f,true,()->{select(kind,id);delete();});
            }
            if(ids.isEmpty()) label(-2.02,-.4,4.1,.3,"아직 항목이 없습니다. + NEW로 만드세요.",0xffad9c89,.57f,true);
            button(-2.03,-2.49,1.05,.24,"+ NEW",0xff6f431c,0xffffd399,.68f,true,()->create(browsing));
            button(-.55,-2.49,.35,.24,"<",0xff39312a,0xffffffff,.75f,true,()->page=Math.max(0,page-1));
            label(-.13,-2.49,.9,.24,(page+1)+" / "+pages,0xffbbaa97,.63f,true);
            button(.83,-2.49,.35,.24,">",0xff39312a,0xffffffff,.75f,true,()->page=Math.min(pages-1,page+1));
            label(1.30,-2.49,.87,.24,ids.size()+" items",0xffac9a84,.5f,true);
            if(selected!=null) {
                button(-2.03,-2.83,1.34,.24,"PROPERTIES",0xff514132,0xffffffff,.60f,true,this::properties);
                button(-.61,-2.83,1.34,.24,"COPY",0xff514132,0xffffffff,.64f,true,this::duplicate);
                button(.82,-2.83,1.35,.24,"SAVE",0xff395929,0xffffffff,.64f,true,()->drafts.put(selected,service.catalog().save(current(),current().value())));
            }
        }
        Vec3 point(double x,double y,double depth) { return anchor.add(right.scale(x)).add(0,y,0).subtract(forward.scale(depth)); }
        TextDisplayElement textDisplay(Component text,Vec3 pos,Vector3f scale) {
            var display=new TextDisplayElement(text);display.setOverridePos(pos);display.setScale(scale);
            display.setYaw(panelYaw);display.setBillboardMode(Display.BillboardConstraints.FIXED);
            display.setBackground(0);display.setLineWidth(8192);display.setBrightness(new net.minecraft.util.Brightness(15,15));
            panel.addElement(display);return display;
        }
        TextDisplayElement rect(double x,double y,double w,double h,int color,double depth) {
            // A native space glyph has a 5 x 10 pixel background; its origin is at 40% of the width.
            var display=textDisplay(Component.literal(" "),point(x+w*.4,y,depth),new Vector3f((float)(w/.125),(float)(h/.25),1));
            display.setBackground(color);return display;
        }
        void board(double x,double y,double w,double h) {
            rect(x+.025,y,w-.05,h,PANEL,0);rect(x,y+.025,w,h-.05,PANEL,0);
        }
        TextDisplayElement label(double x,double y,double w,double h,String value,int color,float scale,boolean center) {
            String clipped=shortLabel(value,(int)(w/(scale*.025))-3);
            double pixels=fontWidth(clipped), left=center?x+(w-pixels*scale*.025)/2:x+.025;
            return textDisplay(Component.literal(clipped).withColor(color&0xffffff),point(left+(pixels/2-1)*scale*.025,y+(h-.225*scale)/2,.027),new Vector3f(scale));
        }
        void button(double x,double y,double w,double h,String value,int background,int foreground,float scale,boolean center,Edit action) {
            var surface=rect(x,y,w,h,background,.01);var text=label(x,y,w,h,value,foreground,scale,center);
            buttons.add(new Hover(x,y,w,h,background,foreground,surface,text));hit(x,y,w,h,action);
        }
        void hit(double x,double y,double w,double h,Edit action) {
            int revision=generation;
            var handler=new VirtualElement.InteractionHandler() {
                void click(ServerPlayer p) {
                    if(!valid(p)||revision!=generation||CameraViews.get(p)!=null||lastClick==server.getTickCount())return;
                    var aim=aim();if(aim==null||aim.x<x-.03||aim.x>x+w+.03||aim.y<y-.03||aim.y>y+h+.03)return;
                    lastClick=server.getTickCount();tryAction(action);
                }
                @Override public void interact(ServerPlayer p,InteractionHand hand){if(hand==InteractionHand.MAIN_HAND)click(p);}
                @Override public void interactAt(ServerPlayer p,InteractionHand hand,Vec3 pos){if(hand==InteractionHand.MAIN_HAND)click(p);}
                @Override public void attack(ServerPlayer p){click(p);}
            };
            // Interaction entities have square footprints; tile wide controls to keep their hitboxes shallow.
            int pieces=Math.max(1,(int)Math.ceil(w/.3));double width=w/pieces;
            for(int i=0;i<pieces;i++) {
                var hit=new InteractionElement();hit.setOverridePos(point(x+(i+.5)*width,y,.035));hit.setSize((float)width,(float)h);hit.setResponse(true);hit.setInteractionHandler(handler);panel.addElement(hit);
            }
        }
        Vec3 aim() {
            var eye=player.getEyePosition();var direction=player.getLookAngle();double dot=direction.dot(forward);
            if(dot<=.0001)return null;double distance=anchor.subtract(eye).dot(forward)/dot;
            if(distance<0||distance>6)return null;var relative=eye.add(direction.scale(distance)).subtract(anchor);
            return new Vec3(relative.dot(right),relative.y,0);
        }
        void updateHover() {
            var aim=aim();int next=-1;
            if(aim!=null)for(int i=0;i<buttons.size();i++){var b=buttons.get(i);if(aim.x>=b.x&&aim.x<=b.x+b.w&&aim.y>=b.y&&aim.y<=b.y+b.h){next=i;break;}}
            if(hovered==next)return;
            if(hovered>=0){var b=buttons.get(hovered);b.surface.setBackground(b.background);b.label.setText(b.label.getText().copy().withColor(b.foreground&0xffffff));}
            hovered=next;
            if(next>=0){var b=buttons.get(next);b.surface.setBackground(b.background==PANEL?TAN:brighter(b.background));b.label.setText(b.label.getText().copy().withColor(b.background==PANEL?ORANGE&0xffffff:b.foreground&0xffffff));}
        }
        void timeline() {
            double x=2.67;board(x,-2.98,4.65,3.33);
            if(selected==null) {
                label(x+.14,.06,4.35,.22,"TIMELINE",ORANGE,.7f,false);
                label(x+.14,-1.13,4.35,.25,"목록에서 이름을 선택하세요.",0xffaa9986,.68f,true);
                label(x+.14,-1.48,4.35,.24,"PLAY  /  SHOW  /  DELETE",0xff796b5b,.55f,true);return;
            }
            Object value=current().value();int duration=value instanceof Scene s?s.duration():Math.max(100,frames(value).getLast().tick()+1);
            label(x+.12,.08,value instanceof Scene scene && scene.cameras().size()+scene.entities().size()+scene.cues().size()>8?2.0:3.24,.23,name(value),ORANGE,.73f,false);
            button(x+3.54,.07,.98,.24,"PLAY",0xff368600,0xffffffff,.67f,true,()->preview(false));
            button(x+.12,-.28,.75,.24,formatTime(cursor),0xff39312a,0xffe7c195,.69f,true,()->show("시간 이동",Map.of("tick",""+cursor),tag->{
                int time=Integer.parseInt(required(tag,"tick"));if(time<0||time>=duration)throw new IllegalArgumentException("시간 범위 오류");cursor=time;
            }));
            button(x+.9,-.28,.47,.24,"VIEW",0xff514132,0xffffffff,.43f,true,()->preview(true));
            double from=x+1.48,to=x+4.48,span=to-from;
            for(int i=0;i<=4;i++) {
                int time=Math.min(duration-1,duration*i/4);double at=from+span*i/4;
                label(at-.22,-.29,.44,.2,formatTime(time),0xffac9c8a,.44f,true);
                rect(at,-2.64,.009,2.28,0xff31251c,.004);
            }
            // The ruler remains available while editing; PLAY previews from the selected cursor.
            hit(from,-.52,span,.17,()->{var at=aim();cursor=Math.max(0,Math.min(duration-1,(int)((at.x-from)/span*duration)));});
            for(int i=0;i<=20;i++)rect(from+span*i/20,-.48,.01,i%5==0?.09:.045,0xff907758,.01);
            for(int i=0;i<8;i++)rect(x+.12,-.82-i*.255,1.25,.23,i%2==0?0xff585858:0xff242424,.005);
            if(value instanceof Scene scene) {
                int count=scene.cameras().size()+scene.entities().size()+scene.cues().size(),pages=Math.max(1,(count+7)/8);
                trackPage=Math.min(trackPage,pages-1);int first=trackPage*8,row=0;
                if(pages>1) {
                    button(x+2.2,.07,.28,.24,"<",0xff39312a,0xffffffff,.65f,true,()->trackPage=Math.max(0,trackPage-1));
                    label(x+2.5,.07,.65,.24,(trackPage+1)+"/"+pages,0xffbbaa97,.53f,true);
                    button(x+3.19,.07,.28,.24,">",0xff39312a,0xffffffff,.65f,true,()->trackPage=Math.min(pages-1,trackPage+1));
                }
                var cameraNames=trackOptions(Kind.CAMERA);var entityNames=trackOptions(Kind.ENTITY);
                for(int i=0;i<scene.cameras().size();i++,row++) {
                    if(row<first||row>=first+8)continue;
                    var clip=scene.cameras().get(i);int index=i;
                    int end=i+1<scene.cameras().size()?scene.cameras().get(i+1).start():duration;
                    track(row,cameraNames.getOrDefault(clip.track(),clip.track()),clip.start(),end,duration,true,()->clipDialog(false,index));
                }
                for(int i=0;i<scene.entities().size();i++,row++) {
                    if(row<first||row>=first+8)continue;
                    var clip=scene.entities().get(i);int index=i;
                    track(row,entityNames.getOrDefault(clip.track(),clip.track()),clip.start(),duration,duration,true,()->clipDialog(true,index));
                }
                for(var cue:scene.cues()) {
                    if(row>=first&&row<first+8)track(row,cue.type().name(),cue.tick(),Math.min(duration,cue.tick()+Math.max(1,cue.duration())),duration,true,()->{cursor=cue.tick();cues();});
                    row++;
                }
                button(x+.12,-2.83,1.36,.24,"+ CAMERA",0xff514132,0xffffffff,.56f,true,()->clipDialog(false,-1));
                button(x+1.56,-2.83,1.36,.24,"+ ENTITY",0xff514132,0xffffffff,.56f,true,()->clipDialog(true,-1));
                button(x+3,-2.83,1.51,.24,"+ CUE",0xff514132,0xffffffff,.56f,true,this::cues);
            } else {
                var names=value instanceof CameraTrack?List.of("Position","Rotation","FOV"):List.of("Position","Rotation","Pose","Equipment");
                for(int i=0;i<names.size();i++) {
                    track(i,names.get(i),0,Math.max(1,frames(value).getLast().tick()),duration,false,()->keyframe(cursor));
                    for(var frame:frames(value)) {
                        double at=from+span*frame.tick()/duration;
                        label(at-.065,-.82-i*.255,.13,.23,"◆",0xffffe1a9,.56f,true);
                        hit(at-.09,-.82-i*.255,.18,.23,()->{cursor=frame.tick();keyframe(cursor);});
                    }
                }
                button(x+.12,-2.83,1.36,.24,"+ CAPTURE",0xff514132,0xffffffff,.56f,true,()->{var frames=new ArrayList<>(frames(value));frames.removeIf(f->f.tick()==cursor);frames.add(capture(cursor));setFrames(frames);});
                button(x+1.56,-2.83,1.36,.24,"EDIT KEY",0xff514132,0xffffffff,.56f,true,()->keyframe(cursor));
                button(x+3,-2.83,1.51,.24,"DELETE KEY",0xff970a08,0xffffffff,.54f,true,()->{if(cursor==0)throw new IllegalArgumentException("첫 키프레임은 삭제할 수 없습니다.");var frames=new ArrayList<>(frames(value));frames.removeIf(f->f.tick()==cursor);setFrames(frames);});
            }
            double playhead=from+span*cursor/duration;
            rect(playhead,-2.67,.028,2.31,TAN,.035);rect(playhead-.025,-.385,.078,.065,TAN,.036);
        }
        void track(int index,String title,int start,int end,int duration,boolean clickableBar,Edit action) {
            double x=2.67,y=-.82-(index%8)*.255;int color=TRACK_COLORS[index%TRACK_COLORS.length];
            button(x+.12,y,1.25,.23,title,index%2==0?0xff585858:0xff242424,0xffffffff,.63f,false,action);
            double left=x+1.48+3.0*start/duration,width=Math.max(.025,3.0*(end-start)/duration);
            rect(left,y+.015,width,.2,color,.014);
            if(clickableBar&&width>=.12)hit(left,y,width,.23,action);
        }
        void drawPaths() throws IOException {
            for(String key:shown) {
                var draft=drafts.get(key);if(draft==null)continue;Object value=draft.value();
                if(value instanceof Scene scene) {
                    var overrides=new HashMap<String,Object>();drafts.forEach((id,d)->overrides.put(id,d.value()));
                    var resolved=service.catalog().resolve(scene,overrides);
                    for(var camera:resolved.cameras().values())drawPath(camera);
                    for(var entity:resolved.entities().values())drawPath(entity);
                } else drawPath(value);
            }
        }
        void drawPath(Object value) {
            if(!dimension(value).equals(world.dimension().location().toString()))return;
            var frames=frames(value);
            for(var frame:frames)marker(new Vec3(frame.x(),frame.y(),frame.z()),"◆ "+frame.tick(),.6f);
            for(int tick=0,end=frames.getLast().tick(),step=Math.max(1,end/128);tick<end;tick+=step){var f=sample(frames,tick);marker(new Vec3(f.x(),f.y(),f.z()),"·",.3f);}
        }
        void marker(Vec3 pos,String label,float size) {
            var text = new TextDisplayElement(Component.literal(label).withColor(0x55ddff)); text.setOverridePos(pos);
            text.setBrightness(new net.minecraft.util.Brightness(15,15));
            text.setScale(new Vector3f(size)); text.setBillboardMode(Display.BillboardConstraints.CENTER); text.setSeeThrough(true); panel.addElement(text);
        }
        Keyframe capture(int time) { return new Keyframe(time,player.getX(),player.getEyeY(),player.getZ(),player.getYRot(),player.getXRot()); }
        void create(Kind kind) throws IOException {
            String id = kind.name().toLowerCase(Locale.ROOT) + "_" + UUID.randomUUID().toString().substring(0,8);
            String dimension = world.dimension().location().toString();
            Object value;
            if (kind == Kind.CAMERA) value = new CameraTrack(1,id,"새 카메라",dimension,List.of(capture(0)));
            else if (kind == Kind.ENTITY) value = new EntityTrack(1,id,"새 엔티티",dimension,"minecraft:pig",null,null,null,List.of(capture(0)));
            else {
                var cameras = service.catalog().list(Kind.CAMERA);
                if (cameras.isEmpty()) throw new IllegalArgumentException("카메라를 먼저 만들고 저장하세요.");
                value = new Scene(1,id,"새 씬",200,List.of(new Clip(cameras.getFirst(),0)),List.of(),List.of());
            }
            browsing=kind; selected = kind+"/"+id; drafts.put(selected,new CinematicCatalog.Draft(kind,id,value,"")); cursor=0; properties();
        }
        void properties() {
            Object value = current().value(); var fields = new LinkedHashMap<String,String>(); fields.put("name",name(value));
            if (value instanceof Scene s) fields.put("duration",""+s.duration());
            else {
                fields.put("dimension",dimension(value));
                if (value instanceof EntityTrack e) { fields.put("type",e.type()); fields.put("item",Objects.toString(e.item(),"")); fields.put("block",Objects.toString(e.block(),"")); fields.put("text",Objects.toString(e.text(),"")); }
            }
            show("이름과 속성",fields,tag -> {
                String name=required(tag,"name");
                if (value instanceof CameraTrack c) update(new CameraTrack(1,c.id(),name,required(tag,"dimension"),c.keyframes()));
                else if (value instanceof EntityTrack e) update(new EntityTrack(1,e.id(),name,required(tag,"dimension"),required(tag,"type"),nullable(tag,"item"),nullable(tag,"block"),nullable(tag,"text"),e.keyframes()));
                else { var s=(Scene)value; update(new Scene(1,s.id(),name,Integer.parseInt(required(tag,"duration")),s.cameras(),s.entities(),s.cues())); }
            });
        }
        void duplicate() {
            var source=current();
            show("복제",Map.of("id",source.id()+"_copy","name",name(source.value())+" 복사"), tag -> {
                String id=CinematicData.id(required(tag,"id"));
                var json=CinematicCatalog.JSON.toJsonTree(source.value()).getAsJsonObject(); json.addProperty("id",id); json.addProperty("name",required(tag,"name"));
                var copy=CinematicCatalog.JSON.fromJson(json,source.value().getClass()); String key=source.kind()+"/"+id;
                try { if (drafts.containsKey(key) || service.catalog().list(source.kind()).contains(id)) throw new IllegalArgumentException("같은 ID가 있습니다."); }
                catch (IOException e) { throw new IllegalStateException(e); }
                drafts.put(key,new CinematicCatalog.Draft(source.kind(),id,copy,"")); selected=key;
            });
        }
        void delete() {
            var draft=current();
            show("삭제 확인",Map.of("id",draft.id()), tag -> {
                if (!draft.id().equals(required(tag,"id"))) throw new IllegalArgumentException("삭제할 ID를 확인하세요.");
                try { if (!draft.revision().isEmpty()) service.catalog().delete(draft); }
                catch (IOException e) { throw new IllegalStateException(e.getMessage(),e); }
                shown.remove(selected);drafts.remove(selected);selected=null;
            });
        }
        void setFrames(List<Keyframe> frames) {
            frames.sort(Comparator.comparingInt(Keyframe::tick)); Object value=current().value();
            if (value instanceof CameraTrack c) update(new CameraTrack(1,c.id(),c.name(),c.dimension(),frames));
            else { var e=(EntityTrack)value; update(new EntityTrack(1,e.id(),e.name(),e.dimension(),e.type(),e.item(),e.block(),e.text(),frames)); }
        }
        void keyframe(int time) {
            Object value=current().value(); var f=sample(frames(value),time);
            var fields=new LinkedHashMap<String,String>(); fields.put("tick",""+time);
            fields.put("position",f.x()+" "+f.y()+" "+f.z()); fields.put("rotation",f.yaw()+" "+f.pitch());
            fields.put("fov",Objects.toString(f.fov(),"")); fields.put("interpolation",f.interpolation().name());
            if (value instanceof EntityTrack) { fields.put("pose",Objects.toString(f.pose(),"STANDING")); fields.put("animation",Objects.toString(f.animation(),"")); fields.put("equipment",CinematicCatalog.JSON.toJson(f.equipment())); }
            show("키프레임",fields,tag -> {
                int tick=Integer.parseInt(required(tag,"tick")); var pos=required(tag,"position").split("\\s+"); var rot=required(tag,"rotation").split("\\s+");
                if (pos.length!=3 || rot.length!=2) throw new IllegalArgumentException("position: x y z, rotation: yaw pitch");
                Map<String,String> equipment=new LinkedHashMap<>();
                if (value instanceof EntityTrack) com.google.gson.JsonParser.parseString(required(tag,"equipment")).getAsJsonObject().entrySet().forEach(e -> equipment.put(e.getKey(),e.getValue().getAsString()));
                String fov=nullable(tag,"fov");
                var frame=new Keyframe(tick,Double.parseDouble(pos[0]),Double.parseDouble(pos[1]),Double.parseDouble(pos[2]),Float.parseFloat(rot[0]),Float.parseFloat(rot[1]),fov==null?null:Float.valueOf(fov),Interpolation.valueOf(required(tag,"interpolation").toUpperCase(Locale.ROOT)),nullable(tag,"pose"),nullable(tag,"animation"),equipment);
                var list=new ArrayList<>(frames(value)); list.removeIf(k -> k.tick()==time || k.tick()==tick); list.add(frame); setFrames(list); cursor=tick;
            });
        }
        void clipDialog(boolean entity,int index) {
            var scene=(Scene)current().value(); var list=entity?scene.entities():scene.cameras();
            var clip=index<0?null:list.get(index);
            show(entity?"엔티티 트랙":"카메라 트랙",Map.of("track",clip==null?"":clip.track(),"start",clip==null?""+cursor:""+clip.start(),"delete","false"),Map.of("track",trackOptions(entity?Kind.ENTITY:Kind.CAMERA)),tag -> {
                var changed=new ArrayList<>(list);
                if (index>=0) changed.remove(index);
                if (!Boolean.parseBoolean(required(tag,"delete"))) changed.add(new Clip(required(tag,"track"),Integer.parseInt(required(tag,"start"))));
                changed.sort(Comparator.comparingInt(Clip::start));
                update(new Scene(1,scene.id(),scene.name(),scene.duration(),entity?scene.cameras():changed,entity?changed:scene.entities(),scene.cues()));
            });
        }
        void cues() {
            var scene=(Scene)current().value();
            var prior=scene.cues().stream().filter(c -> c.tick()==cursor).findFirst().orElse(new Cue(cursor,CueType.MUSIC,"all",20,0xffffff));
            show("타임라인 큐",Map.of("tick",""+cursor,"type",prior.type().name(),"id",Objects.toString(prior.id(),""),"duration",""+prior.duration(),"color",Integer.toHexString(prior.color()),"delete","false"), tag -> {
                int time=Integer.parseInt(required(tag,"tick")); var cues=new ArrayList<>(scene.cues()); cues.removeIf(c -> c.tick()==time && c.type()==prior.type());
                if (!Boolean.parseBoolean(required(tag,"delete"))) cues.add(new Cue(time,CueType.valueOf(required(tag,"type").toUpperCase(Locale.ROOT)),nullable(tag,"id"),Integer.parseInt(required(tag,"duration")),Integer.parseUnsignedInt(required(tag,"color"),16)));
                cues.sort(Comparator.comparingInt(Cue::tick)); update(new Scene(1,scene.id(),scene.name(),scene.duration(),scene.cameras(),scene.entities(),cues));
            });
        }
        void preview(boolean paused) throws IOException {
            if (preview!=null) preview.stop();
            var overrides=new HashMap<String,Object>(); drafts.forEach((key,draft)->overrides.put(key,draft.value()));
            Object value=current().value(); ResolvedScene resolved;
            if (value instanceof Scene s) resolved=service.catalog().resolve(s,overrides);
            else if (value instanceof CameraTrack c) resolved=new ResolvedScene(new Scene(1,"preview","미리보기",Math.max(cursor+1,Math.max(100,c.keyframes().getLast().tick()+1)),List.of(new Clip(c.id(),0)),List.of(),List.of()),Map.of(c.id(),c),Map.of());
            else {
                var e=(EntityTrack)value; var c=new CameraTrack(1,"preview_camera","미리보기",world.dimension().location().toString(),List.of(capture(0)));
                resolved=new ResolvedScene(new Scene(1,"preview","미리보기",Math.max(cursor+1,Math.max(100,e.keyframes().getLast().tick()+1)),List.of(new Clip(c.id(),0)),List.of(new Clip(e.id(),0)),List.of()),Map.of(c.id(),c),Map.of(e.id(),e));
            }
            preview=service.preview(resolved,player,cursor,paused);
            player.sendSystemMessage(Component.literal("미리보기 종료: /minigamelib cinematic stop"));
        }
        Map<String,String> trackOptions(Kind kind) {
            var options=new TreeMap<String,String>();
            try { for(String id:service.catalog().list(kind))try{options.put(id,name(service.catalog().read(kind,id).value()));}catch(IOException ignored){} }
            catch(IOException e){throw new IllegalStateException(e);}
            drafts.values().stream().filter(d->d.kind()==kind).forEach(d->options.put(d.id(),name(d.value())));
            return options;
        }
        void show(String title,Map<String,String> fields,Consumer<CompoundTag> callback) { show(title,fields,Map.of(),callback); }
        void show(String title,Map<String,String> fields,Map<String,Map<String,String>> choices,Consumer<CompoundTag> callback) {
            String editing = selected;
            var revision = editing == null ? null : drafts.get(editing);
            dialogId=ResourceLocation.fromNamespaceAndPath("minigamelib","editor/"+UUID.randomUUID());
            dialog=tag -> {
                if (!Objects.equals(editing,selected) || (editing != null && drafts.get(editing) != revision)) throw new IllegalArgumentException("편집 상태가 변경되었습니다. 입력 창을 다시 여세요.");
                callback.accept(tag);
            };
            var inputs=new ArrayList<Input>();
            fields.forEach((key,value)->{
                Map<String,String> options=choices.get(key);
                if(options==null && key.equals("interpolation"))options=Map.of("LINEAR","선형","SMOOTH","부드럽게","CUT","컷");
                if(options==null && key.equals("delete"))options=Map.of("false","유지 / 추가","true","삭제");
                if(options==null && key.equals("dimension")) {
                    options=new LinkedHashMap<>();
                    for(var world:server.levelKeys()) {String id=world.location().toString();options.put(id,switch(id){case "minecraft:overworld"->"오버월드";case "minecraft:the_nether"->"네더";case "minecraft:the_end"->"엔드";default->id;});}
                }
                if(options!=null && !options.isEmpty()) {
                    var entries=new ArrayList<net.minecraft.server.dialog.input.SingleOptionInput.Entry>();
                    String initial=options.containsKey(value)?value:options.keySet().iterator().next();
                    options.forEach((id,label)->entries.add(new net.minecraft.server.dialog.input.SingleOptionInput.Entry(id,Optional.of(Component.literal(label)),id.equals(initial))));
                    inputs.add(new Input(key,new net.minecraft.server.dialog.input.SingleOptionInput(300,entries,Component.literal(inputLabel(key)),true)));
                } else inputs.add(new Input(key,new TextInput(300,Component.literal(inputLabel(key)),true,value,8192,Optional.empty())));
            });
            var common=new CommonDialogData(Component.literal(title),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(Component.literal("초안에 적용합니다. 파일 변경은 패널의 저장 버튼을 사용하세요."),300)),inputs);
            var apply=new ActionButton(new CommonButtonData(Component.literal("적용"),150),Optional.of(new CustomAll(dialogId,Optional.empty())));
            var cancel=new ActionButton(new CommonButtonData(Component.literal("취소"),150),Optional.empty());
            player.openDialog(Holder.direct(new MultiActionDialog(common,List.of(apply),Optional.of(cancel),1)));
        }
        void close() { closed=true; dialog=null; dialogId=null; if (preview!=null) preview.stop(); panel.destroy(); drafts.clear(); }
    }
    private static String name(Object value) { return value instanceof CameraTrack c?c.name():value instanceof EntityTrack e?e.name():((Scene)value).name(); }
    private static String dimension(Object value) { return value instanceof CameraTrack c?c.dimension():((EntityTrack)value).dimension(); }
    private static List<Keyframe> frames(Object value) { return value instanceof CameraTrack c?c.keyframes():((EntityTrack)value).keyframes(); }
    private static String required(CompoundTag tag,String key) { return tag.getString(key).filter(s -> !s.isBlank()).orElseThrow(()->new IllegalArgumentException("필수 입력: "+key)); }
    private static String nullable(CompoundTag tag,String key) { return tag.getString(key).filter(s -> !s.isBlank()).orElse(null); }
    private static int fontWidth(String text) {
        var font=eu.pb4.mapcanvas.api.font.DefaultFonts.VANILLA;int width=0;
        for(int cp:text.codePoints().toArray())width+=font.containsGlyph(cp)?font.getGlyphWidth(cp,8,2):9;
        return width;
    }
    private static String shortLabel(String text,int width) {
        if(fontWidth(text)<=width)return text;
        int end=text.length();while(end>0&&fontWidth(text.substring(0,end)+"...")>width)end=text.offsetByCodePoints(end,-1);
        return text.substring(0,end)+"...";
    }
    private static int brighter(int color) {
        return 0xff000000 | Math.min(255,((color>>16)&255)+30)<<16 | Math.min(255,((color>>8)&255)+24)<<8 | Math.min(255,(color&255)+18);
    }
    private static String formatTime(int tick) {return String.format(Locale.ROOT,"%d:%02d",tick/1200,(tick/20)%60);}
    private static String inputLabel(String key) {
        return switch(key) {
            case "name"->"이름"; case "dimension"->"차원"; case "duration"->"길이 (틱)";
            case "track"->"트랙 선택"; case "start"->"시작 시간 (틱)"; case "tick"->"시간 (틱)";
            case "position"->"위치: x y z"; case "rotation"->"회전: yaw pitch"; case "fov"->"FOV (선택)";
            case "interpolation"->"보간 방식"; case "pose"->"포즈"; case "animation"->"애니메이션 (선택)";
            case "equipment"->"장비 JSON"; case "delete"->"작업"; case "item"->"아이템 ID";
            case "block"->"블록 ID"; case "text"->"표시할 글"; case "color"->"색상 (RGB 16진수)"; default->key;
        };
    }
}
