#!/system/bin/sh

# capture all the outputs
(

mkdir $F/tmp
$F/busybox unzip -p "$Z" META-INF/com/google/android/update-binary > $F/tmp/update-binary;

# update-binary <RECOVERY_API_VERSION> <OUTFD> <ZIPFILE>
AKHOME=$F/tmp $F/busybox ash $F/tmp/update-binary 3 1 "$Z";
RC=$?;

rm -rf $F/tmp;

# work around libsu not cleanly accepting return or exit as last line
safereturn() { return $RC; }
safereturn;

) 2>&1;
# done capture
