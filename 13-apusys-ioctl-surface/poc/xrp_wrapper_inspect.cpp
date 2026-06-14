#include <dlfcn.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vector>

namespace {

constexpr size_t kBufInfoSize = 0x30;
constexpr size_t kVpuRequestInfoSize = 0x10;
constexpr uint32_t kCodeSize = 0x1c8;
constexpr uint32_t kOutputSize = 0x80;
constexpr uint32_t kAnnVersionOpcode = 10003;

using XrpCreate = uint32_t (*)(const void *options, void **device);
using XrpRelease = uint32_t (*)(void **device);
using XrpCreateCommand = uint32_t (*)(void *device, uint64_t handle);
using XrpAllocateBuffer = uint32_t (*)(void *device, uint32_t size,
                                       void *buffer_info);
using XrpUseInputBuffer = uint32_t (*)(void *device, uint64_t handle,
                                       const void *buffer_info);
using XrpUseOutputBuffer = uint32_t (*)(void *device, uint64_t handle,
                                        const void *buffer_info);
using XrpFinalizeCommand = uint32_t (*)(void *device, uint64_t handle,
                                        void *output_buffers,
                                        uint32_t output_count);
using XrpGetPreparedRequests = uint32_t (*)(void *device, uint64_t handle,
                                            void *requests,
                                            uint32_t request_count);
using XrpSyncBuffer = uint32_t (*)(void *device, int32_t direction,
                                   const void *buffer_info);
using ApusysSessionCreateInstance = void *(*)();
using ApusysSessionDeleteInstance = int32_t (*)(void *);

template <typename T>
T load_le(const uint8_t *p) {
    T v = 0;
    memcpy(&v, p, sizeof(v));
    return v;
}

template <typename T>
void store_le(uint8_t *p, T v) {
    memcpy(p, &v, sizeof(v));
}

void dump_hex(const char *label, const void *data, size_t size,
              size_t max_size = 0x100) {
    const uint8_t *p = static_cast<const uint8_t *>(data);
    size_t n = size < max_size ? size : max_size;
    printf("%s size=0x%zx dump=0x%zx\n", label, size, n);
    for (size_t off = 0; off < n; off += 16) {
        printf("  %04zx:", off);
        for (size_t i = 0; i < 16 && off + i < n; ++i) {
            printf(" %02x", p[off + i]);
        }
        printf("\n");
    }
}

void dump_words(const char *label, const uint8_t *data, size_t size) {
    printf("%s words:", label);
    for (size_t off = 0; off + 4 <= size; off += 4) {
        printf(" +0x%02zx=0x%08x", off, load_le<uint32_t>(data + off));
    }
    printf("\n");
}

void *host_va_from_bufinfo(const uint8_t *info) {
    uintptr_t candidate28 = load_le<uint64_t>(info + 0x28);
    uintptr_t candidate20 = load_le<uint64_t>(info + 0x20);
    if (candidate28 > 0x1000000000ULL) {
        return reinterpret_cast<void *>(candidate28);
    }
    if (candidate20 > 0x1000000000ULL) {
        return reinterpret_cast<void *>(candidate20);
    }
    return nullptr;
}

void dump_bufinfo(const char *label, const uint8_t *info) {
    printf("%s raw:", label);
    for (size_t off = 0; off < kBufInfoSize; off += 4) {
        printf(" +0x%02zx=0x%08x", off, load_le<uint32_t>(info + off));
    }
    printf("\n");
    printf("%s qwords: +0x08=0x%016" PRIx64
           " +0x20=0x%016" PRIx64 " +0x28=0x%016" PRIx64 "\n",
           label, load_le<uint64_t>(info + 0x08),
           load_le<uint64_t>(info + 0x20), load_le<uint64_t>(info + 0x28));
}

void init_empty_bufinfo(uint8_t *info) {
    memset(info, 0, kBufInfoSize);
    store_le<int32_t>(info + 0x18, -1);
}

void fill_ann_version_code(uint8_t *code) {
    memset(code, 0, kCodeSize);
    store_le<uint16_t>(code + 0x00, static_cast<uint16_t>(kAnnVersionOpcode));
    store_le<uint32_t>(code + 0x04, kCodeSize);
    store_le<uint32_t>(code + 0x08, 0);
    store_le<uint32_t>(code + 0x0c, 0);
    store_le<uint32_t>(code + 0x10, 1);
    store_le<uint16_t>(code + 0x48, 0);
}

template <typename T>
bool load_sym(void *lib, const char *name, T *out) {
    *out = reinterpret_cast<T>(dlsym(lib, name));
    if (!*out) {
        fprintf(stderr, "missing symbol %s: %s\n", name, dlerror());
        return false;
    }
    return true;
}

}  // namespace

uint64_t parse_u64_arg(const char *arg, const char *prefix, uint64_t fallback) {
    size_t len = strlen(prefix);
    if (strncmp(arg, prefix, len) != 0) {
        return fallback;
    }
    return strtoull(arg + len, nullptr, 0);
}

int main(int argc, char **argv) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);

    bool use_apuware = false;
    bool skip_finalize = false;
    bool force_get_prepared = false;
    bool sync_buffers = false;
    bool finalize_slot_index = false;
    bool dlopen_lazy = false;
    bool create_apusys_session = true;
    uint64_t handle = 1;
    uint32_t finalize_count = 1;
    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "apuware") == 0 || strcmp(argv[i], "--lib=apuware") == 0) {
            use_apuware = true;
        } else if (strcmp(argv[i], "neuron") == 0 || strcmp(argv[i], "--lib=neuron") == 0) {
            use_apuware = false;
        } else if (strcmp(argv[i], "--skip-finalize") == 0) {
            skip_finalize = true;
        } else if (strcmp(argv[i], "--force-get-prepared") == 0) {
            force_get_prepared = true;
        } else if (strcmp(argv[i], "--sync") == 0) {
            sync_buffers = true;
        } else if (strcmp(argv[i], "--finalize-slot-index") == 0) {
            finalize_slot_index = true;
        } else if (strcmp(argv[i], "--dlopen-lazy") == 0) {
            dlopen_lazy = true;
        } else if (strcmp(argv[i], "--create-apusys-session") == 0) {
            create_apusys_session = true;
        } else if (strcmp(argv[i], "--no-create-apusys-session") == 0) {
            create_apusys_session = false;
        } else if (strncmp(argv[i], "--handle=", 9) == 0) {
            handle = parse_u64_arg(argv[i], "--handle=", handle);
        } else if (strncmp(argv[i], "--finalize-count=", 17) == 0) {
            finalize_count = static_cast<uint32_t>(
                parse_u64_arg(argv[i], "--finalize-count=", finalize_count));
        }
    }

    const char *apuware_paths[] = {
        "/system/system_ext/lib64/libapuwarexrp_v2.mtk.so",
        "/system_ext/lib64/libapuwarexrp_v2.mtk.so",
    };
    const char *neuron_paths[] = {
        "/system/lib64/libneuron_platform.vpu.so",
        "/vendor/lib64/mt6893/libneuron_platform.vpu.so",
        "/vendor/lib64/libneuron_platform.vpu.so",
        "/system/vendor/lib64/mt6893/libneuron_platform.vpu.so",
        "/system/vendor/lib64/libneuron_platform.vpu.so",
    };
    const char **lib_paths = use_apuware ? apuware_paths : neuron_paths;
    size_t lib_path_count = use_apuware
        ? sizeof(apuware_paths) / sizeof(apuware_paths[0])
        : sizeof(neuron_paths) / sizeof(neuron_paths[0]);
    if (use_apuware) {
        create_apusys_session = false;
    }

    printf("mode=%s handle=%" PRIu64
           " finalize_count=%u sync=%d finalize_index_mode=%s dlopen=%s"
           " create_apusys_session=%d\n",
           use_apuware ? "apuware" : "neuron", handle, finalize_count,
           sync_buffers ? 1 : 0,
           finalize_slot_index ? "slot" : "buffer_info",
           dlopen_lazy ? "lazy" : "now",
           create_apusys_session ? 1 : 0);
    void *lib = nullptr;
    for (size_t i = 0; i < lib_path_count; ++i) {
        const char *path = lib_paths[i];
        lib = dlopen(path, dlopen_lazy ? RTLD_LAZY : RTLD_NOW);
        if (lib) {
            printf("dlopen path=%s\n", path);
            break;
        }
        fprintf(stderr, "dlopen failed path=%s error=%s\n", path, dlerror());
    }
    if (!lib) {
        return 2;
    }

    XrpCreate xrp_create = nullptr;
    XrpRelease xrp_release = nullptr;
    XrpCreateCommand xrp_create_command = nullptr;
    XrpAllocateBuffer xrp_allocate_buffer = nullptr;
    XrpUseInputBuffer xrp_use_input_buffer = nullptr;
    XrpUseOutputBuffer xrp_use_output_buffer = nullptr;
    XrpFinalizeCommand xrp_finalize_command = nullptr;
    XrpGetPreparedRequests xrp_get_prepared_requests = nullptr;
    XrpSyncBuffer xrp_sync_buffer = nullptr;

    if (!load_sym(lib, "XRP_Create", &xrp_create) ||
        !load_sym(lib, "XRP_Release", &xrp_release) ||
        !load_sym(lib, "XRP_CreateCommand", &xrp_create_command) ||
        !load_sym(lib, "XRP_AllocateBuffer", &xrp_allocate_buffer) ||
        !load_sym(lib, "XRP_UseInputBuffer", &xrp_use_input_buffer) ||
        !load_sym(lib, "XRP_UseOutputBuffer", &xrp_use_output_buffer) ||
        !load_sym(lib, "XRP_FinalizeCommand", &xrp_finalize_command) ||
        !load_sym(lib, "XRP_GetPreparedRequests", &xrp_get_prepared_requests)) {
        return 2;
    }
    xrp_sync_buffer = reinterpret_cast<XrpSyncBuffer>(dlsym(lib, "XRP_SyncBuffer"));

    uint8_t options[0x30] = {};
    store_le<uint32_t>(options + 0x00, 0x18);
    void *apusys_lib = nullptr;
    void *apusys_session = nullptr;
    ApusysSessionDeleteInstance apusys_session_delete = nullptr;
    if (create_apusys_session) {
        const char *apusys_paths[] = {
            "/vendor/lib64/libapu_mdw.so",
            "/system/vendor/lib64/libapu_mdw.so",
            "/system/lib64/libapu_mdw.so",
        };
        ApusysSessionCreateInstance apusys_session_create = nullptr;
        for (size_t i = 0; i < sizeof(apusys_paths) / sizeof(apusys_paths[0]); ++i) {
            apusys_lib = dlopen(apusys_paths[i], RTLD_NOW | RTLD_GLOBAL);
            if (apusys_lib) {
                printf("dlopen apusys path=%s\n", apusys_paths[i]);
                break;
            }
            fprintf(stderr, "dlopen apusys failed path=%s error=%s\n",
                    apusys_paths[i], dlerror());
        }
        if (!apusys_lib ||
            !load_sym(apusys_lib, "apusysSession_createInstance",
                      &apusys_session_create) ||
            !load_sym(apusys_lib, "apusysSession_deleteInstance",
                      &apusys_session_delete)) {
            printf("APUSYS session helper unavailable; continue with null session.\n");
        } else {
            apusys_session = apusys_session_create();
            printf("apusysSession_createInstance session=%p\n", apusys_session);
            store_le<uint64_t>(options + 0x10,
                               reinterpret_cast<uintptr_t>(apusys_session));
        }
    }
    void *device = nullptr;
    uint32_t st = xrp_create(options, &device);
    printf("XRP_Create status=%u device=%p\n", st, device);
    if (st != 0 || !device) {
        printf("XRP_Create did not initialize the wrapper; skip follow-up calls.\n");
        if (apusys_session && apusys_session_delete) {
            int32_t del_st = apusys_session_delete(apusys_session);
            printf("apusysSession_deleteInstance status=%d\n", del_st);
        }
        return 1;
    }

    st = xrp_create_command(device, handle);
    printf("XRP_CreateCommand status=%u handle=%" PRIu64 "\n", st, handle);
    if (st != 0) {
        xrp_release(&device);
        return 1;
    }

    uint8_t code_info[kBufInfoSize];
    uint8_t out_info[kBufInfoSize];
    init_empty_bufinfo(code_info);
    init_empty_bufinfo(out_info);
    st = xrp_allocate_buffer(device, kCodeSize, code_info);
    printf("XRP_AllocateBuffer(code) status=%u\n", st);
    dump_bufinfo("code_info", code_info);
    void *code_va = host_va_from_bufinfo(code_info);
    printf("code_host_va=%p\n", code_va);
    if (st == 0 && code_va) {
        fill_ann_version_code(static_cast<uint8_t *>(code_va));
        dump_hex("code_host_after_fill", code_va, kCodeSize, 0x80);
    }

    if (sync_buffers && xrp_sync_buffer) {
        st = xrp_sync_buffer(device, 1, code_info);
        printf("XRP_SyncBuffer(code,1) status=%u\n", st);
    }

    st = xrp_allocate_buffer(device, kOutputSize, out_info);
    printf("XRP_AllocateBuffer(output) status=%u\n", st);
    dump_bufinfo("out_info", out_info);
    void *out_va = host_va_from_bufinfo(out_info);
    printf("out_host_va=%p\n", out_va);
    if (st == 0 && out_va) {
        memset(out_va, 0xa5, kOutputSize);
        dump_hex("out_host_after_fill", out_va, kOutputSize, 0x40);
    }

    if (sync_buffers && xrp_sync_buffer) {
        st = xrp_sync_buffer(device, 1, out_info);
        printf("XRP_SyncBuffer(output,1) status=%u\n", st);
    }

    st = xrp_use_input_buffer(device, handle, code_info);
    printf("XRP_UseInputBuffer status=%u\n", st);
    st = xrp_use_output_buffer(device, handle, out_info);
    printf("XRP_UseOutputBuffer status=%u\n", st);

    std::vector<uint8_t> finalize_infos;
    if (!skip_finalize) {
        void *finalize_outputs = nullptr;
        if (finalize_count) {
            finalize_infos.resize(static_cast<size_t>(finalize_count) * kBufInfoSize);
            for (uint32_t i = 0; i < finalize_count; ++i) {
                uint8_t *dst = finalize_infos.data() + static_cast<size_t>(i) * kBufInfoSize;
                memcpy(dst, out_info, kBufInfoSize);
                if (finalize_slot_index) {
                    store_le<uint64_t>(dst + 0x08, i);
                }
            }
            finalize_outputs = finalize_infos.data();
            dump_bufinfo("finalize_info[0]", finalize_infos.data());
        }
        st = xrp_finalize_command(device, handle, finalize_outputs, finalize_count);
        printf("XRP_FinalizeCommand status=%u\n", st);
        if (st != 0 && !force_get_prepared) {
            printf("XRP_FinalizeCommand did not complete; skip GetPreparedRequests.\n");
            if (out_va) {
                dump_hex("out_host_final", out_va, kOutputSize, 0x80);
            }
            xrp_release(&device);
            return 1;
        }
    } else {
        printf("XRP_FinalizeCommand skipped by option.\n");
    }

    uint8_t requests[kVpuRequestInfoSize * 4] = {};
    st = xrp_get_prepared_requests(device, handle, requests, 4);
    printf("XRP_GetPreparedRequests status=%u\n", st);
    for (size_t i = 0; i < 4; ++i) {
        const uint8_t *r = requests + i * kVpuRequestInfoSize;
        printf("request[%zu]: u32_0=0x%08x u32_4=0x%08x ptr=0x%016" PRIx64 "\n",
               i, load_le<uint32_t>(r), load_le<uint32_t>(r + 4),
               load_le<uint64_t>(r + 8));
    }
    dump_hex("requests_raw", requests, sizeof(requests), sizeof(requests));

    uintptr_t req_ptr = load_le<uint64_t>(requests + 8);
    if (st == 0 && req_ptr > 0x1000000000ULL) {
        dump_hex("request0_blob", reinterpret_cast<const void *>(req_ptr),
                 0xb70, 0x180);
        const uint8_t *req = reinterpret_cast<const uint8_t *>(req_ptr);
        dump_words("request0_head", req, 0x80);
        printf("request0_algo=%s\n", reinterpret_cast<const char *>(req + 4));
    }

    if (out_va) {
        dump_hex("out_host_final", out_va, kOutputSize, 0x80);
    }

    xrp_release(&device);
    if (apusys_session && apusys_session_delete) {
        int32_t del_st = apusys_session_delete(apusys_session);
        printf("apusysSession_deleteInstance status=%d\n", del_st);
    }
    return 0;
}
