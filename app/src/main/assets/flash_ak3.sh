#!/system/bin/sh

## setup for testing:
unzip -p $Z tools*/busybox > $F/busybox;
unzip -p $Z META-INF/com/google/android/update-binary > $F/update-binary;
/system/bin/make_ext4fs -b 4096 -l 400M $F/ak3-tmp.img || /system/bin/mke2fs -b 4096 -t ext4 $F/ak3-tmp.img 400M || exit 1;
##

chmod 755 $F/busybox;
$F/busybox chmod 755 $F/update-binary;
$F/busybox chown root:root $F/busybox $F/ak3-tmp.img $F/update-binary;

TMP=$F/tmp;

$F/busybox umount $TMP 2>/dev/null;
$F/busybox rm -rf $TMP 2>/dev/null;
$F/busybox mkdir -p $TMP;

LOOP=$($F/busybox losetup -f | sed -e 's/\/dev/\/dev\/block/')
[[ -z "$LOOP" ]] && exit 1;

$F/busybox losetup $LOOP $F/ak3-tmp.img;
$F/busybox losetup $LOOP | $F/busybox grep -q ak3-tmp.img || exit 1;

$F/busybox mount -t ext4 -o rw,noatime $LOOP $TMP;
$F/busybox mount | $F/busybox grep -q " $TMP " || exit 1;

# update-binary <RECOVERY_API_VERSION> <OUTFD> <ZIPFILE>
AKHOME=$TMP/anykernel $F/busybox ash $F/update-binary 3 1 "$Z";
RC=$?;

$F/busybox umount $TMP;
$F/busybox losetup -d $LOOP;
$F/busybox rm -rf $TMP;
$F/busybox mount -o ro,remount -t auto /;
$F/busybox rm -f $F/ak3-tmp.img $F/update-binary $F/busybox;

# work around libsu not cleanly accepting return or exit as last line
safereturn() { return $RC; }
safereturn;
