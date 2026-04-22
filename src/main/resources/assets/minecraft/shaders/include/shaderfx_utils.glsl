#define PI 3.14159265

mat2 mat2_rotate_z(float radians) {
    float s = sin(radians);
    float c = cos(radians);
    return mat2(
        c, -s,
        s,  c
    );
}

int hash(int x) {
    uint u = uint(x);
    u ^= u >> 16;
    u *= 0x85ebca6bu;
    u ^= u >> 13;
    u *= 0xc2b2ae35u;
    u ^= u >> 16;
    return int(u);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}


