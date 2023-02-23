#!/system/bin/sh

## setup for testing:
unzip -p $Z tools*/busybox > $F/busybox;
unzip -p $Z META-INF/com/google/android/update-binary > $F/update-binary;
##

chmod 755 $F/busybox;
$F/busybox chmod 755 $F/update-binary;
$F/busybox chown root:root $F/busybox $F/update-binary;

TMP=$F/tmp;

$F/busybox umount $TMP 2>/dev/null;
$F/busybox rm -rf $TMP 2>/dev/null;
$F/busybox mkdir -p $TMP;

$F/busybox mount -t tmpfs -o noatime tmpfs $TMP;
$F/busybox mount | $F/busybox grep -q " $TMP " || exit 1;

# update-binary <RECOVERY_API_VERSION> <OUTFD> <ZIPFILE>
AKHOME=$TMP/anykernel $F/busybox ash $F/update-binary 3 1 "$Z";
RC=$?;

$F/busybox umount $TMP;
$F/busybox rm -rf $TMP;
$F/busybox mount -o ro,remount -t auto /;
$F/busybox rm -f $F/update-binary $F/busybox;

# work around libsu not cleanly accepting return or exit as last line
safereturn() { return $RC; }
safereturn;
