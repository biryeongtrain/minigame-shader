package kim.biryeong.dantashader.shaderfx.api;

import kim.biryeong.dantashader.DantaShaderInitializer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class FileUtil {
    public static String wrapCase(String snippet, int caseNum) {
        return String.format(
                """
                        case %d: {
                        %s
                        } break;
                        """, caseNum, snippet
        );
    }

    public static byte[] loadBytes(String snippet) {
        try (InputStream is = DantaShaderInitializer.class.getResourceAsStream("/shaders/" + snippet)) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String loadSnippet(String snippet) {
        return extractFunctionImplementation(new String(loadBytes("snippets/" + snippet), StandardCharsets.UTF_8));
    }

    public static String loadCore(String id) {
        return new String(loadBytes(id), StandardCharsets.UTF_8);
    }

    public static String extractFunctionImplementation(String code) {
        var list = code.split("\n");
        return String.join("\n", Arrays.copyOfRange(list, 1, list.length - 1));
    }
}
