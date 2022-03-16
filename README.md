# Pixel - version control with Minecraft
마인크래프트 월드에서 **지형**, **(플레이어를 제외한)엔티티** 데이터를 버전관리하기 위한 서버 플러그인.  
  
백업지점, 평행세계를 만들고 그 사이를 자유롭게 이동하거나 시간을 되돌려 그 이후의 사항을 삭제할 수 있습니다.  
서버 환경에 직접 접속하여 파일을 복사하거나 하는 과정 없이 커맨드를 통해 간편하게 월드의 지형, 엔티티 데이터를 백업하고 복원해보세요!

![미리보기](pixel_preview.png)

## 이 문서의 내용
[서버에 추가](#서버에-추가)  
[일반적인 사용](#일반적인-사용)  
&nbsp;&nbsp;&nbsp;&nbsp;[백업지점 만들기](#백업지점-만들기)  
&nbsp;&nbsp;&nbsp;&nbsp;[특정 백업지점으로 시간을 되돌리기](#특정-백업지점으로-시간을-되돌리기)  
&nbsp;&nbsp;&nbsp;&nbsp;[N개 뒤의 백업지점으로 시간을 되돌리기](#N개-뒤의-백업지점으로-시간을-되돌리기)  
&nbsp;&nbsp;&nbsp;&nbsp;[마지막 백업지점 이후의 변경사항만 삭제하기](#마지막-백업지점-이후의-변경사항만-삭제하기)  
&nbsp;&nbsp;&nbsp;&nbsp;[백업지점 목록 확인하기](#백업지점-목록-확인하기)  
[자주 묻는 질문](#자주-묻는-질문)  
&nbsp;&nbsp;&nbsp;&nbsp;[백업지점이 많아지면 용량 많이 잡아먹나요?](#백업지점이-많아지면-용량-많이-잡아먹나요)  
&nbsp;&nbsp;&nbsp;&nbsp;[속도는 어떤가요?](#속도는-어떤가요)  
&nbsp;&nbsp;&nbsp;&nbsp;[커맨드 썼더니 갑자기 아무것도 없는 세계로 이동되었어요!](#커맨드-썼더니-갑자기-아무것도-없는-세계로-이동되었어요)  
[사용 가능한 모든 커맨드](#사용-가능한-모든-커맨드)  
[이 플러그인에 대해 더 알아보기](#이-플러그인에-대해-더-알아보기)

## 서버에 추가
1. 플러그인 jar 파일을 서버의 `/plugins` 디렉터리에 배치하고, 서버를 켭니다.  
   월드의 생성이 모두 끝나고 서버가 정상적으로 켜지면 다음 과정을 수행합니다.
  
  
2. 서버의 콘솔에서 다음 명령을 입력합니다.  
   `pixel allow 당신의_플레이어_이름`
  
  
3. 마인크래프트 클라이언트에서 다음 명령을 입력합니다.  
   `/pixel init all`  
   `/pixel commit all "Initial Commit"`
  
    
4. 처음 플러그인을 추가하고 초기화하는 과정은 시간이 조금 걸리므로 여유를 가지고 수행해주세요.  
   이 과정을 수행하는 동안 플레이어가 잠시 **공허 세계**로 이동될 수 있습니다.  
   위의 과정이 끝나고 'successfully committed ~~' 메시지가 뜨는지 확인합니다.  

끝났습니다! 이제 여러분은 아래의 커맨드를 사용해 월드의 일부 데이터를 버전관리할 수 있게 되었습니다.  

## 일반적인 사용
아래 내용은 오버월드(world_overworld)를 기준으로 설명하고 있고, 다른 월드를 백업하기 원한다면 `world_overworld` 인수 대신 다른 월드의 이름을 전달하면 됩니다.

### 백업지점 만들기
오버월드에 대한 새로운 백업지점을 만드려면 다음 커맨드를 사용합니다.  
  
`/pixel commit world_overworld "수선 사서 주민을 만들었다"`  
  
마지막 인수는 백업지점을 구분할 수 있는, 현재 백업지점의 상황에 대한 간단한 요약이면 됩니다.

### 특정 백업지점으로 시간을 되돌리기
오버월드에서 특정 시점 이후의 모든 변경사항을 삭제하고 그 시점으로 돌아가려면 다음 커맨드를 사용합니다.  
  
`/pixel reset world_overworld <돌아가려는 백업지점의 고유 해시>`  
  
마지막 인수는 [여기](#백업지점-목록-확인하기)에 서술된 커맨드를 사용하여 확인할 수 있습니다.  

### N개 뒤의 백업지점으로 시간을 되돌리기
오버월드에서 뒤로 N개의 백업지점으로 시간을 되돌리려면 다음 커맨드를 사용합니다.  
  
`/pixel reset world_overworld <되돌릴 백업지점의 수>`
  
1을 입력하면, 마지막으로 만든 백업지점이 삭제되고 그 이전 백업지점으로 시간을 되돌립니다.

### 마지막 백업지점 이후의 변경사항만 삭제하기
오버월드에서 마지막으로 백업지점을 만든 이후의 모든 변경사항만 지우려면 다음 커맨드를 사용합니다.
  
`/pixel discard world_overworld`

### 백업지점 목록 확인하기
오버월드에 만들어진 백업지점의 목록을 확인하려면 다음 커맨드를 사용합니다.  
  
`/pixel list commits world_overworld`  
  
각 항목은 '날짜, 시간, 고유 해시, 메시지'를 나타냅니다.

### 더 많은 기능
시간을 되돌리지 않고(특정 시점 이후의 변경사항을 지우지 않고) 잠깐 타임머신처럼 시간여행만 하고싶거나, 시간여행중인 시점에서 또다른 평행세계를 만들고싶다면,
[평행세계와 시간여행](/docs/BRANCH_AND_CHECKOUT.md) 문서를 참고해주세요!

## 자주 묻는 질문
### 백업지점이 많아지면 용량 많이 잡아먹나요?
네, 월드 데이터를 직접 복사하여 관리하는 것 보다는 적지만, 그래도 많이 차지합니다.  
그래서 '아, 이제 이전 데이터는 필요 없겠다'싶은 시점을 잘 찾아서 이전 기록을 날려주는 `/pixel init true` 커맨드를 사용하거나, 
단 하나의 백업지점과 현재만 있어도 된다면 `-amend` 옵션을 사용한 `/pixel commit` 커맨드를 사용해야합니다.

### 속도는 어떤가요?
월드 하나를 처리하는데, 병합을 제외하고 일반적인 경우 5초~30초 내외로 완료됩니다.  
서버가 켜질 때 월드를 로드하는 그 속도와 비슷하다고 보시면 됩니다.

### 커맨드 썼더니 갑자기 아무것도 없는 세계로 이동되었어요!
월드를 언로드하여 데이터를 읽기 위한 작업으로, 일반적인 경우 작업이 완료되면 다시 기존 월드로 복귀됩니다.  
혹 오류가 발생하여 아무것도 없는 세계에 남겨졌다면, `/pixel tp` 커맨드를 사용하여 기존 월드로 복귀할 수 있습니다.

## 사용 가능한 모든 커맨드
꺽쇠괄호로 감싼 인수는 필수 인수이고, 대괄호로 감싼 인수는 선택 인수입니다.

### `/pixel` 커맨드 사용 권한 관리
- ```pixel allow <player>```  
  <b>서버 콘솔에서만 사용 가능</b>  
  특정 사용자가 아래의 나머지 `/pixel` 커맨드를 사용할 수 있도록 허용합니다.
  - player : `/pixel` 커맨드 사용을 허용할 플레이어의 이름
  

- ```pixel deny <player>```  
  <b>서버 콘솔에서만 사용 가능</b>  
  특정 사용자가 아래의 나머지 `/pixel` 커맨드를 사용할 수 없도록 권한을 취소합니다.
  - player : `/pixel` 커맨드 사용 권한을 취소할 플레이어의 이름

### 플러그인 초기화
- ```/pixel init <world> [force]```  
  플러그인 및 저장소를 초기화합니다.  
  - world : 버전관리를 사용할 월드. `all` 을 사용하면 모든 월드를 한 번에 초기화할 수 있습니다.
  - force: 이 값이 `true`로 지정되고 이미 초기화한 적이 있을 경우 지정한 월드의 모든 관리 기록이 삭제되고 다시 초기화됩니다.

### 새로운 백업지점 만들기
- ```/pixel commit <world> [-amend] <commit_message>```  
  현재 평행세계의 새로운 백업지점을 만듭니다.
   - world : 백업지점을 만들 월드
   - -amend : 현재 상황으로 직전에 만든 백업지점을 덮어쓰려면 이 값을 전달
   - commit_message : 백업지점에 붙힐 메시지 (예시: "집을 지었음")

### 시간 되돌리기
- ```/pixel reset <world> <commit|steps>```  
  시간을 **뒤로 되돌립니다**. 타겟 지점보다 뒤에 이루어진 모든 변경사항이 삭제됩니다.
   - world : 되감기를 반영할 월드
   - steps : 뒤로 몇 백업지점을 되감아 지울지에 대한 값 (예시: 마지막 백업지점의 바로 이전 백업지점으로 되감으려면 1)
   - commit : 되감기하여 돌아갈 백업지점의 고유 해시


- ```/pixel discard <world>```  
  마지막 백업지점으로 월드를 되감기합니다.
   - world : 변경사항을 취소할 월드

### 새로운 평행세계 만들기
- ```/pixel branch <world> <new_branch|-d> [branch_to_delete]```  
  현재의 백업지점을 현재로 하는 새로운 평행세계를 만들거나, 혹은 현재 속해있지 않은 이미 있는 평행세계 하나를 지웁니다.
   - world : 평행세계를 만들 월드
   - new_branch : 생성할 평행세계의 이름
   - -d : 평행세계를 삭제하려는 경우 해당 값을 인수에 전달
   - branch_to_delete : 첫 인수가 `-d` 일 경우 반드시 전달해야함. 삭제할 평행세계의 이름.

### 백업지점 및 평행세계 사이의 이동
- ```/pixel checkout <world> <branch|commit> <committed>```  
  특정 백업지점, 혹은 평행세계 사이를 **이동**합니다.  
  타겟 이후의 변경사항이 삭제되지 않습니다.
   - world : 시간이동 이후의 상황을 반영할 월드
   - branch : 시간이동으로 이동할 타겟 브랜치
   - commit : 시간이동으로 이동할 타겟 백업지점의 고유 해시
   - committed : 시간이동하기 전에 현재까지의 변경사항에 대한 백업지점을 만들었는지에 대한 확인


- ```/pixel checkout <world> -recover```  
  버전관리 데이터가 꼬였을 것으로 예상되는 경우, 마지막 커밋 상태로 버전관리 데이터를 clean 할 수 있습니다.

### 평행세계 합치기
- ```/pixel merge <world> <branch|commit> <mode> <committed>```  
  두 평행세계를 합칩니다. **위험한 기능이므로 사용에 주의해주십시오**
   - world : 병합을 진행할 월드
   - branch : 병합을 진행할 source 평행세계. (A를 B에 병합한다고 할 때 A를 말함)
   - commit : 병합을 진행할 source 백업지점의 고유 해시. (A를 B에 병합한다고 할 때 A를 말함)
   - mode : keep 혹은 replace 중 하나. keep 일 경우 병합 충돌 발생 시 현재 평행세계의 값을, replace 일 경우 source 평행세계의 값을 반영합니다.
   - committed : 병합하기 전에 현재까지의 변경사항에 대한 백업지점을 만들었는지에 대한 확인


- ```/pixel merge abort```  
  병합을 중단합니다. <b>병합이 현재 진행중일 때만 수행 가능합니다.</b>

### 백업지점 및 평행세계 목록 확인
- ```/pixel list <what> <world> [page]```  
  백업지점, 혹은 평행세계의 목록을 출력합니다. 백업지점의 경우 **현재 평행세계에 포함된 것만** 출력합니다.
   - what : commits 혹은 branches 중 하나. 각각 커밋의 목록과 브랜치의 목록을 출력합니다.
   - world : 목록을 확인할 월드
   - page : what 인수가 commits 였을 경우 유효한 인수. 커밋 목록의 여러 페이지 중 몇 페이지를 보여줄지 정합니다.

### 기타 편의기능
- ```/pixel tp <player> <"dummy"|"overworld">```  
  플레이어를 지정한 월드의 동일한 좌표 위치로 텔레포트합니다.  
  사용 중 뭔가 문제가 생겨 공허 세계에 남겨졌거나, 어떠한 이유로 버전관리가 되지 않는 기존의 월드로 돌아가려면 사용합니다.
   - player : 텔레포트할 플레이어의 닉네임
   - world : 도착 월드. dummy(기존의 Overworld(<b><i>level_name</i></b> 월드)) 혹은 overworld(버전관리가 진행되는 Overworld(<b><i>level_name</i>_overworld</b> 월드)) 중 하나.


- ```/pixel whereis <player>```  
  플레이어가 현재 어느 월드에 있는지를 출력합니다.
   - player : 확인할 플레이어의 닉네임

## 이 플러그인에 대해 더 알아보기
위의 문서는 최대한 간략화된 문서입니다.  
전체 작동 방식 및 기술적인 사항을 확인하고 싶으시면 아래 문서들을 확인해보세요!  
  
기술적인 사항  
&nbsp;&nbsp;&nbsp;&nbsp;[Git과 전체적인 작동 방식](/docs/MECHANISM.md)  
&nbsp;&nbsp;&nbsp;&nbsp;[어째서 버전관리 월드를 따로 만들었는가?](/docs/DUMMY_WORLD.md)  
&nbsp;&nbsp;&nbsp;&nbsp;[왜 플레이어 데이터는 버전관리하지 않는가?](/docs/PLAYER_DATA.md)  
&nbsp;&nbsp;&nbsp;&nbsp;[병합 시의 광원으로부터의 빛 반영 방식](/docs/LIGHT_SOURCES.md)  
  
더 많은 기능  
&nbsp;&nbsp;&nbsp;&nbsp;[평행세계와 시간여행](/docs/BRANCH_AND_CHECKOUT.md)  
&nbsp;&nbsp;&nbsp;&nbsp;[월드의 병합에 대해](/docs/MERGING_WORLDS.md)  