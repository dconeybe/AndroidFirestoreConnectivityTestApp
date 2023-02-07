```
./gradlew assembleDebug
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start com.google.firebase.firestore.connectivitytestapp/.MainActivity
```
