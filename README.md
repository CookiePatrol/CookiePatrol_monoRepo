# CookiePatrol_monoRepo

GWUK의 Android 피싱 위험 안내 앱 **COOKIE PATROL**입니다. Kotlin/Compose 앱과 FastAPI 백엔드로 구성합니다.

- [심사 의견 반영 보고서](docs/심사_반영_보고서.md): 호출 흐름, 변경 내용, 실행 결과와 한계
- [재점검 결과와 남은 작업](docs/재점검_결과.md): 추가 결함 수정, 최종 실행 기록, 데이터 독립성 한계
- [main 통합 검증](docs/머지_검증.md): 충돌 해결, 디자인·배포 설정 보존, 통합 후 테스트 결과
- [평가 실행 방법](docs/평가_실행_방법.md): 키 없는 전체 검증, 실제 API 비교 재실행
- [120개 합성 문장 평가 결과](docs/evaluation/mock-results.md): 로컬 규칙과 외부 검사 **모의 시험**
- [규칙·점수·공식 근거](docs/규칙_점수_근거.md)
- [실기기 2~3대 검증표와 영상 촬영안](docs/실기기_검증표.md)
- [팀원 검토용 협업 기록](docs/협업_기록.md)
- [기획서](PLAN.md), [백엔드 실행 안내](backend/README.md), [데모](demo/README.md)

Windows에서 최초 `backend`의 `uv sync` 후 저장소 루트에서 실행합니다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
```

갤럭시 실기기 1대에서 PC 검사 서버와 연결해 테스트용 메시지 알림의 링크 검사, 위험 배너, 경고 상세 화면을 확인했습니다. Google·OpenAI 탐지 정확도와 기종별 동작은 별도 검증이 필요합니다. 개발·평가 문구에 의미가 유사한 사례가 있어 합성 결과만으로 독립적인 정확도를 판단하기 어렵습니다. 자동 URL 외부 검사는 앱에서 별도 동의한 경우에만 켜집니다.

Windows PC에서 USB로 연결한 휴대폰에 시연 메시지를 보내려면 [시연 도구 안내](tools/demo-sender/README.md)를 참고하세요.
