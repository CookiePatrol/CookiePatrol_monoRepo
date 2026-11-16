# 합성 데이터 평가 실행 결과

실행 모드: **모의 시험 (mock)**. 실기기 결과가 아닙니다.
mock은 고정 응답으로 파이프라인 동작을 검증합니다. AI 모델의 탐지 성능을 측정한 것이 아닙니다.

정답은 팀을 위해 작성한 합성 시나리오이며, 주소의 실제 악성 판정을 뜻하지 않습니다.
동일 작성자의 합성 문장이며 개발/평가 사이에 의미가 유사한 교육 문구가 있습니다. 독립 성능 검증은 미완료입니다.

| 구성 | 집단 | TP | FP | TN | FN | 판단불가 | 정밀도 | 재현율 | F1 | 오탐률 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| rules | development | 24 | 1 | 29 | 6 | 0 | 96.00% (24/25) | 80.00% (24/30) | 87.27% (48/55) | 3.33% (1/30) |
| rules | holdout | 20 | 1 | 29 | 10 | 0 | 95.24% (20/21) | 66.67% (20/30) | 78.43% (40/51) | 3.33% (1/30) |
| rules_google | development | 25 | 1 | 29 | 5 | 0 | 96.15% (25/26) | 83.33% (25/30) | 89.29% (50/56) | 3.33% (1/30) |
| rules_google | holdout | 21 | 1 | 29 | 8 | 1 | 95.45% (21/22) | 72.41% (21/29) | 82.35% (42/51) | 3.33% (1/30) |
| rules_google_ai | development | 25 | 2 | 28 | 5 | 0 | 92.59% (25/27) | 83.33% (25/30) | 87.72% (50/57) | 6.67% (2/30) |
| rules_google_ai | holdout | 23 | 2 | 28 | 5 | 2 | 92.00% (23/25) | 82.14% (23/28) | 86.79% (46/53) | 6.67% (2/30) |

정밀도=TP/(TP+FP), 재현율=TP/(TP+FN), F1=2TP/(2TP+FP+FN), 오탐률=FP/(FP+TN).
주 혼동행렬은 판단불가를 제외합니다. JSON의 coverage와 recall_all_phishing(TP/전체 위험 수)을 함께 확인하세요.

| 변경 | 집단 | TP | FP | TN | FN |
|---|---|---:|---:|---:|---:|
| 원본 70923b4 규칙 | development | 24 | 3 | 27 | 6 |
| 원본 70923b4 규칙 | holdout | 20 | 4 | 26 | 10 |

## 지연·호출·전송

| 구성 | 집단 | 평균 ms | Google 논리 조회 | AI 논리 조회 |
|---|---|---:|---:|---:|
| rules | development | 0.061 | 0 | 0 |
| rules | holdout | 0.085 | 0 | 0 |
| rules_google | development | 0.061 | 18 | 0 |
| rules_google | holdout | 0.085 | 17 | 0 |
| rules_google_ai | development | 0.061 | 18 | 17 |
| rules_google_ai | holdout | 0.085 | 17 | 16 |

실제 외부 호출 시도: {'google': 0, 'ai': 0}
JVM local median + provider wall time; mock provider latency=0; not device/network performance
외부 전송은 후보 URL 전체입니다. 원문 문장·알림 제목·인증번호는 전송하지 않습니다. URL 속 값은 예외이므로 주의가 필요합니다.

## 오탐·미탐·판단불가

- rules/development: FP=['N26'], FN=['P25', 'P26', 'P27', 'P28', 'P29', 'P30'], unknown=[]; 상태={}
- rules/holdout: FP=['EN06'], FN=['EP17', 'EP18', 'EP19', 'EP20', 'EP21', 'EP22', 'EP23', 'EP24', 'EP25', 'EP28'], unknown=[]; 상태={}
- rules_google/development: FP=['N26'], FN=['P25', 'P26', 'P27', 'P29', 'P30'], unknown=[]; 상태={'not_detected': 17, 'dangerous': 1}
- rules_google/holdout: FP=['EN06'], FN=['EP17', 'EP18', 'EP19', 'EP20', 'EP21', 'EP23', 'EP24', 'EP25'], unknown=['EP28']; 상태={'not_detected': 15, 'dangerous': 1, 'unavailable': 1}
- rules_google_ai/development: FP=['N25', 'N26'], FN=['P25', 'P26', 'P27', 'P29', 'P30'], unknown=[]; 상태={'not_detected': 32, 'dangerous': 3}
- rules_google_ai/holdout: FP=['EN06', 'EN21'], FN=['EP17', 'EP18', 'EP19', 'EP24', 'EP25'], unknown=['EP23', 'EP28']; 상태={'not_detected': 26, 'dangerous': 4, 'rate_limited': 1, 'unavailable': 1, 'timeout': 1}

## AI를 더했을 때 바뀐 사례

- N25: not_detected → dangerous (악화 또는 판단불가; mock)
- EN21: not_detected → dangerous (악화 또는 판단불가; mock)
- EP20: not_detected → dangerous (개선; mock)
- EP21: not_detected → dangerous (개선; mock)
- EP23: not_detected → unknown (악화 또는 판단불가; mock)

## 사례별 결과

입력·정답 근거·합성 표시·평가 유형은 데이터 원본을 참고하세요.

| ID | 집단 | 정답 | 원본 점수 | 수정 점수 | 신호 | 후보 | 규칙 | +Google | +AI |
|---|---|---|---:|---:|---|---|---|---|---|
| N01 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N02 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N03 | development | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| N04 | development | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| N05 | development | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| N06 | development | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| N07 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N08 | development | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| N09 | development | False | 2 | 2 | family | False | not_detected | not_detected | not_detected |
| N10 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N11 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N12 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N13 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N14 | development | False | 4 | 2 | family | False | not_detected | not_detected | not_detected |
| N15 | development | False | 3 | 0 |  | False | not_detected | not_detected | not_detected |
| N16 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N17 | development | False | 2 | 2 | suspension | False | not_detected | not_detected | not_detected |
| N18 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N19 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N20 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N21 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N22 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N23 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N24 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N25 | development | False | 2 | 2 | idn | True | not_detected | not_detected | dangerous |
| N26 | development | False | 4 | 4 | http, ip_host | True | dangerous | dangerous | dangerous |
| N27 | development | False | 2 | 2 | lure_words | True | not_detected | not_detected | not_detected |
| N28 | development | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| N29 | development | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| N30 | development | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| P01 | development | True | 4 | 4 | short_url, lure_words | True | dangerous | dangerous | dangerous |
| P02 | development | True | 9 | 9 | http, ip_host, apk, lure_words | True | dangerous | dangerous | dangerous |
| P03 | development | True | 3 | 3 | http, lure_words | True | dangerous | dangerous | dangerous |
| P04 | development | True | 3 | 3 | http, lure_words | True | dangerous | dangerous | dangerous |
| P05 | development | True | 4 | 4 | family, credential | False | dangerous | dangerous | dangerous |
| P06 | development | True | 4 | 4 | family, transfer | False | dangerous | dangerous | dangerous |
| P07 | development | True | 4 | 4 | family, credential | False | dangerous | dangerous | dangerous |
| P08 | development | True | 4 | 4 | family, transfer | False | dangerous | dangerous | dangerous |
| P09 | development | True | 4 | 4 | short_url, suspension | True | dangerous | dangerous | dangerous |
| P10 | development | True | 4 | 4 | credential, suspension | False | dangerous | dangerous | dangerous |
| P11 | development | True | 4 | 4 | credential, suspension | False | dangerous | dangerous | dangerous |
| P12 | development | True | 3 | 3 | http, suspension | True | dangerous | dangerous | dangerous |
| P13 | development | True | 4 | 4 | short_url, lure_words | True | dangerous | dangerous | dangerous |
| P14 | development | True | 4 | 4 | lure_words, credential | False | dangerous | dangerous | dangerous |
| P15 | development | True | 3 | 3 | apk | True | dangerous | dangerous | dangerous |
| P16 | development | True | 6 | 6 | ip_host, apk | True | dangerous | dangerous | dangerous |
| P17 | development | True | 3 | 3 | userinfo | True | dangerous | dangerous | dangerous |
| P18 | development | True | 5 | 5 | lure_words, identity | False | dangerous | dangerous | dangerous |
| P19 | development | True | 3 | 3 | identity | False | dangerous | dangerous | dangerous |
| P20 | development | True | 4 | 4 | family, transfer | False | dangerous | dangerous | dangerous |
| P21 | development | True | 4 | 4 | credential, suspension | True | dangerous | dangerous | dangerous |
| P22 | development | True | 4 | 4 | idn, lure_words | True | dangerous | dangerous | dangerous |
| P23 | development | True | 3 | 3 | ip_host | True | dangerous | dangerous | dangerous |
| P24 | development | True | 3 | 3 | apk | True | dangerous | dangerous | dangerous |
| P25 | development | True | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| P26 | development | True | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| P27 | development | True | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| P28 | development | True | 2 | 2 | lure_words | True | not_detected | dangerous | dangerous |
| P29 | development | True | 2 | 2 | transfer | False | not_detected | not_detected | not_detected |
| P30 | development | True | 2 | 2 | family | False | not_detected | not_detected | not_detected |
| EN01 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN02 | holdout | False | 2 | 2 | lure_words | True | not_detected | not_detected | not_detected |
| EN03 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN04 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN05 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN06 | holdout | False | 4 | 4 | http, ip_host | True | dangerous | dangerous | dangerous |
| EN07 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN08 | holdout | False | 2 | 2 | family | False | not_detected | not_detected | not_detected |
| EN09 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN10 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN11 | holdout | False | 4 | 2 | family | False | not_detected | not_detected | not_detected |
| EN12 | holdout | False | 3 | 0 |  | False | not_detected | not_detected | not_detected |
| EN13 | holdout | False | 2 | 2 | suspension | False | not_detected | not_detected | not_detected |
| EN14 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN15 | holdout | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| EN16 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN17 | holdout | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| EN18 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN19 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN20 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN21 | holdout | False | 2 | 2 | idn | True | not_detected | not_detected | dangerous |
| EN22 | holdout | False | 2 | 2 | lure_words | False | not_detected | not_detected | not_detected |
| EN23 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN24 | holdout | False | 4 | 2 | suspension | False | not_detected | not_detected | not_detected |
| EN25 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN26 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN27 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN28 | holdout | False | 2 | 2 | family | False | not_detected | not_detected | not_detected |
| EN29 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EN30 | holdout | False | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EP01 | holdout | True | 3 | 3 | apk | True | dangerous | dangerous | dangerous |
| EP02 | holdout | True | 3 | 3 | http, lure_words | True | dangerous | dangerous | dangerous |
| EP03 | holdout | True | 4 | 4 | family, transfer | False | dangerous | dangerous | dangerous |
| EP04 | holdout | True | 4 | 4 | family, credential | False | dangerous | dangerous | dangerous |
| EP05 | holdout | True | 4 | 4 | credential, suspension | False | dangerous | dangerous | dangerous |
| EP06 | holdout | True | 4 | 4 | credential, suspension | False | dangerous | dangerous | dangerous |
| EP07 | holdout | True | 3 | 3 | apk | True | dangerous | dangerous | dangerous |
| EP08 | holdout | True | 3 | 3 | userinfo | True | dangerous | dangerous | dangerous |
| EP09 | holdout | True | 3 | 3 | identity | False | dangerous | dangerous | dangerous |
| EP10 | holdout | True | 3 | 3 | identity | False | dangerous | dangerous | dangerous |
| EP11 | holdout | True | 4 | 4 | lure_words, credential | False | dangerous | dangerous | dangerous |
| EP12 | holdout | True | 3 | 3 | ip_host | True | dangerous | dangerous | dangerous |
| EP13 | holdout | True | 4 | 4 | http, ip_host | True | dangerous | dangerous | dangerous |
| EP14 | holdout | True | 4 | 4 | family, credential | False | dangerous | dangerous | dangerous |
| EP15 | holdout | True | 3 | 3 | apk | True | dangerous | dangerous | dangerous |
| EP16 | holdout | True | 4 | 4 | lure_words, credential | False | dangerous | dangerous | dangerous |
| EP17 | holdout | True | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EP18 | holdout | True | 0 | 0 |  | False | not_detected | not_detected | not_detected |
| EP19 | holdout | True | 2 | 2 | transfer | False | not_detected | not_detected | not_detected |
| EP20 | holdout | True | 2 | 2 | suspension | True | not_detected | not_detected | dangerous |
| EP21 | holdout | True | 2 | 2 | lure_words | True | not_detected | not_detected | dangerous |
| EP22 | holdout | True | 2 | 2 | lure_words | True | not_detected | dangerous | dangerous |
| EP23 | holdout | True | 2 | 2 | lure_words | True | not_detected | not_detected | unknown |
| EP24 | holdout | True | 2 | 2 | family | False | not_detected | not_detected | not_detected |
| EP25 | holdout | True | 2 | 2 | credential | False | not_detected | not_detected | not_detected |
| EP26 | holdout | True | 4 | 4 | idn, lure_words | True | dangerous | dangerous | dangerous |
| EP27 | holdout | True | 3 | 3 | apk | True | dangerous | dangerous | dangerous |
| EP28 | holdout | True | 2 | 2 | lure_words | True | not_detected | unknown | unknown |
| EP29 | holdout | True | 3 | 3 | identity | False | dangerous | dangerous | dangerous |
| EP30 | holdout | True | 6 | 6 | credential, transfer, suspension | False | dangerous | dangerous | dangerous |

## 분리 검토

정확히 같은 원문 수: 0; 유사 후보: []
URL/number normalized character 4-gram Jaccard >= 0.45; heuristic, not proof of independence

개발용 후보 가중치·임계값 비교는 아래 표와 JSON의 threshold_development에 기록합니다. 평가용 문장을 보고 선택하지 않습니다.
- 별도 의미 검토: N14 ↔ EN11: 가족 사칭 주의·인증번호 전달 금지 교육의 변형
- 별도 의미 검토: N15 ↔ EN12: 주민번호 입력·기재 금지 교육의 변형
- 별도 의미 검토: N14 ↔ EN24: 비밀번호·인증번호 제공 금지 교육이라는 유사 상황
- 별도 의미 검토: N26 ↔ EN06: 정상 내부·실습 IP 서버 안내라는 유사 상황

### 유사 상황 제외 민감도 분석

사후 의미 검토로 ['EN06', 'EN11', 'EN12', 'EN24']를 제외한 56건입니다. 최종 독립 성능이나 학습에 사용할 결과가 아닙니다.
| 규칙 | TP | FP | TN | FN |
|---|---:|---:|---:|---:|
| baseline | 20 | 0 | 26 | 10 |
| current | 20 | 0 | 26 | 10 |
이 제외에는 어려운 정상 사례도 빠지므로 남은 사례의 높은 정밀도를 개선 증거로 제시하지 마세요. 새 독립 평가용 자료가 필요합니다.

## 개발용 설정 비교

| 경고 기준 | 조회 기준 | 송금 가중치 | TP | FP | FN | F1 | URL 후보 수 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 1 | 2 | 27 | 13 | 3 | 77.14% (54/70) | 18 |
| 2 | 2 | 2 | 27 | 13 | 3 | 77.14% (54/70) | 18 |
| 2 | 3 | 2 | 27 | 13 | 3 | 77.14% (54/70) | 15 |
| 3 | 1 | 2 | 24 | 1 | 6 | 87.27% (48/55) | 18 |
| 3 | 2 | 2 | 24 | 1 | 6 | 87.27% (48/55) | 18 |
| 3 | 3 | 2 | 24 | 1 | 6 | 87.27% (48/55) | 15 |
| 4 | 1 | 2 | 16 | 1 | 14 | 68.09% (32/47) | 18 |
| 4 | 2 | 2 | 16 | 1 | 14 | 68.09% (32/47) | 18 |
| 4 | 3 | 2 | 16 | 1 | 14 | 68.09% (32/47) | 15 |
| 2 | 1 | 3 | 27 | 13 | 3 | 77.14% (54/70) | 18 |
| 2 | 2 | 3 | 27 | 13 | 3 | 77.14% (54/70) | 18 |
| 2 | 3 | 3 | 27 | 13 | 3 | 77.14% (54/70) | 15 |
| 3 | 1 | 3 | 25 | 1 | 5 | 89.29% (50/56) | 18 |
| 3 | 2 | 3 | 25 | 1 | 5 | 89.29% (50/56) | 18 |
| 3 | 3 | 3 | 25 | 1 | 5 | 89.29% (50/56) | 15 |
| 4 | 1 | 3 | 16 | 1 | 14 | 68.09% (32/47) | 18 |
| 4 | 2 | 3 | 16 | 1 | 14 | 68.09% (32/47) | 18 |
| 4 | 3 | 3 | 16 | 1 | 14 | 68.09% (32/47) | 15 |
