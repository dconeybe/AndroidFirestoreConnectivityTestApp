The steps below build, install, launch the test app, wait for 20 seconds, then
save the logs to logcat.txt
```
./gradlew assembleDebug
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start com.google.firebase.firestore.connectivitytestapp/.MainActivity
sleep 20
adb logcat -d > logcat.txt
```
