# Proot GNU/Linux Container Binary
#
# Location: apps/android/src/main/jniLibs/arm64-v8a/
#
# This directory should contain a statically-linked ARM64 proot binary
# (or libproot.so shared library) that can be executed from the Android
# app to bootstrap a GNU/Linux rootfs.
#
# Options to obtain:
# 1. Build with Android NDK: see Octo4a bootstrap builder
#    https://github.com/feelfreelinux/octo4a-bootstrap-builder
# 2. Use proot-static: https://github.com/ZhymabekRoman/proot-static
# 3. Termux package: `pkg install proot` inside Termux (not directly usable from app)
#
# Recommended command line (from TerminalManager.kt):
#   proot --link2symlink -0 -r /path/to/rootfs -w / \
#         -b /dev -b /proc -b /sys /bin/sh -l
#
# Rootfs bootstrap (first run):
#   - Download ARM64 Debian/Ubuntu rootfs tarball
#   - Extract into apps/android/src/main/files/linux/rootfs/
#   - Ensure /dev, /proc, /sys are bound (handled by proot args)
#
# The binary name used in TerminalManager.kt: "libproot.so"
# Adjust the TerminalSession shell path args if using a different binary name.