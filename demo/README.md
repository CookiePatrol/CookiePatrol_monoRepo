# Cookie Patrol 데모 시나리오

이 데모는 교육용 가짜 배송 알림과 정적 페이지를 사용합니다. 실제 피싱 사이트로 이동하지 않고, 이름·계정·인증번호를 입력받거나 저장하지 않습니다. 페이지에 표시한 `192.0.2.1`은 문서 예제용 주소입니다.

## 1. 백엔드 실행

Google Safe Browsing API 키를 `SAFE_BROWSING_API_KEY` 환경 변수에 설정한 뒤 실행합니다. 키는 저장소에 추가하지 마세요.

```powershell
cd backend
uv run fastapi dev app/main.py
```

에뮬레이터에서 Android 앱은 기본값 `http://10.0.2.2:8000`으로 백엔드에 접속합니다. 실제 폰에서는 PC의 로컬 네트워크 주소를 빌드 속성으로 전달합니다.

```powershell
cd frontend
./gradlew.bat -PcookiePatrolApiBaseUrl=http://192.168.0.10:8000 assembleDebug
```

예시 주소를 PC의 실제 로컬 네트워크 주소로 바꾸고, 폰과 PC를 같은 네트워크에 연결하세요. 앱의 Android 매니페스트는 데모를 위해 HTTP 통신을 허용합니다.

## 2. 데모 페이지 실행

별도 PowerShell 창에서 저장소 루트 기준으로 실행합니다.

```powershell
powershell -ExecutionPolicy Bypass -File demo/serve.ps1
```

에뮬레이터 브라우저에서 `http://10.0.2.2:8765/`를 엽니다. 실제 폰에서는 서버 스크립트가 표시하는 PC 주소에 `:8765`를 붙여 사용합니다. Windows 방화벽이 접근을 막으면 개인 네트워크에서만 이 서버의 접속을 허용하세요.

## 3. Android 경고 확인

1. 앱을 설치하고 알림 표시와 문자·카톡 알림 감시 권한을 켭니다.
2. 화면 속 글자를 시험하려면 앱의 **화면 감시 켜기**를 눌러 데모용 접근성 서비스를 켭니다.
3. 데모 페이지를 브라우저에서 엽니다. `택배`, `http`, 숫자 IP, `.apk` 표시가 로컬 위험 신호를 만들어 경고가 떠야 합니다.
4. 다른 기기에서 다음 문장을 문자로 보내거나, 이 문장 그대로 보이는 알림을 생성해 알림 감시도 확인합니다.

```text
[배송 안내] 주소가 맞지 않아 택배가 보류됐습니다. 확인: http://192.0.2.1/parcel.apk
```

5. 정상 알림 예시로 `내일 회의 자료 https://docs.google.com/abc 확인 부탁드려요`를 보내고 위험 경고가 뜨지 않는지 확인합니다.

## 기대 결과와 한계

- 의심 문장과 데모 페이지에서는 로컬 위험 신호 경고가 즉시 나타납니다. 실제 앱 설치나 개인정보 입력은 일어나지 않습니다.
- Safe Browsing에 등록되지 않은 데모 주소는 `dangerous: false` 또는 조회 불가 시 `unknown`이 될 수 있습니다. `false`는 안전 판정 보증이 아닙니다.
- 서버 조회 실패나 API 키 누락으로도 로컬 경고가 사라지지 않아야 합니다.
- 의심 URL의 전체 주소는 백엔드와 Google Safe Browsing으로 전송됩니다. URL의 경로·쿼리에 개인 정보나 토큰을 넣지 마세요. 서버 캐시는 주소 원문 대신 SHA-256 키를 저장하지만, 이는 익명화가 아닙니다.
- 이 페이지는 교육용 시뮬레이션입니다. 실제 문자 앱·제조사별 접근성 트리·알림 형식은 폰에 따라 다르므로 실기기 확인이 필요합니다.

## 자동 확인

저장소 루트에서 Android 점수 단위 테스트와 디버그 APK 빌드를 실행합니다.

```powershell
cd frontend
./gradlew.bat testDebugUnitTest assembleDebug
```

백엔드 테스트는 다음과 같습니다.

```powershell
cd backend
uv run pytest
```
