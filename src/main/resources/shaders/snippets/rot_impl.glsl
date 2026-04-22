void main() {
    vec2 uv = mat2_rotate_z(vertexColor.a*4) * (centerUV / vec2(ratio, 1)) / (1.0001 - vertexColor.a) * 0.2 + 0.5;
    if (clamp(uv, vec2(0), vec2(1)) == uv) color = texture(Sampler0, texCoord0 + uv * 64 / 256);
    else color = vec4(vertexColor.rgb, 1);
}