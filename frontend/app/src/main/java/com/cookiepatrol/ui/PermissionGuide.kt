package com.cookiepatrol.ui

data class PermissionGuide(val purpose: String, val steps: String, val revoke: String, val limitation: String)

internal object LinkDisclosure {
    const val TRANSFER = "주소 전체를 서버와 Google Safe Browsing에 보내고, 필요할 때 OpenAI에도 보내요."
    const val SENSITIVE = "주소에 개인정보나 로그인 토큰이 들어 있으면 검사하지 마세요."
    const val STORAGE = "서버는 주소 해시·판정·위험 신호 코드·시각을 남겨요. 판정은 24시간 동안 사용하고, 만료 기록은 다음 조회·저장 때 지워요. 해시는 완전한 익명 정보가 아니에요."
    const val ANALYSIS = "AI는 주소의 도메인·경로 등을 살펴봐요. 웹페이지 방문이나 이동 주소 확인은 하지 않아요."
}

object PermissionGuides {
    val warnings = PermissionGuide(
        "2·3단계 경고는 다른 앱을 사용하는 중에도 화면 상단에 팝업으로 떠요. 팝업을 누르면 경고 이유와 대응 방법을 볼 수 있어요.",
        "Cookie Patrol의 알림을 허용하고 ‘위험 경고’ 알림의 팝업 표시를 켜주세요.",
        "같은 설정에서 알림 허용을 끄면 돼요.",
        "팝업을 끄거나 방해 금지 모드를 켜면 화면에 경고가 뜨지 않을 수 있어요.",
    )
    val messages = PermissionGuide(
        "문자·카톡 알림의 내용에서 의심스러운 링크, 전화·송금 유도를 기기 안에서 검사해요. 시스템의 알림 접근 권한은 다른 알림에도 적용되지만 앱은 메시지 알림만 검사해요.",
        "알림 접근 목록에서 Cookie Patrol을 찾아 허용해주세요. Android 설정의 주의 문구도 읽어주세요.",
        "알림 접근 목록에서 Cookie Patrol을 끄면 돼요. 다운로드·앱 설치 감시도 함께 멈춰요.",
        "숨겨진 알림이나 지원되지 않는 메신저의 내용은 검사할 수 없어요. 절전 설정에 따라 감시가 멈추고 Android 15 이상은 인증번호를 가릴 수 있어요. 전체 링크 검사에 동의하면 알림에 표시된 링크 주소가 전송돼요. ${LinkDisclosure.TRANSFER}",
    )
    val downloads = PermissionGuide(
        "다운로드된 앱 설치 파일(APK)의 이름을 확인해 경고해요. 파일 내용은 서버로 보내지 않아요. 데모에서만 넓은 파일 접근 권한을 사용해요.",
        "먼저 문자·카톡 알림 감시를 켜주세요. 그다음 모든 파일 접근 설정에서 Cookie Patrol을 허용해주세요.",
        "모든 파일 접근 설정에서 Cookie Patrol을 끄면 돼요.",
        "알림 접근 서비스가 연결돼 있을 때만 감시해요. 기종·Android 버전에 따라 새 파일을 읽지 못할 수 있어요. 권한이 켜졌다는 표시가 감지 성공을 보증하지는 않아요.",
    )
    val screen = PermissionGuide(
        "선택한 데모 기능이에요. 다른 앱 화면에 보이는 글자와 링크를 기기 안에서 읽어요. Android 14 이상은 이미지 속 글자도 읽어요.",
        "접근성 → 설치된 앱 또는 다운로드한 앱 → Cookie Patrol 화면 감시를 찾아 켜주세요. 메뉴 이름은 기종마다 달라요. 직접 설치한 앱은 Android가 제한된 설정 허용을 먼저 요구할 수 있어요.",
        "같은 접근성 설정에서 화면 감시를 끄면 돼요.",
        "화면 캡처는 OCR 처리 후 버리고 저장·전송하지 않아요. 의심 링크 전체는 서버로 전송될 수 있어요. 보안 화면·숨은 링크·QR 코드는 검사하지 못할 수 있어요. 출시 범위는 별도로 검토해야 해요.",
    )
}
