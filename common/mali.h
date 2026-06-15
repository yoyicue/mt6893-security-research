/* SPDX-License-Identifier: GPL-2.0 WITH Linux-syscall-note */
/*
 * Minimal Mali UAPI definitions for security research PoCs.
 * Extracted from ARM Mali r32p1 kernel driver headers.
 *
 * Only structures and ioctls referenced by the PoCs in this repository.
 */
#ifndef _MALI_RESEARCH_H_
#define _MALI_RESEARCH_H_

#include <sys/ioctl.h>
#include <linux/types.h>

#define KBASE_IOCTL_TYPE 0x80

/* ---- Memory allocation flags ---- */

typedef __u32 base_mem_alloc_flags;

#define BASE_MEM_PROT_CPU_RD        ((base_mem_alloc_flags)1 << 0)
#define BASE_MEM_PROT_CPU_WR        ((base_mem_alloc_flags)1 << 1)
#define BASE_MEM_PROT_GPU_RD        ((base_mem_alloc_flags)1 << 2)
#define BASE_MEM_PROT_GPU_WR        ((base_mem_alloc_flags)1 << 3)
#define BASE_MEM_PROT_GPU_EX        ((base_mem_alloc_flags)1 << 4)
#define BASEP_MEM_NO_USER_FREE      ((base_mem_alloc_flags)1 << 7)
#define BASE_MEM_GROW_ON_GPF        ((base_mem_alloc_flags)1 << 9)
#define BASE_MEM_COHERENT_SYSTEM    ((base_mem_alloc_flags)1 << 10)
#define BASE_MEM_SAME_VA            ((base_mem_alloc_flags)1 << 13)
#define BASE_MEM_DONT_NEED          ((base_mem_alloc_flags)1 << 17)

/* ---- Version check ---- */

struct kbase_ioctl_version_check {
    __u16 major;
    __u16 minor;
};

#define KBASE_IOCTL_VERSION_CHECK \
    _IOWR(KBASE_IOCTL_TYPE, 0, struct kbase_ioctl_version_check)

/* ---- Context setup ---- */

struct kbase_ioctl_set_flags {
    __u32 create_flags;
};

#define KBASE_IOCTL_SET_FLAGS \
    _IOW(KBASE_IOCTL_TYPE, 1, struct kbase_ioctl_set_flags)

/* ---- Memory allocation ---- */

union kbase_ioctl_mem_alloc {
    struct {
        __u64 va_pages;
        __u64 commit_pages;
        __u64 extension;
        __u64 flags;
    } in;
    struct {
        __u64 flags;
        __u64 gpu_va;
    } out;
};

#define KBASE_IOCTL_MEM_ALLOC \
    _IOWR(KBASE_IOCTL_TYPE, 5, union kbase_ioctl_mem_alloc)

/* ---- Memory query ---- */

union kbase_ioctl_mem_query {
    struct {
        __u64 gpu_addr;
        __u64 query;
    } in;
    struct {
        __u64 value;
    } out;
};

#define KBASE_IOCTL_MEM_QUERY \
    _IOWR(KBASE_IOCTL_TYPE, 6, union kbase_ioctl_mem_query)

#define KBASE_MEM_QUERY_COMMIT_SIZE ((__u64)1)
#define KBASE_MEM_QUERY_VA_SIZE     ((__u64)2)
#define KBASE_MEM_QUERY_FLAGS       ((__u64)3)

/* ---- Memory free ---- */

struct kbase_ioctl_mem_free {
    __u64 gpu_addr;
};

#define KBASE_IOCTL_MEM_FREE \
    _IOW(KBASE_IOCTL_TYPE, 7, struct kbase_ioctl_mem_free)

/* ---- Memory flags change ---- */

struct kbase_ioctl_mem_flags_change {
    __u64 gpu_va;
    __u64 flags;
    __u64 mask;
};

#define KBASE_IOCTL_MEM_FLAGS_CHANGE \
    _IOW(KBASE_IOCTL_TYPE, 23, struct kbase_ioctl_mem_flags_change)

/* ---- Memory import ---- */

enum base_mem_import_type {
    BASE_MEM_IMPORT_TYPE_INVALID = 0,
    BASE_MEM_IMPORT_TYPE_UMM = 2,
    BASE_MEM_IMPORT_TYPE_USER_BUFFER = 3
};

struct base_mem_import_user_buffer {
    __u64 ptr;
    __u64 length;
};

union kbase_ioctl_mem_import {
    struct {
        __u64 flags;
        __u64 phandle;
        __u32 type;
        __u32 padding;
    } in;
    struct {
        __u64 flags;
        __u64 gpu_va;
        __u64 va_pages;
    } out;
};

#define KBASE_IOCTL_MEM_IMPORT \
    _IOWR(KBASE_IOCTL_TYPE, 22, union kbase_ioctl_mem_import)

/* ---- JIT init ---- */

struct kbase_ioctl_mem_jit_init {
    __u64 va_pages;
    __u8  max_allocations;
    __u8  trim_level;
    __u8  group_id;
    __u8  padding[5];
    __u64 phys_pages;
};

#define KBASE_IOCTL_MEM_JIT_INIT \
    _IOW(KBASE_IOCTL_TYPE, 14, struct kbase_ioctl_mem_jit_init)

/* ---- Job submit ---- */

struct kbase_ioctl_job_submit {
    __u64 addr;
    __u32 nr_atoms;
    __u32 stride;
};

#define KBASE_IOCTL_JOB_SUBMIT \
    _IOW(KBASE_IOCTL_TYPE, 2, struct kbase_ioctl_job_submit)

/* ---- Job requirements ---- */

#define BASE_JD_REQ_FS             (1 << 0)
#define BASE_JD_REQ_CS             (1 << 1)
#define BASE_JD_REQ_T              (1 << 2)
#define BASE_JD_REQ_SOFT_JOB       (1 << 9)
#define BASE_JD_REQ_SOFT_JIT_ALLOC (BASE_JD_REQ_SOFT_JOB | 0x09)
#define BASE_JD_REQ_SOFT_JIT_FREE  (BASE_JD_REQ_SOFT_JOB | 0x0A)

/* ---- Event codes ---- */

#define BASE_JD_EVENT_DONE         0x1
#define BASE_JD_EVENT_TERMINATED   0x4
#define BASE_JD_SW_EVENT           0x60000000
#define BASE_JD_SW_EVENT_JOB       0x20000000

/* ---- JIT alloc info ---- */

struct base_jit_alloc_info {
    __u64 gpu_alloc_addr;
    __u64 va_pages;
    __u64 commit_pages;
    __u64 extension;
    __u8  id;
    __u8  padding1[7];
    __u64 usage_id;
    __u16 flags;
    __u8  padding2[6];
    __u64 heap_info_gpu_addr;
};

/* ---- Tracking handle ---- */

#define BASE_MEM_MAP_TRACKING_HANDLE (3ull << 12)

#endif /* _MALI_RESEARCH_H_ */
