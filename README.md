# 이어듣기 · AFTERPLAY

유튜브 공개 영상 링크로 나만의 재생목록을 만들고, 화면을 끄거나 다른 앱을 사용하는 동안 **오디오 스트림만** 재생하는 개인 설치용 Android 앱입니다.

## 설치와 사용

1. `artifacts/AfterPlay-1.2.1-debug.apk`를 Android 휴대폰으로 복사해 설치합니다. Android 8.0(API 26) 이상이 필요합니다. APK를 연 브라우저/파일 앱에 ‘알 수 없는 앱 설치’를 허용해야 할 수 있습니다.
2. **새 목록**으로 재생목록을 만듭니다.
3. **영상 추가**에서 유튜브 영상 링크를 붙여넣습니다. 유튜브 앱의 **공유 → 이어듣기**도 지원합니다.
4. **전체 재생** 또는 개별 곡을 누릅니다. 홈으로 이동하거나 화면을 꺼도 재생됩니다.
5. 앱 하단, 미디어 알림, 잠금화면에서 재생을 제어합니다. 하단의 × 버튼으로 재생을 종료합니다.
6. 재생바의 **수면 모드**에서 **20분 / 40분 / 60분** 중 하나를 선택하면 그 시간이 지난 뒤 재생이 종료됩니다. 남은 시간이 표시되며 옆의 × 또는 수면 모드 창의 **타이머 해제**로 취소할 수 있습니다.

## 기능

- 여러 재생목록 생성, 이름 변경, 삭제, 기기 내 영구 저장(DataStore)
- 영상 제목/채널/썸네일 자동 조회, 같은 목록 내 중복 추가 방지
- 곡 순서 변경 및 삭제
- 곡 메뉴 → 다른 재생목록으로 이동 (대상에 이미 있으면 중복 없이 이동)
- 순차 재생, 이전/다음 곡, 탐색, 셔플, 한 곡/전체 반복
- 종료 후 셔플·반복 설정, 마지막 곡과 재생 위치 복원
- 재생목록별 마지막 위치 기억: 전체 재생 또는 같은 곡 선택 시 이어듣기
- 수면 타이머: 20분·40분·60분 프리셋, 남은 시간 표시, 변경/해제, 화면 꺼짐·백그라운드에서 재생 종료
- 연결 실패 시 현재 곡을 총 3회 시도한 뒤 다음 곡으로 이동하고, 다음 곡도 3회 실패하면 정지
- 한국어 음성 우선 재생, 한국어가 없으면 원본 음성 선택 (같은 음성 내에서는 고음질 우선)
- Media3 `MediaSessionService` 기반 백그라운드 오디오 재생
- 시스템 오디오 포커스 처리 및 이어폰 연결 해제 시 일시정지
- 일반 watch 링크, youtu.be, Shorts, YouTube Music 영상 링크, 공유 텍스트 지원
- 로그인, API 키, 별도 서버 불필요

현재 재생 대기열은 재생을 시작할 때의 목록입니다. 목록 편집과 곡 이동은 다음 전체 재생부터 반영됩니다. 목록은 앱을 다시 실행해도 보관됩니다. × 버튼으로 종료하면 마지막 정보를 남기며, 앱을 다시 열면 재생 대기열과 설정을 일시정지 상태로 복원합니다. 하단 재생 버튼 또는 해당 목록의 전체 재생으로 마지막 위치에서 이어듣습니다. 다른 곡을 직접 누르면 그 곡의 처음부터 시작합니다(해당 목록에서 마지막으로 듣던 곡은 이어서 재생). 영상이 끝까지 완료된 경우에는 처음부터 시작합니다.

재생 중에는 2초마다 위치를 저장하며, 일시정지·탐색·종료·정상 서비스 종료 때도 저장합니다. 운영체제의 갑작스러운 프로세스 종료나 강제 종료 직후에는 마지막 주기 저장 시점으로 복원되어 약 2초의 차이가 있을 수 있습니다. 앱을 열기만 해서는 오디오를 자동으로 재생하지 않습니다.

수면 타이머는 설정한 순간부터 경과 시간을 셉니다. 일시정지하거나 곡/목록을 바꿔도 계속되며, 다른 프리셋을 선택하면 그 순간부터 새로 시작합니다. 만료 시 마지막 곡/위치를 저장하고 재생을 종료합니다. 직접 재생을 종료하거나 서비스를 종료하면 타이머도 해제되며, 앱 강제 종료 후에는 다시 설정해야 합니다.

## 빌드

Android Studio에서 이 폴더를 열거나 JDK 17 이상과 Android SDK 36을 설치한 뒤 실행합니다. 첫 빌드는 Maven/Google/JitPack에 접속합니다.

```powershell
.\build-apk.ps1 -Test
```

Android Studio의 기본 JDK 및 SDK 경로는 스크립트가 자동으로 찾습니다. 다른 위치에 설치했다면 `JAVA_HOME`, `ANDROID_HOME`을 지정하세요. macOS/Linux에서는 `./gradlew assembleDebug testDebugUnitTest lintDebug`를 사용할 수 있습니다.

설치 파일은 `artifacts/AfterPlay-1.2.1-debug.apk`와 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 개인 설치용 debug 서명을 사용합니다. 동일한 기기에 업데이트하려면 동일한 개발용 서명 키로 빌드해야 합니다. 스토어 제출용 서명은 포함하지 않습니다.

## 검증

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
# 에뮬레이터 없이 유튜브 추출/오디오 응답 확인
.\gradlew.bat testDebugUnitTest -PyoutubeNetwork=true
# 에뮬레이터/기기를 연결한 뒤: UI, 실제 미디어 서비스의 백그라운드 재생 테스트
.\gradlew.bat connectedDebugAndroidTest
# 공개 유튜브 영상 추출 및 오디오 재생까지 확인하는 네트워크 테스트
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.youtubeNetwork=true
```

네트워크 테스트는 기본적으로 건너뜁니다. 실제 유튜브와 해당 네트워크의 접근 상태에 영향을 받습니다. 로컬 WAV 재생 테스트는 안드로이드 서비스의 재생 지속/자동 다음 곡/일시정지를 검증하며, 유튜브 추출 성공을 대신하지 않습니다. 기기 네트워크 테스트에서는 공개 영상 재생을 시작한 뒤 앱을 백그라운드로 옮기고 화면을 꺼 재생 지속을 확인합니다.

## 지원 범위

- 인터넷 연결이 필요합니다. 영상/오디오 다운로드 및 오프라인 저장 기능은 없습니다.
- 공개된 일반 영상만 지원합니다. 로그인, 비공개, 유료, 지역/연령 제한, 실시간 방송 지원을 보장하지 않습니다. 접근 제한을 해제하는 기능은 없습니다.
- 재생목록은 앱 안에서 만듭니다. 유튜브 재생목록 URL 전체 가져오기나 계정 동기화는 포함하지 않습니다.
- YouTube 공식 API 정책은 음성 분리와 백그라운드 플레이어를 허용하지 않습니다. 이 앱은 공식 API나 임베디드 플레이어 대신 NewPipe Extractor를 사용하며 YouTube의 공식·승인된 앱이 아닙니다. 개인용 APK로 제작했으며 정책 준수나 스토어 배포 가능성을 보장하지 않습니다.
- YouTube 변경, 요청 제한, 봇 확인에 따라 추출/재생이 실패할 수 있습니다. 이 경우 네트워크를 확인하거나 `app/build.gradle.kts`의 NewPipe Extractor를 호환 버전으로 업데이트해야 합니다.
- 일부 제조사의 강한 절전 설정이나 사용자의 강제 종료는 재생 서비스를 중단할 수 있습니다.

참고: [YouTube API 정책](https://developers.google.com/youtube/terms/developer-policies), [Media3 백그라운드 재생](https://developer.android.com/media/media3/session/background-playback), [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## 구성

- `data/Models.kt`: URL 검증과 재생목록 연산
- `data/LibraryStore.kt`: DataStore 저장소
- `playback/YoutubeRepository.kt`: HTTP 다운로드 어댑터, 오디오 추출, 짧은 메모리 캐시
- `playback/PlaybackService.kt`: 재생 직전 URL 해석, ExoPlayer 및 미디어 세션
- `playback/PlaybackStore.kt`: 재생 대기열, 목록별 마지막 곡/위치, 셔플/반복 및 선택한 목록 저장
- `playback/SleepTimer.kt`: 서비스 수명에 묶인 수면 타이머, 경과 시간 기준 마감, 화면 꺼짐 중 만료 처리
- `PlayerViewModel.kt`: 라이브러리 및 미디어 컨트롤러 상태
- `ui/AfterPlayScreen.kt`: 한국어 Jetpack Compose UI

스트림 주소는 디스크에 저장하지 않고 각 곡의 로딩 시점에 해석합니다. 오디오 스트림만 ExoPlayer에 전달합니다. API 키/쿠키/사용자 계정 정보는 사용하지 않습니다. 영상 메타데이터, 스트림, 썸네일 요청은 YouTube/Google 서버로 전송됩니다.

## 라이선스

앱 소스는 **GPL-3.0-or-later**로 제공합니다. [LICENSE](LICENSE) 및 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)를 참고하세요. APK를 재배포할 때에는 GPL에 따른 대응 소스 제공 등의 의무를 확인하세요.
