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
            } else {
                throw new IllegalArgumentException("unknown option: " + arg);
            }
        }

        System.out.println("[*] === APUSYS ioctl probe ===");
        if (memNegative || devCtrl || memDmabuf || memIon || fdScan
                || ucmdNegative || hardwareBuffer || ucmdHardwareBuffer
                || runCmdHardwareBuffer) {
            System.out.println("[*] Mode: optional checks enabled;"
                + " no secure alloc/free, no run_cmd cmdbuf\n");
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

    private static void runOneUcmdHardwareBufferProbe(int apusysFd, String label,
                                                      int firstU32) throws Exception {
        System.out.println("\n[*] --- ucmd HardwareBuffer case: " + label
            + " first_u32=0x" + Integer.toHexString(firstU32) + " ---");

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
            fillImageHeader(input, firstU32);
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
            probeHardwareBufferParcel(apusysFd, hb, true, false, "hwb_" + label);
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

    private static void runOneRunCmdHardwareBufferProbe(int apusysFd, String label,
                                                        int firstU32) throws Exception {
        System.out.println("\n[*] --- run_cmd HardwareBuffer case: " + label
            + " first_u32=0x" + Integer.toHexString(firstU32) + " ---");

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
            fillImageHeader(input, firstU32);
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
        image.setTimestamp(System.nanoTime());
        System.out.println("[+] input image payload: first_u32=0x"
            + Integer.toHexString(firstU32)
            + " plane_count=" + planes.length
            + " cap=" + buffer.capacity()
            + " rowStride=" + planes[0].getRowStride()
            + " pixelStride=" + planes[0].getPixelStride());
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

    private static void probeHardwareBufferParcel(int apusysFd,
                                                  android.hardware.HardwareBuffer hb,
                                                  boolean runUcmd,
                                                  boolean runRunCmd,
                                                  String prefix)
            throws Exception {
        android.os.Parcel parcel = android.os.Parcel.obtain();
        try {
            hb.writeToParcel(parcel, 0);
            int size = parcel.dataSize();
            System.out.println("[*] HardwareBuffer parcel: dataSize=" + size
                + " hasFd=" + parcel.hasFileDescriptors()
                + " describe=0x" + Integer.toHexString(hb.describeContents()));

            tryReadAidlHardwareBuffer(apusysFd, parcel);
            bruteReadParcelFds(apusysFd, parcel, size, runUcmd, runRunCmd, prefix);
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
                                           boolean runRunCmd, String prefix)
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
                    runUcmdWithFd(apusysFd, "ucmd_" + prefix + "_c0_pos" + pos,
                        3, 0, fd, 0, 0x1000);
                    runUcmdWithFd(apusysFd, "ucmd_" + prefix + "_c1_pos" + pos,
                        3, 1, fd, 0, 0x1000);
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
