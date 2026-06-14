import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * APUNN/XRP wrapper request inspector for app_process64.
 *
 * This uses DrmTrigger's ART native-call primitive to call functions already
 * mapped from /system. It does not load or execute native code from app data
 * and it does not call XRP_RunCommand().
 */
public final class XrpWrapperInspect {
    private static final String[] NEURON_LIB_PATHS = {
        "/system/lib64/libneuron_platform.vpu.so",
        "/vendor/lib64/mt6893/libneuron_platform.vpu.so",
        "/vendor/lib64/libneuron_platform.vpu.so",
        "/system/vendor/lib64/mt6893/libneuron_platform.vpu.so",
        "/system/vendor/lib64/libneuron_platform.vpu.so",
    };
    private static final String[] APUWARE_LIB_PATHS = {
        "/system/system_ext/lib64/libapuwarexrp_v2.mtk.so",
        "/system_ext/lib64/libapuwarexrp_v2.mtk.so",
        "/vendor/lib64/libapuwarexrp_v2.mtk.so",
        "/system/system_ext/lib64/libapuwarexrp.mtk.so",
        "/system_ext/lib64/libapuwarexrp.mtk.so",
    };
    private static final String[] APUSYS_LIB_PATHS = {
        "/system/lib64/libapu_mdw.so",
        "/system/vendor/lib64/libapu_mdw.so",
        "/vendor/lib64/libapu_mdw.so",
    };
    private static final String[] VPU_LIB_PATHS = {
        "/system/lib64/libvpu5.so",
        "/system/vendor/lib64/libvpu5.so",
        "/vendor/lib64/libvpu5.so",
    };
    private static final String LIBDL_PATH = "/apex/com.android.runtime/lib64/bionic/libdl.so";
    private static final int RTLD_NOW_GLOBAL = 0x102;

    private static final int PROT_READ = DrmTrigger.PROT_READ;
    private static final int PROT_WRITE = DrmTrigger.PROT_WRITE;
    private static final int MAP_PRIVATE = DrmTrigger.MAP_PRIVATE;
    private static final int MAP_ANONYMOUS = DrmTrigger.MAP_ANONYMOUS;

    private static final int BUF_INFO_SIZE = 0x30;
    private static final int VPU_REQ_INFO_SIZE = 0x10;
    private static final int CODE_SIZE = 0x1c8;
    private static final int OUTPUT_SIZE = 0x80;
    private static final int ANN_VERSION_OPCODE = 10003;

    private XrpWrapperInspect() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("[*] === XRP wrapper request inspector ===");
        printContext();

        boolean apuwareMode = args.length > 0 && "apuware".equals(args[0]);
        boolean createApusysSession = !apuwareMode;
        for (String arg : args) {
            if ("--no-create-apusys-session".equals(arg)) {
                createApusysSession = false;
            } else if ("--create-apusys-session".equals(arg)) {
                createApusysSession = true;
            }
        }
        String libKind = apuwareMode ? "apuware" : "neuron";
        String libPath = loadWrapper(apuwareMode ? APUWARE_LIB_PATHS : NEURON_LIB_PATHS);
        System.out.println("[+] mode=" + libKind);
        System.out.println("[+] loaded=" + libPath);
        System.out.println("[+] create_apusys_session=" + createApusysSession);

        String libcPath = "/apex/com.android.runtime/lib64/bionic/libc.so";
        long libcGetpid = resolve(libcPath, "getpid");
        DrmTrigger.initNativeCall();
        long pid = call(libcGetpid, 0, 0, 0, 0, 0);
        System.out.println("native getpid()=" + pid);

        long xrpCreate = resolve(libPath, "XRP_Create");
        long xrpRelease = resolve(libPath, "XRP_Release");
        long xrpCreateCommand = resolve(libPath, "XRP_CreateCommand");
        long xrpAllocateBuffer = resolve(libPath, "XRP_AllocateBuffer");
        long xrpUseInputBuffer = resolve(libPath, "XRP_UseInputBuffer");
        long xrpUseOutputBuffer = resolve(libPath, "XRP_UseOutputBuffer");
        long xrpFinalizeCommand = resolve(libPath, "XRP_FinalizeCommand");
        long xrpGetPreparedRequests = resolve(libPath, "XRP_GetPreparedRequests");

        long mem = DrmTrigger.osMmap(0, 0x4000, PROT_READ | PROT_WRITE,
            MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        System.out.println("[+] inspect_mem=0x" + Long.toHexString(mem));
        clear(mem, 0x4000);

        String apusysLibPath = null;
        long apusysSessionCreate = 0;
        long apusysSessionDelete = 0;
        if (createApusysSession) {
            apusysLibPath = systemLoadFirst(APUSYS_LIB_PATHS, "apusys");
            if (apusysLibPath == null) {
                systemLoadFirst(VPU_LIB_PATHS, "vpu5");
                apusysLibPath = findMappedLibrary(APUSYS_LIB_PATHS);
                if (apusysLibPath != null) {
                    System.out.println("[+] apusys_mapped=" + apusysLibPath);
                }
            }
            long dlopen = resolve(LIBDL_PATH, "dlopen");
            if (apusysLibPath == null) {
                apusysLibPath = nativeDlopenFirst(APUSYS_LIB_PATHS, dlopen, mem + 0x3000);
            }
            if (apusysLibPath == null) {
                System.out.println("APUSYS native dlopen failed for all candidate paths.");
            } else {
                System.out.println("[+] apusys_resolved=" + apusysLibPath);
                apusysSessionCreate = resolve(apusysLibPath, "apusysSession_createInstance");
                apusysSessionDelete = resolve(apusysLibPath, "apusysSession_deleteInstance");
            }
        }

        long options = mem;
        long devicePtr = mem + 0x40;
        long codeInfo = mem + 0x80;
        long outInfo = mem + 0xc0;
        long requests = mem + 0x100;
        DrmTrigger.unsafePutInt(options, 0x18);
        initEmptyBufInfo(codeInfo);
        initEmptyBufInfo(outInfo);
        long apusysSession = 0;
        if (apusysSessionCreate != 0) {
            apusysSession = call(apusysSessionCreate, 0, 0, 0, 0, 0);
            System.out.println("apusysSession_createInstance session=0x"
                + Long.toHexString(apusysSession));
            DrmTrigger.unsafePutLong(options + 0x10, apusysSession);
        }

        long st = call(xrpCreate, options, devicePtr, 0, 0, 0);
        long device = DrmTrigger.unsafeGetLong(devicePtr);
        System.out.println("XRP_Create status=" + u32(st)
            + " device=0x" + Long.toHexString(device));
        if (u32(st) != 0 || device == 0) {
            System.out.println("XRP_Create did not initialize; stop.");
            if (apusysSession != 0 && apusysSessionDelete != 0) {
                long delSt = call(apusysSessionDelete, apusysSession, 0, 0, 0, 0);
                System.out.println("apusysSession_deleteInstance status=" + u32(delSt));
            }
            return;
        }

        long handle = 1;
        st = call(xrpCreateCommand, device, handle, 0, 0, 0);
        System.out.println("XRP_CreateCommand status=" + u32(st));
        if (u32(st) != 0) {
            call(xrpRelease, devicePtr, 0, 0, 0, 0);
            return;
        }

        st = call(xrpAllocateBuffer, device, CODE_SIZE, codeInfo, 0, 0);
        System.out.println("XRP_AllocateBuffer(code) status=" + u32(st));
        dumpBufInfo("code_info", codeInfo);
        long codeVa = hostVaFromBufInfo(codeInfo);
        System.out.println("code_host_va=0x" + Long.toHexString(codeVa));
        if (u32(st) == 0 && codeVa != 0) {
            fillAnnVersionCode(codeVa);
            dumpHex("code_host_after_fill", codeVa, 0x80);
        }

        st = call(xrpAllocateBuffer, device, OUTPUT_SIZE, outInfo, 0, 0);
        System.out.println("XRP_AllocateBuffer(output) status=" + u32(st));
        dumpBufInfo("out_info", outInfo);
        long outVa = hostVaFromBufInfo(outInfo);
        System.out.println("out_host_va=0x" + Long.toHexString(outVa));
        if (u32(st) == 0 && outVa != 0) {
            fillByte(outVa, OUTPUT_SIZE, (byte) 0xa5);
            dumpHex("out_host_after_fill", outVa, 0x40);
        }

        st = call(xrpUseInputBuffer, device, handle, codeInfo, 0, 0);
        System.out.println("XRP_UseInputBuffer status=" + u32(st));
        st = call(xrpUseOutputBuffer, device, handle, outInfo, 0, 0);
        System.out.println("XRP_UseOutputBuffer status=" + u32(st));

        st = call(xrpFinalizeCommand, device, handle, outInfo, 1, 0);
        System.out.println("XRP_FinalizeCommand status=" + u32(st));

        st = call(xrpGetPreparedRequests, device, handle, requests, 4, 0);
        System.out.println("XRP_GetPreparedRequests status=" + u32(st));
        for (int i = 0; i < 4; i++) {
            long r = requests + (long) i * VPU_REQ_INFO_SIZE;
            System.out.println("request[" + i + "]: u32_0=0x"
                + hex32(DrmTrigger.unsafeGetInt(r))
                + " u32_4=0x" + hex32(DrmTrigger.unsafeGetInt(r + 4))
                + " ptr=0x" + Long.toHexString(DrmTrigger.unsafeGetLong(r + 8)));
        }
        dumpHex("requests_raw", requests, VPU_REQ_INFO_SIZE * 4);

        long reqPtr = DrmTrigger.unsafeGetLong(requests + 8);
        if (u32(st) == 0 && looksLikeUserVa(reqPtr)) {
            dumpHex("request0_blob", reqPtr, 0x180);
            dumpWords("request0_head", reqPtr, 0x80);
            System.out.println("request0_algo=" + readCString(reqPtr + 4, 0x40));
        }

        if (outVa != 0) {
            dumpHex("out_host_final", outVa, OUTPUT_SIZE);
        }

        call(xrpRelease, devicePtr, 0, 0, 0, 0);
        if (apusysSession != 0 && apusysSessionDelete != 0) {
            long delSt = call(apusysSessionDelete, apusysSession, 0, 0, 0, 0);
            System.out.println("apusysSession_deleteInstance status=" + u32(delSt));
        }
    }

    private static long call(long fn, long a0, long a1, long a2,
                             long a3, long a4) throws Exception {
        System.out.println("    call begin fn=0x" + Long.toHexString(fn)
            + " a0=0x" + Long.toHexString(a0)
            + " a1=0x" + Long.toHexString(a1)
            + " a2=0x" + Long.toHexString(a2)
            + " a3=0x" + Long.toHexString(a3)
            + " a4=0x" + Long.toHexString(a4));
        long ret = DrmTrigger.callFunction(fn, a0, a1, a2, a3, a4);
        System.out.println("    call fn=0x" + Long.toHexString(fn)
            + " ret=0x" + Long.toHexString(ret));
        return ret;
    }

    private static void initEmptyBufInfo(long addr) throws Exception {
        clear(addr, BUF_INFO_SIZE);
        DrmTrigger.unsafePutInt(addr + 0x18, -1);
    }

    private static void printContext() {
        try {
            System.out.println("uid=" + android.os.Process.myUid()
                + " pid=" + android.os.Process.myPid());
            System.out.println("context="
                + new String(Files.readAllBytes(Paths.get("/proc/self/attr/current")),
                    StandardCharsets.UTF_8).trim());
        } catch (Throwable t) {
            System.out.println("context_error=" + t);
        }
    }

    private static String loadWrapper(String[] paths) {
        Throwable last = null;
        for (String path : paths) {
            try {
                System.load(path);
                return path;
            } catch (Throwable t) {
                last = t;
                System.out.println("System.load failed path=" + path + " error=" + t);
            }
        }
        throw new RuntimeException("could not load libneuron wrapper", last);
    }

    private static String systemLoadFirst(String[] paths, String label) {
        for (String path : paths) {
            try {
                System.load(path);
                System.out.println("[+] System.load " + label + "=" + path);
                return path;
            } catch (Throwable t) {
                System.out.println("System.load failed " + label + "=" + path
                    + " error=" + t);
            }
        }
        return null;
    }

    private static String nativeDlopenFirst(String[] paths, long dlopen,
                                            long pathBuf) throws Exception {
        for (String path : paths) {
            writeCString(pathBuf, path);
            long handle = call(dlopen, pathBuf, RTLD_NOW_GLOBAL, 0, 0, 0);
            System.out.println("native dlopen path=" + path
                + " handle=0x" + Long.toHexString(handle));
            if (handle != 0) {
                return path;
            }
        }
        return null;
    }

    private static String findMappedLibrary(String[] paths) throws Exception {
        for (String path : paths) {
            if (isMapped(path)) {
                return path;
            }
        }
        return null;
    }

    private static long resolve(String libPath, String symbol) throws Exception {
        long bias = findLoadBias(libPath);
        long value = findElfSymbolValue(libPath, symbol);
        long addr = bias + value;
        System.out.println("symbol " + symbol + " value=0x" + Long.toHexString(value)
            + " bias=0x" + Long.toHexString(bias)
            + " addr=0x" + Long.toHexString(addr));
        return addr;
    }

    private static boolean isMapped(String libPath) throws Exception {
        String canonical = new File(libPath).getCanonicalPath();
        String name = new File(libPath).getName();
        List<String> lines = Files.readAllLines(Paths.get("/proc/self/maps"),
            StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.contains(libPath) || line.contains(canonical)
                || line.endsWith("/" + name)) {
                return true;
            }
        }
        return false;
    }

    private static long findLoadBias(String libPath) throws Exception {
        String canonical = new File(libPath).getCanonicalPath();
        String name = new File(libPath).getName();
        List<String> lines = Files.readAllLines(Paths.get("/proc/self/maps"),
            StandardCharsets.UTF_8);
        long best = Long.MAX_VALUE;
        for (String line : lines) {
            if (!line.contains(libPath) && !line.contains(canonical)
                && !line.endsWith("/" + name)) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 5) continue;
            String[] range = parts[0].split("-");
            long start = Long.parseUnsignedLong(range[0], 16);
            long offset = Long.parseUnsignedLong(parts[2], 16);
            long bias = start - offset;
            if (Long.compareUnsigned(bias, best) < 0) {
                best = bias;
            }
        }
        if (best == Long.MAX_VALUE) {
            throw new IllegalStateException("mapping not found for " + libPath);
        }
        return best;
    }

    private static long findElfSymbolValue(String path, String symbol) throws Exception {
        byte[] b = readFile(path);
        if (b.length < 0x40 || b[0] != 0x7f || b[1] != 'E' || b[2] != 'L'
            || b[3] != 'F' || b[4] != 2 || b[5] != 1) {
            throw new IllegalArgumentException("not ELF64 little-endian: " + path);
        }
        long shoff = u64(b, 0x28);
        int shentsize = u16(b, 0x3a);
        int shnum = u16(b, 0x3c);
        for (int i = 0; i < shnum; i++) {
            int sh = (int) shoff + i * shentsize;
            int type = (int) u32(b, sh + 4);
            if (type != 2 && type != 11) {
                continue;
            }
            int link = (int) u32(b, sh + 40);
            if (link < 0 || link >= shnum) {
                continue;
            }
            int strSh = (int) shoff + link * shentsize;
            long symOff = u64(b, sh + 24);
            long symSize = u64(b, sh + 32);
            long entSize = u64(b, sh + 56);
            long strOff = u64(b, strSh + 24);
            if (entSize == 0) entSize = 24;
            for (long off = symOff; off + 24 <= symOff + symSize; off += entSize) {
                int nameOff = (int) u32(b, (int) off);
                String name = cstring(b, (int) strOff + nameOff);
                if (symbol.equals(name)) {
                    return u64(b, (int) off + 8);
                }
            }
        }
        throw new IllegalArgumentException("symbol not found: " + symbol);
    }

    private static byte[] readFile(String path) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream in = new FileInputStream(path);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private static void fillAnnVersionCode(long code) throws Exception {
        clear(code, CODE_SIZE);
        DrmTrigger.unsafePutInt(code + 0x00, ANN_VERSION_OPCODE);
        DrmTrigger.unsafePutInt(code + 0x04, CODE_SIZE);
        DrmTrigger.unsafePutInt(code + 0x08, 0);
        DrmTrigger.unsafePutInt(code + 0x0c, 0);
        DrmTrigger.unsafePutInt(code + 0x10, 1);
        DrmTrigger.unsafePutInt(code + 0x48, 0);
    }

    private static void dumpBufInfo(String label, long addr) throws Exception {
        StringBuilder raw = new StringBuilder(label).append(" raw:");
        for (int off = 0; off < BUF_INFO_SIZE; off += 4) {
            raw.append(" +0x").append(hex2(off)).append("=0x")
                .append(hex32(DrmTrigger.unsafeGetInt(addr + off)));
        }
        System.out.println(raw.toString());
        System.out.println(label + " qwords: +0x08=0x"
            + Long.toHexString(DrmTrigger.unsafeGetLong(addr + 0x08))
            + " +0x20=0x" + Long.toHexString(DrmTrigger.unsafeGetLong(addr + 0x20))
            + " +0x28=0x" + Long.toHexString(DrmTrigger.unsafeGetLong(addr + 0x28)));
    }

    private static long hostVaFromBufInfo(long addr) throws Exception {
        long candidate28 = DrmTrigger.unsafeGetLong(addr + 0x28);
        long candidate20 = DrmTrigger.unsafeGetLong(addr + 0x20);
        if (looksLikeUserVa(candidate28)) return candidate28;
        if (looksLikeUserVa(candidate20)) return candidate20;
        return 0;
    }

    private static boolean looksLikeUserVa(long v) {
        return Long.compareUnsigned(v, 0x1000000000L) > 0
            && Long.compareUnsigned(v, 0x0100000000000000L) < 0;
    }

    private static void clear(long addr, int size) throws Exception {
        for (int off = 0; off < size; off += 8) {
            DrmTrigger.unsafePutLong(addr + off, 0);
        }
    }

    private static void fillByte(long addr, int size, byte value) throws Exception {
        for (int off = 0; off < size; off++) {
            DrmTrigger.unsafePutByte(addr + off, value);
        }
    }

    private static void writeCString(long addr, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            DrmTrigger.unsafePutByte(addr + i, bytes[i]);
        }
        DrmTrigger.unsafePutByte(addr + bytes.length, (byte) 0);
    }

    private static void dumpHex(String label, long addr, int size) throws Exception {
        System.out.println(label + " size=0x" + Integer.toHexString(size));
        int n = Math.min(size, 0x180);
        for (int off = 0; off < n; off += 16) {
            StringBuilder sb = new StringBuilder();
            sb.append("  ").append(hex4(off)).append(":");
            for (int i = 0; i < 16 && off + i < n; i++) {
                int v = DrmTrigger.unsafeGetByte(addr + off + i) & 0xff;
                sb.append(' ').append(hex2(v));
            }
            System.out.println(sb.toString());
        }
    }

    private static void dumpWords(String label, long addr, int size) throws Exception {
        StringBuilder sb = new StringBuilder(label).append(" words:");
        for (int off = 0; off + 4 <= size; off += 4) {
            sb.append(" +0x").append(hex2(off)).append("=0x")
                .append(hex32(DrmTrigger.unsafeGetInt(addr + off)));
        }
        System.out.println(sb.toString());
    }

    private static String readCString(long addr, int max) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            int b = DrmTrigger.unsafeGetByte(addr + i) & 0xff;
            if (b == 0) break;
            if (b >= 0x20 && b < 0x7f) {
                sb.append((char) b);
            } else {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    private static long u32(long v) {
        return v & 0xffffffffL;
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static long u32(byte[] b, int off) {
        return ((long) b[off] & 0xff)
            | (((long) b[off + 1] & 0xff) << 8)
            | (((long) b[off + 2] & 0xff) << 16)
            | (((long) b[off + 3] & 0xff) << 24);
    }

    private static long u64(byte[] b, int off) {
        return u32(b, off) | (u32(b, off + 4) << 32);
    }

    private static String cstring(byte[] b, int off) {
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < b.length && b[i] != 0; i++) {
            sb.append((char) (b[i] & 0xff));
        }
        return sb.toString();
    }

    private static String hex32(int v) {
        return String.format("%08x", v);
    }

    private static String hex4(int v) {
        return String.format("%04x", v);
    }

    private static String hex2(int v) {
        return String.format("%02x", v & 0xff);
    }
}
