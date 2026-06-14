#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define APUSYS_DEV "/dev/apusys"

#define APUSYS_CMD_HANDSHAKE      0xC0284100UL
#define APUSYS_CMD_RUN_SYNC       0x40184106UL
#define APUSYS_CMD_RUN_ASYNC      0xC0184107UL
#define APUSYS_CMD_UCMD           0x4014410EUL
#define APUSYS_CMD_DISABLED_0C    0x4038410CUL
#define APUSYS_CMD_DISABLED_0D    0x4038410DUL
#define APUSYS_CMD_UNKNOWN        0x41414141UL

static void put_u32(void *buf, size_t off, uint32_t v) {
    unsigned char *p = (unsigned char *)buf + off;
    p[0] = (unsigned char)(v & 0xff);
    p[1] = (unsigned char)((v >> 8) & 0xff);
    p[2] = (unsigned char)((v >> 16) & 0xff);
    p[3] = (unsigned char)((v >> 24) & 0xff);
}

static int do_ioctl(int fd, const char *name, unsigned long cmd, void *arg) {
    errno = 0;
    int ret = ioctl(fd, cmd, arg);
    int saved_errno = errno;

    if (ret == -1) {
        printf("%-24s cmd=0x%08lx ret=-1 errno=%d (%s)\n",
               name, cmd, saved_errno, strerror(saved_errno));
    } else {
        printf("%-24s cmd=0x%08lx ret=%d errno=0\n", name, cmd, ret);
    }
    return ret;
}

static void dump_words(const char *label, const unsigned char *buf, size_t len) {
    printf("%s:", label);
    for (size_t i = 0; i < len; i += 4) {
        uint32_t v = (uint32_t)buf[i]
                   | ((uint32_t)buf[i + 1] << 8)
                   | ((uint32_t)buf[i + 2] << 16)
                   | ((uint32_t)buf[i + 3] << 24);
        printf(" %02zu:0x%08x", i, v);
    }
    printf("\n");
}

int main(int argc, char **argv) {
    int run_query = argc > 1 && strcmp(argv[1], "--query") == 0;
    int fd = open(APUSYS_DEV, O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        fprintf(stderr, "open(%s) failed: %d (%s)\n",
                APUSYS_DEV, errno, strerror(errno));
        return 1;
    }

    printf("opened %s fd=%d\n", APUSYS_DEV, fd);

    unsigned char handshake[0x28];
    unsigned char run_cmd[0x18];
    unsigned char ucmd[0x14];

    memset(handshake, 0, sizeof(handshake));
    memset(run_cmd, 0, sizeof(run_cmd));
    memset(ucmd, 0, sizeof(ucmd));

    do_ioctl(fd, "unknown", APUSYS_CMD_UNKNOWN, NULL);
    do_ioctl(fd, "disabled_0c", APUSYS_CMD_DISABLED_0C, NULL);
    do_ioctl(fd, "disabled_0d", APUSYS_CMD_DISABLED_0D, NULL);

    do_ioctl(fd, "handshake_reject", APUSYS_CMD_HANDSHAKE, handshake);

    put_u32(run_cmd, 0x0c, 1);
    do_ioctl(fd, "run_async_reject", APUSYS_CMD_RUN_ASYNC, run_cmd);
    do_ioctl(fd, "run_sync_reject", APUSYS_CMD_RUN_SYNC, run_cmd);

    put_u32(ucmd, 0x0c, 1);
    do_ioctl(fd, "ucmd_reject", APUSYS_CMD_UCMD, ucmd);

    if (run_query) {
        memset(handshake, 0, sizeof(handshake));
        put_u32(handshake, 0x0c, 1);
        do_ioctl(fd, "handshake_query", APUSYS_CMD_HANDSHAKE, handshake);
        dump_words("handshake", handshake, sizeof(handshake));
    }

    close(fd);
    return 0;
}
