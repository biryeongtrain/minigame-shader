package kim.biryeong.minigamelib.music;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Reads and checks Ogg framing without a native decoder or external executable. */
public final class OggVorbis {
    private OggVorbis() {}
    public static double duration(Path path) throws IOException {
        try (var input = new BufferedInputStream(Files.newInputStream(path))) { return duration(input); }
    }
    public static double duration(InputStream input) throws IOException {
        int serial = 0, sequence = 0, packets = 0, rate = 0;
        boolean first = true, ended = false, continued = false;
        long granule = -1;
        var header = new ByteArrayOutputStream();
        while (true) {
            byte[] prefix = input.readNBytes(27);
            if (prefix.length == 0) break;
            if (ended || prefix.length != 27 || !new String(prefix, 0, 4, StandardCharsets.US_ASCII).equals("OggS") || prefix[4] != 0)
                throw new IOException("Invalid/truncated Ogg page or chained streams");
            var b = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN);
            int flags = prefix[5] & 255, pageSerial = b.getInt(14), pageSequence = b.getInt(18), crc = b.getInt(22);
            if ((flags & ~7) != 0 || ((flags & 1) != 0) != continued) throw new IOException("Invalid Ogg continuation");
            if (first) { serial = pageSerial; if ((flags & 2) == 0 || pageSequence != 0) throw new IOException("Missing stream start"); }
            else if ((flags & 2) != 0) throw new IOException("Multiple logical streams are unsupported");
            if (pageSerial != serial || pageSequence != sequence++) throw new IOException("Missing page or multiplexed streams");
            int count = prefix[26] & 255;
            byte[] lacing = input.readNBytes(count);
            if (lacing.length != count) throw new IOException("Truncated page table");
            int size = 0; for (byte n : lacing) size += n & 255;
            byte[] payload = input.readNBytes(size);
            if (payload.length != size) throw new IOException("Truncated page payload");
            Arrays.fill(prefix, 22, 26, (byte) 0);
            int computed = crc(crc(crc(0, prefix), lacing), payload);
            if (computed != crc) throw new IOException("Ogg checksum mismatch");
            int offset = 0;
            for (byte value : lacing) {
                int n = value & 255;
                if (packets < 3) {
                    if (header.size() + n > 16 * 1024 * 1024) throw new IOException("Vorbis header too large");
                    header.write(payload, offset, n);
                }
                offset += n; continued = n == 255;
                if (!continued) {
                    if (packets < 3) {
                        byte[] h = header.toByteArray();
                        if (h.length < 7 || h[0] != (packets * 2 + 1) || !new String(h, 1, 6, StandardCharsets.US_ASCII).equals("vorbis"))
                            throw new IOException("Expected Ogg Vorbis identification/comment/setup header");
                        if (packets == 0) {
                            if (h.length != 30) throw new IOException("Invalid Vorbis identification header");
                            var info = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
                            rate = info.getInt(12);
                            int small = h[28] & 15, large = (h[28] & 255) >>> 4;
                            if (info.getInt(7) != 0 || h[11] == 0 || rate <= 0 || small < 6 || large > 13 || small > large || h[29] != 1)
                                throw new IOException("Unsupported Vorbis version or invalid audio parameters");
                        }
                        header.reset();
                    }
                    packets++;
                }
            }
            long end = b.getLong(6);
            if (end >= 0) { if (end < granule) throw new IOException("Decreasing granule position"); granule = end; }
            ended = (flags & 4) != 0;
            first = false;
        }
        if (!ended || continued || packets < 4 || granule <= 0 || rate <= 0) throw new IOException("Incomplete or empty Vorbis stream");
        return (double) granule / rate;
    }
    private static int crc(int crc, byte[] bytes) {
        for (byte b : bytes) {
            crc ^= (b & 255) << 24;
            for (int i = 0; i < 8; i++) crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
        }
        return crc;
    }
}
