#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

const vec2[4] corners = vec2[4](vec2(0), vec2(0, 1), vec2(1), vec2(1, 0));

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
#moj_import <globals.glsl> //Hud
#moj_import <hud.glsl> //Hud

flat out int effectId;
// animated emoji
flat out int frames;
flat out int fps;
flat out float frameheight;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    int vert = gl_VertexID % 4;
    vec2 coord = corners[vert];
    vec4 col = round(texture(Sampler0, UV0) * 255);

    if (col.a == 251) { // screenspace
        effectId = int(col.b);
        gl_Position.xy = vec2(coord * 2 - 1) * vec2(1, -1);
        gl_Position.zw = vec2(-1, 1);
        vertexColor = Color;
        texCoord0 = vec2(UV0 - coord * 64 / 256);
    } else if (col.a == 253) { // normal/in-world effect
        effectId = int(col.b);
        vertexColor = Color;
        texCoord0 = UV0;
    } else {
        effectId = 0;
        vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
        texCoord0 = UV0;
    }

    // animated emoji
    frames = fps = 0;
    if (col.a == 252 && Position.z == 0) {
        frames = int(col.r);
        fps = int(col.g);
        frameheight = col.b;
    }
    //Hud
    if (make_hud()) {
        vertexColor = Color;
        sphericalVertexDistance = 0.0;
        cylindricalVertexDistance = 0.0;
        return;
    }
    //Hud
}
