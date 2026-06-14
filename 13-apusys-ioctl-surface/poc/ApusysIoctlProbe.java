/**
 * APUSYS ioctl reachability probe.
 *
 * Build this class together with DrmTrigger.java so it can reuse the pure-Java
 * syscall helper from the system_app dalvikvm context:
 *   javac -source 8 -target 8 -cp $ANDROID_JAR -d /tmp/apusys-build \
 *     07-cve-2023-32836-display-overflow/poc/DrmTrigger.java \
 *     13-apusys-ioctl-surface/poc/ApusysIoctlProbe.java
 */
public final class ApusysIoctlProbe {
    private static final String APUSYS_DEV = "/dev/apusys";

    private static final long APUSYS_CMD_HANDSHAKE   = 0xC0284100L;
    private static final long APUSYS_CMD_MEM_FREE_02 = 0xC0384102L;
    private static final long APUSYS_CMD_MEM_CREATE2 = 0xC0384103L;
    private static final long APUSYS_CMD_MEM_CREATE3 = 0xC038410FL;
    private static final long APUSYS_CMD_MEM_FREE_10 = 0xC0384110L;
    private static final long APUSYS_CMD_RUN_SYNC    = 0x40184106L;
    private static final long APUSYS_CMD_RUN_ASYNC   = 0xC0184107L;
    private static final long APUSYS_CMD_DEV_CTRL    = 0x400C4109L;
    private static final long APUSYS_CMD_UCMD        = 0x4014410EL;
    private static final long APUSYS_CMD_DISABLED_0C = 0x4038410CL;
    private static final long APUSYS_CMD_DISABLED_0D = 0x4038410DL;
    private static final long APUSYS_CMD_UNKNOWN     = 0x41414141L;

    private static final int OFF_HANDSHAKE = 0x100;
    private static final int OFF_RUN_CMD   = 0x140;
    private static final int OFF_UCMD      = 0x180;
    private static final int OFF_MEM_A     = 0x1c0;
    private static final int OFF_MEM_B     = 0x220;
    private static final int OFF_DEV_CTRL  = 0x280;

    private ApusysIoctlProbe() {
    }

    public static void main(String[] args) throws Exception {
        boolean query = false;
        boolean memNegative = false;
        boolean devCtrl = false;
        for (String arg : args) {
            if ("--query".equals(arg)) {
                query = true;
            } else if ("--mem-negative".equals(arg)) {
                memNegative = true;
            } else if ("--dev-ctrl".equals(arg)) {
                devCtrl = true;
            } else {
                throw new IllegalArgumentException("unknown option: " + arg);
            }
        }

        System.out.println("[*] === APUSYS ioctl probe ===");
        if (memNegative || devCtrl) {
            System.out.println("[*] Mode: optional checks enabled;"
                + " no secure alloc/free, no valid cmdbuf\n");
        } else {
            System.out.println("[*] Mode: reject/query paths only;"
                + " no memory create/free, no dev-ctrl, no valid cmdbuf\n");
        }

        DrmTrigger.initSyscall();

        int fd = -1;
        try {
            fd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("[+] Opened " + APUSYS_DEV + " fd=" + fd);

            ioctlAndPrint(fd, "unknown", APUSYS_CMD_UNKNOWN, 0);
            ioctlAndPrint(fd, "disabled_0c", APUSYS_CMD_DISABLED_0C, 0);
            ioctlAndPrint(fd, "disabled_0d", APUSYS_CMD_DISABLED_0D, 0);

            long handshake = DrmTrigger.sScratchBuf + OFF_HANDSHAKE;
            DrmTrigger.zeroMem(handshake, 0x28);
            ioctlAndPrint(fd, "handshake_reject", APUSYS_CMD_HANDSHAKE, handshake);

            long runCmd = DrmTrigger.sScratchBuf + OFF_RUN_CMD;
            DrmTrigger.zeroMem(runCmd, 0x18);
            DrmTrigger.unsafePutInt(runCmd + 0x0c, 1);
            ioctlAndPrint(fd, "run_async_reject", APUSYS_CMD_RUN_ASYNC, runCmd);
            ioctlAndPrint(fd, "run_sync_reject", APUSYS_CMD_RUN_SYNC, runCmd);

            long ucmd = DrmTrigger.sScratchBuf + OFF_UCMD;
            DrmTrigger.zeroMem(ucmd, 0x14);
            DrmTrigger.unsafePutInt(ucmd + 0x0c, 1);
            ioctlAndPrint(fd, "ucmd_reject", APUSYS_CMD_UCMD, ucmd);

            if (query) {
                DrmTrigger.zeroMem(handshake, 0x28);
                DrmTrigger.unsafePutInt(handshake + 0x0c, 1);
                long ret = ioctlAndPrint(fd, "handshake_query", APUSYS_CMD_HANDSHAKE, handshake);
                if (ret >= 0) {
                    dumpU32Words("handshake", handshake, 0x28);
                }
            }

            if (memNegative) {
                runMemNegative(fd);
            }

            if (devCtrl) {
                runDevCtrlProbe(fd);
            }

            System.out.println("\n[+] Probe completed. Interpret results as handler reachability only.");
        } finally {
            if (fd >= 0) {
                DrmTrigger.closeFd(fd);
            }
        }
    }

    private static void runMemNegative(int fd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS memory negative tests ===");
        System.out.println("[*] Mode: NULL user pointer and bad fd/zero-size only; no valid dmabuf");

        ioctlAndPrint(fd, "mem_create2_null", APUSYS_CMD_MEM_CREATE2, 0);
        ioctlAndPrint(fd, "mem_create3_null", APUSYS_CMD_MEM_CREATE3, 0);

        long memA = DrmTrigger.sScratchBuf + OFF_MEM_A;
        DrmTrigger.zeroMem(memA, 0x38);
        long ret = ioctlAndPrint(fd, "mem_create2_zero", APUSYS_CMD_MEM_CREATE2, memA);
        cleanupUnexpectedMemSuccess(fd, "mem_create2", APUSYS_CMD_MEM_FREE_02, memA, ret);
        DrmTrigger.zeroMem(memA, 0x38);
        DrmTrigger.unsafePutInt(memA + 0x20, -1);
        ret = ioctlAndPrint(fd, "mem_create2_badfd", APUSYS_CMD_MEM_CREATE2, memA);
        cleanupUnexpectedMemSuccess(fd, "mem_create2", APUSYS_CMD_MEM_FREE_02, memA, ret);

        long memB = DrmTrigger.sScratchBuf + OFF_MEM_B;
        DrmTrigger.zeroMem(memB, 0x38);
        ret = ioctlAndPrint(fd, "mem_create3_zero", APUSYS_CMD_MEM_CREATE3, memB);
        cleanupUnexpectedMemSuccess(fd, "mem_create3", APUSYS_CMD_MEM_FREE_10, memB, ret);
        DrmTrigger.zeroMem(memB, 0x38);
        DrmTrigger.unsafePutInt(memB + 0x20, -1);
        ret = ioctlAndPrint(fd, "mem_create3_badfd", APUSYS_CMD_MEM_CREATE3, memB);
        cleanupUnexpectedMemSuccess(fd, "mem_create3", APUSYS_CMD_MEM_FREE_10, memB, ret);
    }

    private static void runDevCtrlProbe(int fd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS device-control tests ===");
        System.out.println("[*] Mode: ioctl 0x400c4109 with known provider ids,"
            + " core 0/1, control value 0");

        probeDevCtrl(fd, "mdla", 0x02, 0, 0);
        probeDevCtrl(fd, "mdla", 0x02, 1, 0);
        probeDevCtrl(fd, "vpu", 0x03, 0, 0);
        probeDevCtrl(fd, "vpu", 0x03, 1, 0);
        probeDevCtrl(fd, "edma", 0x04, 0, 0);
        probeDevCtrl(fd, "edma", 0x04, 1, 0);
        probeDevCtrl(fd, "mdla_rt", 0x22, 0, 0);
        probeDevCtrl(fd, "mdla_rt", 0x22, 1, 0);
        probeDevCtrl(fd, "vpu_rt", 0x23, 0, 0);
        probeDevCtrl(fd, "vpu_rt", 0x23, 1, 0);
    }

    private static long probeDevCtrl(int fd, String provider, int devId,
                                     int coreId, int control) throws Exception {
        long devCtrl = DrmTrigger.sScratchBuf + OFF_DEV_CTRL;
        DrmTrigger.zeroMem(devCtrl, 0x0c);
        DrmTrigger.unsafePutInt(devCtrl + 0x00, devId);
        DrmTrigger.unsafePutInt(devCtrl + 0x04, coreId);
        DrmTrigger.unsafePutInt(devCtrl + 0x08, control);

        String name = "devctl_" + provider + "_c" + coreId;
        return ioctlAndPrint(fd, name, APUSYS_CMD_DEV_CTRL, devCtrl);
    }

    private static void cleanupUnexpectedMemSuccess(int fd, String name, long freeCmd,
                                                    long mem, long ret) throws Exception {
        if (ret < 0) {
            return;
        }

        long id = DrmTrigger.unsafeGetLong(mem + 0x28);
        System.out.println("[!] " + name + " unexpectedly succeeded; id=0x"
            + Long.toHexString(id) + ", attempting cleanup");
        ioctlAndPrint(fd, name + "_cleanup", freeCmd, mem);
    }

    private static long ioctlAndPrint(int fd, String name, long cmd, long arg)
            throws Exception {
        long ret = DrmTrigger.rawIoctl(fd, cmd, arg);
        System.out.println("[*] " + pad(name, 18)
            + " cmd=0x" + Long.toHexString(cmd)
            + " ret=" + retText(ret));
        return ret;
    }

    private static void dumpU32Words(String name, long addr, int len)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(name).append(":");
        for (int off = 0; off < len; off += 4) {
            sb.append(" [").append(off).append("]=0x")
              .append(Integer.toHexString(DrmTrigger.unsafeGetInt(addr + off)));
        }
        System.out.println(sb.toString());
    }

    private static String retText(long ret) {
        if (ret >= 0) {
            return Long.toString(ret);
        }
        return ret + " (" + errnoName(-ret) + ")";
    }

    private static String errnoName(long errno) {
        if (errno == 1) return "EPERM";
        if (errno == 2) return "ENOENT";
        if (errno == 5) return "EIO";
        if (errno == 9) return "EBADF";
        if (errno == 12) return "ENOMEM";
        if (errno == 13) return "EACCES";
        if (errno == 14) return "EFAULT";
        if (errno == 16) return "EBUSY";
        if (errno == 19) return "ENODEV";
        if (errno == 22) return "EINVAL";
        if (errno == 25) return "ENOTTY";
        if (errno == 75) return "EOVERFLOW";
        if (errno == 95) return "EOPNOTSUPP";
        if (errno == 110) return "ETIMEDOUT";
        return "errno=" + errno;
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
