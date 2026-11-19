"""Doc thang binary AndroidManifest.xml (AXML) de liet ke DUNG node uses-permission.
Khong can aapt2. Tranh viec doan tu chuoi trong string pool.
"""
import struct
import sys
import zipfile

UTF8_FLAG = 1 << 8


def read_string_pool(buf, off):
    typ, hsize, csize = struct.unpack_from("<HHI", buf, off)
    assert typ == 0x0001, hex(typ)
    count, style_count, flags, strings_start, styles_start = struct.unpack_from("<IIIII", buf, off + 8)
    offsets = struct.unpack_from("<%dI" % count, buf, off + 28)
    base = off + strings_start
    utf8 = bool(flags & UTF8_FLAG)
    out = []
    for o in offsets:
        p = base + o
        if utf8:
            n = buf[p]
            p += 2 if n & 0x80 else 1
            ln = buf[p]
            if ln & 0x80:
                ln = ((ln & 0x7F) << 8) | buf[p + 1]
                p += 2
            else:
                p += 1
            out.append(buf[p:p + ln].decode("utf-8", errors="replace"))
        else:
            ln = struct.unpack_from("<H", buf, p)[0]
            p += 2
            if ln & 0x8000:
                ln = ((ln & 0x7FFF) << 16) | struct.unpack_from("<H", buf, p)[0]
                p += 2
            out.append(buf[p:p + ln * 2].decode("utf-16-le", errors="replace"))
    return out, csize


def walk(buf):
    strings, pool_size = read_string_pool(buf, 8)
    off = 8 + pool_size
    total = struct.unpack_from("<I", buf, 4)[0]
    while off < total:
        typ, hsize, csize = struct.unpack_from("<HHI", buf, off)
        if csize == 0:
            break
        if typ == 0x0102:  # START_TAG
            name_idx = struct.unpack_from("<I", buf, off + 20)[0]
            tag = strings[name_idx]
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", buf, off + 24)
            attrs = {}
            for i in range(attr_count):
                a = off + 16 + attr_start + i * attr_size
                ns_i, nm_i, raw_i = struct.unpack_from("<III", buf, a)
                dtype, data = struct.unpack_from("<BI", buf, a + 15)
                key = strings[nm_i]
                if raw_i != 0xFFFFFFFF:
                    attrs[key] = strings[raw_i]
                elif dtype == 0x12:
                    attrs[key] = "true" if data else "false"
                elif dtype == 0x10:
                    attrs[key] = str(struct.unpack("<i", struct.pack("<I", data))[0])
                else:
                    attrs[key] = "0x%08x" % data
            yield tag, attrs
        off += csize


apk = sys.argv[1]
with zipfile.ZipFile(apk) as z:
    buf = z.read("AndroidManifest.xml")

print("=== uses-permission (quyen app XIN) ===")
requested = []
for tag, attrs in walk(buf):
    if tag == "uses-permission":
        requested.append(attrs.get("name", "?"))
        print("   ", attrs.get("name"))
if not requested:
    print("    (khong co)")

print("=== service / activity / application ===")
for tag, attrs in walk(buf):
    if tag in ("service", "activity", "application"):
        keep = {k: v for k, v in attrs.items()
                if k in ("name", "permission", "exported", "allowBackup", "label", "debuggable")}
        print("   ", tag, keep)

print()
print("INTERNET duoc xin:", "android.permission.INTERNET" in requested)
print("Tong so quyen xin:", len(requested))
