/**
 * Reject-only APUSYS ioctl reachability probe.
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
    private static final long APUSYS_CMD_RUN_SYNC    = 0x40184106L;
    private static final long APUSYS_CMD_RUN_ASYNC   = 0xC0184107L;
    private static final long APUSYS_CMD_UCMD        = 0x4014410EL;
    private static final long APUSYS_CMD_DISABLED_0C = 0x4038410CL;
    private static final long APUSYS_CMD_DISABLED_0D = 0x4038410DL;
    private static final long APUSYS_CMD_UNKNOWN     = 0x41414141L;

    private static final int OFF_HANDSHAKE = 0x100;
    private static final int OFF_RUN_CMD   = 0x140;
    private static final int OFF_UCMD      = 0x180;

    private ApusysIoctlProbe() {
    }

    public static void main(String[] args) throws Exception {
        boolean query = args.length > 0 && "--query".equals(args[0]);

        System.out.println("[*] === APUSYS ioctl reject-only probe ===");
        System.out.println("[*] Mode: no memory create/free, no secure alloc/free, no valid cmdbuf\n");

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

            System.out.println("\n[+] Probe completed. Interpret results as handler reachability only.");
        } finally {
            if (fd >= 0) {
                DrmTrigger.closeFd(fd);
            }
        }
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
        if (errno == 22) return "EINVAL";
        if (errno == 25) return "ENOTTY";
        if (errno == 75) return "EOVERFLOW";
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
