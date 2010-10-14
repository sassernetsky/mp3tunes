#!/bin/bash

cd mp3tunes  
rm -rf bin gen
ant release 
/usr/lib/jvm/java-6-sun-1.6.0.14/bin/jarsigner -verbose -keystore ~/my-release-key.keystore -storepass wEluveM1 bin/Login-unsigned.apk  latency
rm ../mp3tunes-android_*.apk

d=`date +20%y%m%d%H%M`
filename='../mp3tunes-android_'$d'.apk'

zipalign -v 4  bin/Login-unsigned.apk $filename
mv bin/Login-release.apk $filename

# original googlecode_upload call...
#
#googlecode_upload.py -s "Latest Version" -p mp3tunes -u mp3tunesadam@gmail.com -w vQ6ku6CD5Ej8  $filename
#
# New call has different username, for example.
#
# Note: the password is NOT your Gmail account password!
# It is the password we use to access Subversion repositories
# and can be found here:
#
#     http://code.google.com/hosting/settings
#
#googlecode_upload.py -s "Latest Version" -p mp3tunes -u mp3tunesron@gmail.com -w vQ6ku6CD5Ej8 $filename
