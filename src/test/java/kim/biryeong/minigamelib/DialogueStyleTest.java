package kim.biryeong.minigamelib;

import kim.biryeong.minigamelib.cinematic.CinematicBuilder;
import kim.biryeong.minigamelib.cinematic.CinematicCatalog;
import kim.biryeong.minigamelib.cinematic.dialogue.DialogueFonts;
import kim.biryeong.minigamelib.cinematic.dialogue.DialogueStyle;
import kim.biryeong.minigamelib.cinematic.dialogue.DialogueStyle.TextFormat;
import kim.biryeong.minigamelib.cinematic.dialogue.DialogueText;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static kim.biryeong.minigamelib.cinematic.CinematicData.*;
import static org.junit.jupiter.api.Assertions.*;

class DialogueStyleTest {
    @AfterEach void reset() { DialogueStyle.setDefault(null); DialogueStyle.forSpeaker("andlist", null); }

    @Test void markupBecomesPerCharacterStyles() {
        var text = DialogueText.parse("<b>굵게</b> <st>취소</st> <u>밑줄</u> <i>기울임</i> <obf>?</obf> <#ff0000>빨강</#ff0000> <font:minecraft:uniform>U</font> \\<태그", DialogueFonts.textStyle());
        assertEquals("굵게 취소 밑줄 기울임 ? 빨강 U <태그", text.string());
        assertTrue(text.style(0).isBold());
        assertFalse(text.style(2).isBold(), "the space after </b> is plain");
        assertTrue(text.style(3).isStrikethrough());
        assertTrue(text.style(6).isUnderlined());
        assertTrue(text.style(9).isItalic());
        assertTrue(text.style(13).isObfuscated());
        assertEquals(0xFF0000, text.style(15).getColor().getValue());
        assertEquals(new FontDescription.Resource(Identifier.parse("minecraft:uniform")), text.style(18).getFont());
        assertEquals(new FontDescription.Resource(DialogueFonts.TEXT), text.style(0 + 2).getFont(), "unstyled text keeps the base font");
        assertEquals("plain <3 stays", DialogueText.parse("plain <3 stays", DialogueFonts.textStyle()).string(), "unknown tags stay text");
    }
    @Test void boldCharactersAreOnePixelWiderAndWrapAccordingly() {
        var plain = DialogueText.parse("가나다라", DialogueFonts.textStyle());
        var bold = DialogueText.parse("<b>가나다라</b>", DialogueFonts.textStyle());
        assertEquals(plain.width() + 4, bold.width(), 0.001);
        for (var row : DialogueText.parse("<b>가나다 라마바 사아자</b> 차카타 파하", DialogueFonts.textStyle()).wrap(40))
            assertTrue(row.width() <= 40, row.string());
        assertEquals(java.util.List.of("첫 줄", "둘째 줄"), DialogueText.parse("첫 <u>줄</u>\n둘째 줄", DialogueFonts.textStyle()).wrap(500).stream().map(DialogueText::string).toList());
    }
    @Test void styleLayersResolveFromGlobalToLine() {
        var plain = new DialogueLine(0, "andlist", null, "x", 5, false);
        assertEquals(DialogueLine.DEFAULT_COLOR, DialogueStyle.resolve(plain).lineColor());
        DialogueStyle.setDefault(DialogueStyle.of().textColor(0xEEEEEE).name(TextFormat.of().italic(true)));
        DialogueStyle.forSpeaker("<b>andlist</b>", DialogueStyle.of().nameColor(0xFF66AA));
        var resolved = DialogueStyle.resolve(plain);
        assertEquals(0xFF66AA, resolved.nameColor(), "speaker style matches the plain name");
        assertEquals(0xFF66AA, resolved.lineColor(), "line follows the name colour");
        assertEquals(0xEEEEEE, resolved.textColor());
        assertTrue(resolved.nameStyle().isItalic());
        var accent = new DialogueLine(0, "andlist", null, 0x123456, "x", 5, false, 0);
        assertEquals(0x123456, DialogueStyle.resolve(accent).nameColor(), "line accent beats the speaker style");
        var styled = accent.withStyle(DialogueStyle.of().lineColor(0x00FF00).highlightColor(0xFFFF00).hideLine()
                .text(TextFormat.of().bold(true).strikethrough(true).shadow(false).font("minecraft:default")));
        var r = DialogueStyle.resolve(styled);
        assertEquals(0x123456, r.nameColor());
        assertEquals(0x00FF00, r.lineColor());
        assertEquals(0xFFFF00, r.highlightColor());
        assertFalse(r.showLine());
        assertFalse(r.textShadow());
        assertTrue(r.nameShadow());
        assertTrue(r.textStyle().isBold() && r.textStyle().isStrikethrough());
        assertEquals(new FontDescription.Resource(Identifier.parse("minecraft:default")), r.textStyle().getFont());
    }
    @Test void styleRoundTripsThroughSceneJsonAndBuilder() {
        var style = DialogueStyle.of().nameColor(0xE05555).lineColor(0x666666).name(TextFormat.of().underlined(true).font("minecraft:uniform"));
        var scene = CinematicBuilder.scene("styled").still(0, 0, 70, 0, 0, 0)
                .mannequin("hero", "x", a -> a.skin("hero").key(0, 0, 70, 0, 0, 0))
                .dialogue(0, d -> d.defaults(style).say("a", null, "<b>하나</b>").say("b", null, "둘").style(DialogueStyle.of().textColor(0xABCDEF)))
                .build();
        assertEquals("skin:hero", scene.entities().get("styled.hero").profile());
        var lines = scene.scene().dialogues();
        assertEquals(style, lines.getFirst().style());
        assertEquals(style.textColor(0xABCDEF), lines.get(1).style());
        assertEquals(2 + 8 + 30, lines.getFirst().duration(), "markup tags are not typed: 2 chars at 30/s");
        var json = CinematicCatalog.JSON.toJson(scene.scene());
        assertFalse(json.contains("\"bold\": null"), "unset fields are omitted");
        assertEquals(scene.scene(), CinematicCatalog.JSON.fromJson(json, Scene.class));
    }
}
