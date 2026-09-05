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
                    session.panel.tick();
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
        final Vec3 anchor, right;
        final Map<String, CinematicCatalog.Draft> drafts = new LinkedHashMap<>();
        ElementHolder panel = new ElementHolder();
        String selected;
        int cursor, page, generation, lastClick = -1;
        boolean markers = true, closed, viewing;
        Playback preview;
        ResourceLocation dialogId;
        Consumer<CompoundTag> dialog;
        Session(ServerPlayer player) {
            this.player = player; world = player.level();
            double yaw = Math.toRadians(player.getYRot());
            right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            anchor = player.position().add(-Math.sin(yaw)*3.0, 3.4, Math.cos(yaw)*3.0);
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
            selected = key;
            cursor = 0;
        }
        void update(Object value) {
            var draft = current(); drafts.put(selected, new CinematicCatalog.Draft(draft.kind(), draft.id(), value, draft.revision()));
        }
        void refresh() {
            if (closed || CameraViews.get(player) != null) return;
            panel.destroy(); panel = new ElementHolder(); generation++;
            new ManualAttachment(panel, world, () -> Vec3.ZERO);
            try {
                int column = 0;
                for (var kind : Kind.values()) {
                    double x = (column++ - 1) * 1.9;
                    background(x - .9, -1.84, 1.8, 2.1, net.minecraft.world.level.block.Blocks.BLACK_CONCRETE);
                    row(x, 0, kind == Kind.CAMERA ? "Camera" : kind == Kind.ENTITY ? "Entity" : "Scenes", 0xff17304a, null);
                    row(x, -.28, "+ 새로 만들기", 0xff28516b, () -> create(kind));
                    var names = new TreeSet<>(service.catalog().list(kind));
                    drafts.values().stream().filter(d -> d.kind() == kind).forEach(d -> names.add(d.id()));
                    int index = 0;
                    for (var id : names.stream().skip(page * 5L).limit(5).toList()) {
                        var draft = drafts.get(kind + "/" + id);
                        String label;
                        try { label = name(draft == null ? service.catalog().read(kind, id).value() : draft.value()); }
                        catch (IOException e) { label = "⚠ " + id; }
                        boolean active = (kind + "/" + id).equals(selected);
                        row(x, -.58 - index++*.27, (active ? "▶ " : "") + label, active ? 0xff387897 : 0xff172331, () -> select(kind, id));
                    }
                }
                row(-1.9, -2, "◀ 이전 목록", 0xff223344, () -> page = Math.max(0, page-1));
                row(0, -2, "다음 목록 ▶", 0xff223344, () -> page++);
                row(1.9, -2, "편집기 닫기", 0xff593138, () -> CinematicEditor.this.close(player));
                if (selected != null) {
                    row(-1.9, -2.34, "이름·속성", 0xff21405a, this::properties);
                    row(0, -2.34, "복제", 0xff21405a, this::duplicate);
                    row(1.9, -2.34, "삭제", 0xff593138, this::delete);
                    row(-1.9, -2.65, "저장", 0xff256246, () -> drafts.put(selected, service.catalog().save(current(), current().value())));
                    row(0, -2.65, markers ? "경로 숨기기" : "경로 표시", 0xff21405a, () -> markers = !markers);
                    row(1.9, -2.65, "미리보기 ▶", 0xff21405a, () -> preview(false));
                    timeline();
                }
                panel.tick(); panel.startWatching(player);
            } catch (Exception e) { player.sendSystemMessage(Component.literal("패널: " + e.getMessage())); }
        }
        void background(double x, double y, double width, double height, net.minecraft.world.level.block.Block block) {
            var display = new BlockDisplayElement(block.defaultBlockState());
            display.setOverridePos(anchor.add(right.scale(x * .65)).add(0, y * .65, 0).add(new Vec3(-right.z,0,right.x).scale(-.04)));
            display.setScale(new Vector3f((float)(width*.65),(float)(height*.65),.025f));
            display.setLeftRotation(new org.joml.Quaternionf().rotationY((float)Math.atan2(-right.z,right.x)));
            display.setBrightness(new net.minecraft.util.Brightness(15,15)); panel.addElement(display);
        }
        void row(double x, double y, String label, int color, Edit action) { row(x,y,label,color,action,1.05f); }
        void row(double x, double y, String label, int color, Edit action, float width) {
            Vec3 pos = anchor.add(right.scale(x * .65)).add(0, y * .65, 0);
            var text = new TextDisplayElement(Component.literal(shortLabel(label))); text.setOverridePos(pos);
            text.setScale(new Vector3f(.60f)); text.setBillboardMode(Display.BillboardConstraints.CENTER);
            text.setBrightness(new net.minecraft.util.Brightness(15,15));
            text.setBackground(color); text.setLineWidth(230); text.setShadow(true); panel.addElement(text);
            if (action == null) return;
            int revision = generation;
            var hit = new InteractionElement(); hit.setOverridePos(pos.add(0,-.02,0)); hit.setSize(width,.15f); hit.setResponse(true);
            hit.setInteractionHandler(new VirtualElement.InteractionHandler() {
                private void click(ServerPlayer p) {
                    if (!valid(p) || revision != generation || CameraViews.get(p) != null || p.getEyePosition().distanceToSqr(pos) > 36 || lastClick == server.getTickCount()) return;
                    lastClick = server.getTickCount(); tryAction(action);
                }
                @Override public void interact(ServerPlayer p, InteractionHand hand) { if (hand == InteractionHand.MAIN_HAND) click(p); }
                @Override public void interactAt(ServerPlayer p, InteractionHand hand, Vec3 pos) { if (hand == InteractionHand.MAIN_HAND) click(p); }
                @Override public void attack(ServerPlayer p) { click(p); }
            }); panel.addElement(hit);
        }
        void timeline() {
            Object value = current().value();
            int duration = value instanceof Scene s ? s.duration() : Math.max(100, frames(value).getLast().tick()+1);
            row(0, -3, "타임라인  |  " + cursor + " / " + duration + " tick", 0xff172331, () -> show("시간 이동", Map.of("tick", ""+cursor), tag -> { cursor = Integer.parseInt(required(tag,"tick")); if (cursor < 0 || cursor >= duration) throw new IllegalArgumentException("시간 범위 오류"); }));
            for (int i = 0; i <= 4; i++) {
                int time = Math.min(duration - 1, duration*i/4);
                small(-2.4+i*1.2,-3.32, (cursor == time ? "▼ " : "")+time, 0xff34475c, () -> { cursor=time; preview(true); });
            }
            if (value instanceof Scene scene) {
                row(-1.9,-3.65,"+ 카메라 트랙",0xff387897, () -> clipDialog(false,-1));
                row(0,-3.65,"+ 엔티티 트랙",0xff8b623e, () -> clipDialog(true,-1));
                row(1.9,-3.65,"음악·화면 큐",0xff655080, this::cues);
                var clips = new ArrayList<Clip>(); clips.addAll(scene.cameras()); clips.addAll(scene.entities());
                int rowIndex = 0;
                for (int index = page*4; index < Math.min(clips.size(),page*4+4); index++) {
                    var clip=clips.get(index); boolean entity=index>=scene.cameras().size();
                    int trackIndex=entity?index-scene.cameras().size():index;
                    double y=-4.1-rowIndex++*.34;
                    row(-1.9,y,trackOptions(entity?Kind.ENTITY:Kind.CAMERA).getOrDefault(clip.track(),clip.track()),entity?0xff785137:0xff285c78,()->clipDialog(entity,trackIndex));
                    int end = !entity && trackIndex+1<scene.cameras().size()?scene.cameras().get(trackIndex+1).start():duration;
                    double startX=-.8+3.45*clip.start()/duration, endX=-.8+3.45*end/duration;
                    background(startX,y,Math.max(.04,endX-startX),.13,entity?net.minecraft.world.level.block.Blocks.ORANGE_CONCRETE:net.minecraft.world.level.block.Blocks.LIGHT_BLUE_CONCRETE);
                    background(-.8+3.45*cursor/duration,y-.03,.018,.25,net.minecraft.world.level.block.Blocks.WHITE_CONCRETE);
                }
            } else {
                row(-1.9,-3.65,"+ 현재 시점 캡처",0xff285c78, () -> { var frames = new ArrayList<>(frames(value)); frames.removeIf(f -> f.tick()==cursor); frames.add(capture(cursor)); setFrames(frames); });
                row(0,-3.65,"키프레임 편집",0xff285c78, () -> keyframe(cursor));
                row(1.9,-3.65,"키프레임 삭제",0xff593138, () -> { if (cursor == 0) throw new IllegalArgumentException("첫 키프레임은 삭제할 수 없습니다."); var frames = new ArrayList<>(frames(value)); frames.removeIf(f -> f.tick()==cursor); setFrames(frames); });
                for (var frame : frames(value)) {
                    double x = -2.4 + Math.min(1,(double)frame.tick()/duration)*4.8;
                    small(x,-4,"◆ " + frame.tick(),0xff397391, () -> { cursor=frame.tick(); keyframe(cursor); });
                }
                if (markers && dimension(value).equals(world.dimension().location().toString())) {
                    var frames = frames(value);
                    for (var frame : frames) marker(new Vec3(frame.x(),frame.y(),frame.z()), "◆ " + frame.tick(), .6f);
                    for (int tick = 0, end = frames.getLast().tick(), step = Math.max(1,end/128); tick < end; tick += step) {
                        var f = sample(frames,tick); marker(new Vec3(f.x(),f.y(),f.z()), "·", .3f);
                    }
                }
            }
        }
        void small(double x,double y,String label,int color,Edit action) {
            row(x,y,label,color,action,.55f);
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
            selected = kind+"/"+id; drafts.put(selected,new CinematicCatalog.Draft(kind,id,value,"")); cursor=0; properties();
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
                drafts.remove(selected); selected=null;
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
    private static String shortLabel(String text) {
        int width=0,end=0;
        for (int cp : text.codePoints().toArray()) { int next=width+(cp>255?2:1); if(next>20) return text.substring(0,end)+"…";width=next;end+=Character.charCount(cp); }
        return text;
    }
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
