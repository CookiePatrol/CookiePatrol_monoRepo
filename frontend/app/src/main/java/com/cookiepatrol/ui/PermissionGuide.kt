package com.cookiepatrol.ui

data class PermissionGuide(val purpose: String, val steps: String, val revoke: String, val limitation: String)

object PermissionGuides {
    val warnings = PermissionGuide(
        "위험 신호를 발견했을 때 경고 알림을 보여드려요. 다른 앱의 문자 내용을 읽는 권한은 아니에요.",
        "설정 화면에서 Cookie Patrol의 알림 허용을 켜주세요. 위험 경고 알림 채널도 켜져 있어야 해요.",
        "같은 설정에서 알림 허용을 끄면 돼요.",
        "알림을 끄거나 방해 금지 모드를 켜면 경고를 놓칠 수 있어요.",
    )
    val messages = PermissionGuide(
        "문자·카톡 알림의 글자와 링크를 기기 안에서 검사해요. 시스템은 다른 알림에도 접근할 수 있는 넓은 권한을 부여하지만 앱은 메시지 알림만 검사해요.",
        "알림 접근 목록에서 Cookie Patrol을 찾아 허용해주세요. Android 설정의 주의 문구도 읽어주세요.",
        "알림 접근 목록에서 Cookie Patrol을 끄면 돼요. 다운로드·앱 설치 감시도 함께 멈춰요.",
        "알림에 내용이 숨겨져 있거나 지원하지 않는 메신저면 검사하지 못해요. Android 15 이상은 인증번호 알림 내용을 가릴 수 있어요. 동의한 경우 의심 링크 전체는 서버·Google으로, 필요하면 OpenAI로 전송돼요. 주소에 개인정보나 로그인 토큰이 포함될 수 있어요.",
    )
    val downloads = PermissionGuide(
        "다운로드된 앱 설치 파일(APK)의 이름을 확인해 경고해요. 파일 내용은 서버로 보내지 않아요. 데모에서만 넓은 파일 접근 권한을 사용해요.",
        "먼저 문자·카톡 알림 감시를 켜주세요. 그다음 모든 파일 접근 설정에서 Cookie Patrol을 허용해주세요.",
        "모든 파일 접근 설정에서 Cookie Patrol을 끄면 돼요.",
        "알림 접근 서비스가 연결돼 있을 때만 감시해요. 기종·Android 버전에 따라 새 파일을 읽지 못할 수 있어요. 권한이 켜졌다는 표시가 감지 성공을 보증하지는 않아요.",
    )
    val screen = PermissionGuide(
        "선택한 데모 기능이에요. 다른 앱 화면에 보이는 글자와 링크, 이미지 속 글자를 기기 안에서 읽어요.",
        "접근성 → 설치된 앱 또는 다운로드한 앱 → Cookie Patrol 화면 감시를 찾아 켜주세요. 메뉴 이름은 기종마다 달라요. 직접 설치한 앱은 Android가 제한된 설정 허용을 먼저 요구할 수 있어요.",
        "같은 접근성 설정에서 화면 감시를 끄면 돼요.",
        "화면 캡처는 OCR 처리 후 버리고 저장·전송하지 않아요. 의심 링크 전체는 서버로 전송될 수 있어요. 보안 화면·숨은 링크·QR 코드는 검사하지 못할 수 있어요. 출시 범위는 별도로 검토해야 해요.",
    )
}
