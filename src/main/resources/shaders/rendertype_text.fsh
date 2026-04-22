#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

//%IMPORTS%

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

flat in int effectId;

flat in int frames;
flat in int fps;
flat in float frameheight;

out vec4 fragColor;

const ivec3 lookup[] = ivec3[128](
ivec3(89, 125, 39),   ivec3(109, 153, 48),  ivec3(127, 178, 56),  ivec3(67, 94, 29),
ivec3(174, 164, 115), ivec3(213, 201, 140), ivec3(247, 233, 163), ivec3(130, 123, 86),
ivec3(140, 140, 140), ivec3(171, 171, 171), ivec3(199, 199, 199), ivec3(105, 105, 105),
ivec3(180, 0, 0),     ivec3(220, 0, 0),     ivec3(255, 0, 0),     ivec3(135, 0, 0),
ivec3(112, 112, 180), ivec3(138, 138, 220), ivec3(160, 160, 255), ivec3(84, 84, 135),
ivec3(117, 117, 117), ivec3(144, 144, 144), ivec3(167, 167, 167), ivec3(88, 88, 88),
ivec3(0, 87, 0),      ivec3(0, 106, 0),     ivec3(0, 124, 0),     ivec3(0, 65, 0),
ivec3(180, 180, 180), ivec3(220, 220, 220), ivec3(255, 255, 255), ivec3(135, 135, 135),
ivec3(115, 118, 129), ivec3(141, 144, 158), ivec3(164, 168, 184), ivec3(86, 88, 97),
ivec3(106, 76, 54),   ivec3(130, 94, 66),   ivec3(151, 109, 77),  ivec3(79, 57, 40),
ivec3(79, 79, 79),    ivec3(96, 96, 96),    ivec3(112, 112, 112), ivec3(59, 59, 59),
ivec3(45, 45, 180),   ivec3(55, 55, 220),   ivec3(64, 64, 255),   ivec3(33, 33, 135),
ivec3(100, 84, 50),   ivec3(123, 102, 62),  ivec3(143, 119, 72),  ivec3(75, 63, 38),
ivec3(180, 177, 172), ivec3(220, 217, 211), ivec3(255, 252, 245), ivec3(135, 133, 129),
ivec3(152, 89, 36),   ivec3(186, 109, 44),  ivec3(216, 127, 51),  ivec3(114, 67, 27),
ivec3(125, 53, 152),  ivec3(153, 65, 186),  ivec3(178, 76, 216),  ivec3(94, 40, 114),
ivec3(72, 108, 152),  ivec3(88, 132, 186),  ivec3(102, 153, 216), ivec3(54, 81, 114),
ivec3(161, 161, 36),  ivec3(197, 197, 44),  ivec3(229, 229, 51),  ivec3(121, 121, 27),
ivec3(89, 144, 17),   ivec3(109, 176, 21),  ivec3(127, 204, 25),  ivec3(67, 108, 13),
ivec3(170, 89, 116),  ivec3(208, 109, 142), ivec3(242, 127, 165), ivec3(128, 67, 87),
ivec3(53, 53, 53),    ivec3(65, 65, 65),    ivec3(76, 76, 76),    ivec3(40, 40, 40),
ivec3(108, 108, 108), ivec3(132, 132, 132), ivec3(153, 153, 153), ivec3(81, 81, 81),
ivec3(53, 89, 108),   ivec3(65, 109, 132),  ivec3(76, 127, 153),  ivec3(40, 67, 81),
ivec3(89, 44, 125),   ivec3(109, 54, 153),  ivec3(127, 63, 178),  ivec3(67, 33, 94),
ivec3(36, 53, 125),   ivec3(44, 65, 153),   ivec3(51, 76, 178),   ivec3(27, 40, 94),
ivec3(72, 53, 36),    ivec3(88, 65, 44),    ivec3(102, 76, 51),   ivec3(54, 40, 27),
ivec3(72, 89, 36),    ivec3(88, 109, 44),   ivec3(102, 127, 51),  ivec3(54, 67, 27),
ivec3(108, 36, 36),   ivec3(132, 44, 44),   ivec3(153, 51, 51),   ivec3(81, 27, 27),
ivec3(17, 17, 17),    ivec3(21, 21, 21),    ivec3(25, 25, 25),    ivec3(13, 13, 13),
ivec3(176, 168, 54),  ivec3(215, 205, 66),  ivec3(250, 238, 77),  ivec3(132, 126, 40),
ivec3(64, 154, 150),  ivec3(79, 188, 183),  ivec3(92, 219, 213),  ivec3(48, 115, 112),
ivec3(52, 90, 180),   ivec3(63, 110, 220),  ivec3(74, 128, 255),  ivec3(39, 67, 135)
);

int decode7u(vec3 color) {
    vec3 c = color * 255.0;
    int bestIndex = 0;
    float bestDistance = 1e20;

    for (int i = 0; i < 128; i++) {
        vec3 diff = c - vec3(lookup[i]);
        float distance = dot(diff, diff);
        if (distance < bestDistance) {
            bestDistance = distance;
            bestIndex = i;
        }
    }

    return bestIndex;
}

bool isNearI11(vec4 sampledColor, float maxDistance) {
    if (sampledColor.a < 0.99) {
        return false;
    }

    vec3 c = sampledColor.rgb * 255.0;
    int bestIndex = 0;
    float bestDistance = 1e20;

    for (int i = 0; i < 8; i++) {
        vec3 diff = c - vec3(lookup[i]);
        float distance = dot(diff, diff);
        if (distance < bestDistance) {
            bestDistance = distance;
            bestIndex = i;
        }
    }

    return bestIndex < 8 && bestDistance <= maxDistance;
}

bool isOpaque(vec4 sampledColor) {
    return sampledColor.a >= 0.99;
}

bool blockIsOpaque(sampler2D samplerTex, ivec2 origin, ivec2 texSize) {
    ivec2 clampedOrigin = clamp(origin, ivec2(0), texSize - ivec2(2));
    return isOpaque(texelFetch(samplerTex, clampedOrigin, 0))
    && isOpaque(texelFetch(samplerTex, clampedOrigin + ivec2(1, 0), 0))
    && isOpaque(texelFetch(samplerTex, clampedOrigin + ivec2(0, 1), 0))
    && isOpaque(texelFetch(samplerTex, clampedOrigin + ivec2(1, 1), 0));
}

int i11Score(sampler2D samplerTex, ivec2 origin, ivec2 texSize, float maxDistance) {
    ivec2 minCoord = ivec2(0);
    ivec2 maxOrigin = texSize - ivec2(2);
    ivec2 o0 = clamp(origin, minCoord, maxOrigin);
    ivec2 o1 = clamp(origin + ivec2(2, 0), minCoord, maxOrigin);
    ivec2 o2 = clamp(origin + ivec2(0, 2), minCoord, maxOrigin);

    int score = 0;
    score += isNearI11(texelFetch(samplerTex, o0 + ivec2(1, 1), 0), maxDistance) ? 1 : 0;
    score += isNearI11(texelFetch(samplerTex, o1 + ivec2(1, 1), 0), maxDistance) ? 1 : 0;
    score += isNearI11(texelFetch(samplerTex, o2 + ivec2(1, 1), 0), maxDistance) ? 1 : 0;
    return score;
}

int i11Available(sampler2D samplerTex, ivec2 origin, ivec2 texSize) {
    ivec2 minCoord = ivec2(0);
    ivec2 maxOrigin = texSize - ivec2(2);
    ivec2 o0 = clamp(origin, minCoord, maxOrigin);
    ivec2 o1 = clamp(origin + ivec2(2, 0), minCoord, maxOrigin);
    ivec2 o2 = clamp(origin + ivec2(0, 2), minCoord, maxOrigin);

    int available = 0;
    available += isOpaque(texelFetch(samplerTex, o0 + ivec2(1, 1), 0)) ? 1 : 0;
    available += isOpaque(texelFetch(samplerTex, o1 + ivec2(1, 1), 0)) ? 1 : 0;
    available += isOpaque(texelFetch(samplerTex, o2 + ivec2(1, 1), 0)) ? 1 : 0;
    return available;
}

int candidateScore(sampler2D samplerTex, ivec2 origin, ivec2 texSize) {
    ivec2 clampedOrigin = clamp(origin, ivec2(0), texSize - ivec2(2));
    if (!isNearI11(texelFetch(samplerTex, clampedOrigin + ivec2(1, 1), 0), 256.0)) {
        return -1;
    }

    int available = i11Available(samplerTex, clampedOrigin, texSize);
    if (available <= 0) {
        return -1;
    }

    int score = i11Score(samplerTex, clampedOrigin, texSize, 256.0);
    int requiredScore = min(3, available);
    return score >= requiredScore ? score : -1;
}

bool findBlockOrigin(sampler2D samplerTex, ivec2 pixel, ivec2 texSize, out ivec2 originOut) {
    if (texSize.x < 2 || texSize.y < 2) {
        return false;
    }

    int originEvenX = pixel.x - (pixel.x & 1);
    int originOddX = pixel.x - ((pixel.x + 1) & 1);
    int originEvenY = pixel.y - (pixel.y & 1);
    int originOddY = pixel.y - ((pixel.y + 1) & 1);

    ivec2 candidate0 = ivec2(originEvenX, originEvenY);
    ivec2 candidate1 = ivec2(originOddX, originEvenY);
    ivec2 candidate2 = ivec2(originEvenX, originOddY);
    ivec2 candidate3 = ivec2(originOddX, originOddY);

    int bestScore = -1;
    ivec2 bestOrigin = candidate0;

    int score0 = candidateScore(samplerTex, candidate0, texSize);
    if (score0 > bestScore) {
        bestScore = score0;
        bestOrigin = candidate0;
    }

    int score1 = candidateScore(samplerTex, candidate1, texSize);
    if (score1 > bestScore) {
        bestScore = score1;
        bestOrigin = candidate1;
    }

    int score2 = candidateScore(samplerTex, candidate2, texSize);
    if (score2 > bestScore) {
        bestScore = score2;
        bestOrigin = candidate2;
    }

    int score3 = candidateScore(samplerTex, candidate3, texSize);
    if (score3 > bestScore) {
        bestScore = score3;
        bestOrigin = candidate3;
    }

    if (bestScore < 0) {
        return false;
    }

    originOut = clamp(bestOrigin, ivec2(0), texSize - ivec2(2));
    return true;
}

bool isRgbMapEncoded(sampler2D samplerTex) {
    ivec2 texSize = textureSize(samplerTex, 0).xy;
    if (texSize.x < 2 || texSize.y < 2) {
        return false;
    }

    ivec2 center = clamp(texSize / 2, ivec2(0), texSize - ivec2(1));
    ivec2 origin;
    if (findBlockOrigin(samplerTex, center, texSize, origin)) {
        return true;
    }

    ivec2 topLeft = ivec2(1, 1);
    if (findBlockOrigin(samplerTex, topLeft, texSize, origin)) {
        return true;
    }

    ivec2 topRight = ivec2(texSize.x - 2, 1);
    if (findBlockOrigin(samplerTex, topRight, texSize, origin)) {
        return true;
    }

    ivec2 bottomLeft = ivec2(1, texSize.y - 2);
    return findBlockOrigin(samplerTex, bottomLeft, texSize, origin);
}
void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    vec4 color = texColor * vertexColor * ColorModulator;

    ivec2 texSize = textureSize(Sampler0, 0).xy;
    if (isOpaque(color) && isRgbMapEncoded(Sampler0)) {
        ivec2 pixel = clamp(ivec2(floor(texCoord0 * vec2(texSize))), ivec2(0), texSize - ivec2(1));
        ivec2 coord;
        if (findBlockOrigin(Sampler0, pixel, texSize, coord) && blockIsOpaque(Sampler0, coord, texSize)) {
            int b1 = decode7u(texelFetch(Sampler0, coord, 0).rgb);
            int b2 = decode7u(texelFetch(Sampler0, coord + ivec2(1, 0), 0).rgb);
            int b3 = decode7u(texelFetch(Sampler0, coord + ivec2(0, 1), 0).rgb);
            int b4 = decode7u(texelFetch(Sampler0, coord + ivec2(1, 1), 0).rgb);
            b1 |= (b4 & 1) << 7; b2 |= (b4 & 2) << 6; b3 |= (b4 & 4) << 5;
            //int u24 = (b3 << 16) | (b2 << 8) | b1;

            color = vec4(vec3(b3, b2, b1) / 255.0, 1.0);
        }
    }

    vec2 centerUV = gl_FragCoord.xy / ScreenSize - 0.5;
    float ratio = ScreenSize.y / ScreenSize.x;

    if (effectId != 0) {
        switch (effectId) {
//%CASES%
        }
    }

    // animated emoji
    if (frames > 1) {
        int frameI = int(mod(floor(GameTime*1000.0*fps), frames-1));
        float framePart = 1.0 / float(frames);

        float ty = texCoord0.y*256.0;
        if (ty > frameheight)
            discard;

        color = texture(Sampler0, texCoord0  + vec2(0, frameheight/256*(frameI+1))) * vertexColor * ColorModulator;
    }

    if (color.a < 0.1) {
        discard;
    }

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
