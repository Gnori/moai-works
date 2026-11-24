package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 일감 — 만들기 · 번호 · 유형 · 상태와 날짜 · 관리자와 담당자 · 상위 · 하위 · 연결 · 이력 · 댓글 · 지우기 · 목록 (design/05).
 * 판정은 프로젝트 경계(독자가 아니면 404, 코드가 없으면 403)를 그대로 쓴다.
 */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IssueTest {

  @Autowired
  TestApi api;

  TestUser owner;
  TestUser admin;
  TestUser curator;
  TestUser member;
  TestUser editor;
  TestUser outsider;
  String ws;

  static final String P = "work";
  static final String BASE = "/workspaces/{ws}/projects/{project}";

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    admin = api.user("admin");
    curator = api.user("curator");
    member = api.user("member");
    editor = api.user("editor");
    outsider = api.user("outsider");
    ws = api.teamWorkspace(owner, "일감 시험");
    api.join(ws, owner, admin, "admin");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.join(ws, owner, editor, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "업무", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    api.put(owner, BASE + "/roles/{user}", Map.of("roles", List.of("project-editor")), ws, P, editor.id())
        .andExpect(status().isOk());
  }

  private ResultActions create(TestUser by, String project, Map<String, Object> body) throws Exception {
    return api.post(by, BASE + "/issues", body, ws, project);
  }

  private int newIssue(TestUser by, String title) throws Exception {
    return TestApi.<Integer>read(create(by, P, Map.of("type", "feature", "title", title))
        .andExpect(status().isCreated()), "$.number");
  }

  private ResultActions get(TestUser as, int number) throws Exception {
    return api.get(as, "/workspaces/{ws}/issues/{n}", ws, number);
  }

  private int rowVersion(int number) throws Exception {
    return TestApi.<Integer>read(get(owner, number), "$.rowVersion");
  }

  private ResultActions patch(TestUser by, int number, Map<String, Object> changes) throws Exception {
    Map<String, Object> body = new HashMap<>(changes);
    body.putIfAbsent("rowVersion", rowVersion(number));
    return api.patch(by, "/workspaces/{ws}/issues/{n}", body, ws, number);
  }

  private static String today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
  }

  // ── 만들기 · 보기 ──────────────────────────────────────────────────

  @Test
  void 유형은_새기능_버그_테스트_기술조사이고_옛_유형은_새로_고를_수_없다() throws Exception {
    // 2026-11-22 사용자: "유형 : 새기능, 버그, 테스트, 기술조사" — 옛 요구사항 · 작업은 뒤에 "더 쓰지 않음"으로 남는다
    ResultActions types = api.get(member, BASE + "/issue-types", ws, P).andExpect(status().isOk());
    assertThat(TestApi.<List<String>>read(types, "$[?(@.active == true)].key"))
        .containsExactly("feature", "bug", "test", "research");
    assertThat(TestApi.<List<String>>read(types, "$[?(@.active == true)].name"))
        .containsExactly("새기능", "버그", "테스트", "기술조사");
    assertThat(TestApi.<List<String>>read(types, "$[?(@.active == false)].key")).containsExactly("requirement", "task");

    create(curator, P, Map.of("type", "task", "title", "옛 유형")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ISSUE_TYPE_RETIRED"));
    int n = newIssue(curator, "유형 바꾸기");
    patch(curator, n, Map.of("type", "requirement")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ISSUE_TYPE_RETIRED"));
    patch(curator, n, Map.of("type", "research")).andExpect(jsonPath("$.type").value("research"));
  }

  @Test
  void 상태는_등록_진행_테스트_완료_종료이고_테스트는_열린_상태다() throws Exception {
    // 2026-11-22 사용자: "상태 : 등록, 진행, 테스트, 완료, 종료"
    int n = newIssue(curator, "테스트 상태");
    patch(curator, n, Map.of("status", "TESTING")).andExpect(jsonPath("$.status").value("TESTING"))
        .andExpect(jsonPath("$.completedOn").doesNotExist()).andExpect(jsonPath("$.closedOn").doesNotExist());
    // 열린 일감 목록에 들어 있다
    assertThat(TestApi.<List<Integer>>read(api.get(member, BASE + "/issues?status=open&size=200", ws, P), "$.items[*].number"))
        .contains(n);
    assertThat(TestApi.<List<Integer>>read(api.get(member, BASE + "/issues?status=closed&size=200", ws, P), "$.items[*].number"))
        .doesNotContain(n);
    // 완료했다가 테스트로 되돌리면 완료일을 비운다
    patch(curator, n, Map.of("status", "DONE")).andExpect(jsonPath("$.completedOn").value(today()));
    patch(curator, n, Map.of("status", "TESTING")).andExpect(jsonPath("$.completedOn").doesNotExist());
  }

  // ── 보류 · 취소 (2026-11-24 사용자: "이슈 상태에 취소, 보류도 만들어" — 일감 #152) ─────────────

  private List<Integer> numbers(String query) throws Exception {
    return TestApi.read(api.get(member, BASE + "/issues?size=200&" + query, ws, P), "$.items[*].number");
  }

  @Test
  void 보류는_까닭이_있어야_옮기고_열린_일감이며_날짜를_채우지_않고_기한_지남은_센다() throws Exception {
    String yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString();
    int n = TestApi.<Integer>read(create(curator, P, Map.of("type", "feature", "title", "결정 기다림", "dueDate", yesterday))
        .andExpect(status().isCreated()), "$.number");
    // 까닭 없이는 옮기지 않는다(빈칸만 있어도 없는 것) — 일감은 그대로
    patch(curator, n, Map.of("status", "ON_HOLD")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STATUS_REASON_REQUIRED"));
    patch(curator, n, Map.of("status", "ON_HOLD", "notes", "   ")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STATUS_REASON_REQUIRED"));
    get(owner, n).andExpect(jsonPath("$.status").value("NEW"));

    patch(curator, n, Map.of("status", "ON_HOLD", "notes", "디자인 결정을 기다린다")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ON_HOLD"))
        .andExpect(jsonPath("$.completedOn").doesNotExist()).andExpect(jsonPath("$.closedOn").doesNotExist())
        .andExpect(jsonPath("$.overdue").value(true));
    // 까닭은 상태 변경과 같은 이력 줄의 댓글이다
    ResultActions view = get(owner, n);
    List<Map<String, Object>> journal = TestApi.read(view, "$.journal");
    Map<String, Object> last = journal.get(journal.size() - 1);
    assertThat(last.get("notes")).isEqualTo("디자인 결정을 기다린다");
    assertThat(TestApi.<List<String>>read(view, "$.journal[-1].changes[*].field")).containsExactly("status");
    assertThat(TestApi.<List<String>>read(view, "$.journal[-1].changes[*].after")).containsExactly("ON_HOLD");

    // 열린 묶음 · 기한 지남에 들고 닫힌 묶음에는 없다
    assertThat(numbers("status=open")).contains(n);
    assertThat(numbers("status=closed")).doesNotContain(n);
    assertThat(numbers("overdue=true")).contains(n);
    assertThat(numbers("status=ON_HOLD")).containsExactly(n);

    // 보류에서 나가는 것은 까닭이 없어도 된다
    patch(curator, n, Map.of("status", "IN_PROGRESS")).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    // 완료했다가 보류로 되돌리면 다른 열린 상태처럼 완료일을 비운다
    patch(curator, n, Map.of("status", "DONE")).andExpect(jsonPath("$.completedOn").value(today()))
        .andExpect(jsonPath("$.overdue").value(false));
    patch(curator, n, Map.of("status", "ON_HOLD", "notes", "다시 확인할 것이 생겼다"))
        .andExpect(jsonPath("$.completedOn").doesNotExist()).andExpect(jsonPath("$.closedOn").doesNotExist());
    // 이미 보류면 상태를 다시 주어도 까닭이 필요 없다(바뀐 것이 없다)
    patch(curator, n, Map.of("status", "ON_HOLD", "title", "결정 기다림 (보류)")).andExpect(status().isOk());
  }

  @Test
  void 취소는_까닭이_있어야_하고_닫힌_일감이며_종료일을_채우고_완료일은_비운다() throws Exception {
    String yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString();
    int n = TestApi.<Integer>read(create(curator, P, Map.of("type", "bug", "title", "중복 버그", "dueDate", yesterday))
        .andExpect(status().isCreated()), "$.number");
    patch(curator, n, Map.of("status", "CANCELLED")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STATUS_REASON_REQUIRED"));
    patch(curator, n, Map.of("status", "CANCELLED", "notes", "#1 과 같은 버그")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.closedOn").value(today())).andExpect(jsonPath("$.completedOn").doesNotExist())
        .andExpect(jsonPath("$.overdue").value(false));
    assertThat(numbers("status=closed")).contains(n);
    assertThat(numbers("status=open")).doesNotContain(n);
    assertThat(numbers("overdue=true")).doesNotContain(n);

    // 다시 열면(등록) 까닭 없이 되고 종료일을 비운다
    patch(curator, n, Map.of("status", "NEW")).andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.closedOn").doesNotExist()).andExpect(jsonPath("$.completedOn").doesNotExist());

    // 완료한 뒤 취소하면 완료일을 비운다 — 취소는 끝낸 것이 아니다
    patch(curator, n, Map.of("status", "DONE")).andExpect(jsonPath("$.completedOn").value(today()));
    patch(curator, n, Map.of("status", "CANCELLED", "notes", "요구가 없어졌다"))
        .andExpect(jsonPath("$.completedOn").doesNotExist()).andExpect(jsonPath("$.closedOn").value(today()));
    // 취소했던 일을 결국 끝내면 완료일을 채우고, 취소한 날(종료일)은 비운다
    patch(curator, n, Map.of("status", "DONE")).andExpect(jsonPath("$.completedOn").value(today()))
        .andExpect(jsonPath("$.closedOn").doesNotExist());
    // 종료 → 취소: 완료일은 비우고, 같은 요청에 직접 준 종료일은 그대로 쓴다
    patch(curator, n, Map.of("status", "CLOSED")).andExpect(jsonPath("$.closedOn").value(today()));
    patch(curator, n, Map.of("status", "CANCELLED", "notes", "종료가 아니라 취소였다", "closedOn", "2026-11-02"))
        .andExpect(jsonPath("$.closedOn").value("2026-11-02")).andExpect(jsonPath("$.completedOn").doesNotExist());
  }

  @Test
  void 보류_취소로는_만들_수_없고_그_밖의_옮김은_까닭이_없어도_된다() throws Exception {
    for (String st : List.of("ON_HOLD", "CANCELLED")) {
      create(curator, P, Map.of("type", "feature", "title", "바로 " + st, "status", st)).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("STATUS_REASON_REQUIRED"));
    }
    int n = newIssue(curator, "까닭 없는 옮김");
    for (String st : List.of("IN_PROGRESS", "TESTING", "DONE", "CLOSED", "NEW")) {
      patch(curator, n, Map.of("status", st)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(st));
    }
  }

  @Test
  void 만들면_워크스페이스_순번을_받고_관리자는_만든_사람이다() throws Exception {
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", "numbers", "name", "번호", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    // 번호는 워크스페이스 순번이다 — 새 프로젝트라도 1 부터가 아니라 워크스페이스에서 다음 번호(ADR-030)
    int first = TestApi.read(create(curator, "numbers", Map.of("type", "feature", "title", "첫 요구사항", "description", "**왜** 필요한가"))
        .andExpect(status().isCreated()), "$.number");
    get(owner, first).andExpect(status().isOk()).andExpect(jsonPath("$.number").value(first))
        .andExpect(jsonPath("$.project.key").value("numbers"))
        .andExpect(jsonPath("$.status").value("NEW")).andExpect(jsonPath("$.type").value("feature"))
        .andExpect(jsonPath("$.manager.id").value(curator.id().toString()))
        .andExpect(jsonPath("$.createdBy.id").value(curator.id().toString()))
        .andExpect(jsonPath("$.journal[0].via").value("WEB"))
        .andExpect(jsonPath("$.journal[0].changes[?(@.field == 'title')].after").value("첫 요구사항"));
    create(curator, "numbers", Map.of("type", "bug", "title", "둘째")).andExpect(jsonPath("$.number").value(first + 1));
  }

  @Test
  void 만들기_권한은_워크스페이스_역할이나_프로젝트_역할에서_온다() throws Exception {
    create(member, P, Map.of("type", "feature", "title", "멤버는 못 만든다")).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    create(editor, P, Map.of("type", "feature", "title", "프로젝트 편집자는 만든다")).andExpect(status().isCreated());
    create(outsider, P, Map.of("type", "feature", "title", "남")).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  void 지정_멤버_프로젝트의_일감은_독자가_아니면_없는_것과_같다() throws Exception {
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", "secret", "name", "비밀", "visibility", "RESTRICTED"), ws)
        .andExpect(status().isCreated());
    int n = TestApi.<Integer>read(create(owner, "secret", Map.of("type", "feature", "title", "숨은 일감"))
        .andExpect(status().isCreated()), "$.number");
    // 정식 주소(워크스페이스 번호)로 와도 그 일감의 프로젝트로 판정한다 — 일감이 있는지도 숨긴다
    api.get(member, "/workspaces/{ws}/issues/{n}", ws, n).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    api.get(member, BASE + "/issues", ws, "secret").andExpect(status().isNotFound());
  }

  @Test
  void 없는_일감과_잘못된_값() throws Exception {
    get(owner, 99999).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    create(owner, P, Map.of("type", "epic", "title", "없는 유형")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_ISSUE_TYPE"));
    create(owner, P, Map.of("type", "feature", "title", "날짜", "startDate", "2026-12-10", "plannedEndDate", "2026-12-01"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ISSUE_DATES"));
    int n = newIssue(owner, "모르는 항목");
    patch(owner, n, Map.of("priority", "HIGH")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
  }

  // ── 고치기 · 이력 ──────────────────────────────────────────────────

  @Test
  void 고치면_이력에_바뀐_필드가_남고_옛_판으로_고치면_409() throws Exception {
    int n = newIssue(curator, "처음 제목");
    int version = rowVersion(n);
    patch(editor, n, Map.of("rowVersion", version, "title", "고친 제목", "assigneeId", editor.id().toString(),
        "dueDate", "2026-12-31", "notes", "제가 맡겠습니다")).andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("고친 제목")).andExpect(jsonPath("$.assignee.id").value(editor.id().toString()))
        .andExpect(jsonPath("$.dueDate").value("2026-12-31"))
        .andExpect(jsonPath("$.journal[1].notes").value("제가 맡겠습니다"))
        .andExpect(jsonPath("$.journal[1].via").value("WEB"))
        .andExpect(jsonPath("$.journal[1].actorId").value(editor.id().toString()));
    List<String> fields = TestApi.read(get(owner, n), "$.journal[1].changes[*].field");
    assertThat(fields).containsExactlyInAnyOrder("title", "assignee", "dueDate");

    // 다른 사람이 먼저 고친 뒤 옛 판으로 고치면 거절
    api.patch(curator, "/workspaces/{ws}/issues/{n}", Map.of("rowVersion", version, "title", "늦은 수정"), ws, n)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    api.patch(curator, "/workspaces/{ws}/issues/{n}", Map.of("title", "판 없음"), ws, n).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROW_VERSION_REQUIRED"));
  }

  @Test
  void 상태가_완료_종료가_되면_날짜를_채우고_되돌리면_비운다() throws Exception {
    int n = newIssue(curator, "날짜 자동");
    patch(curator, n, Map.of("status", "DONE")).andExpect(jsonPath("$.completedOn").value(today()))
        .andExpect(jsonPath("$.closedOn").doesNotExist());
    patch(curator, n, Map.of("status", "CLOSED")).andExpect(jsonPath("$.completedOn").value(today()))
        .andExpect(jsonPath("$.closedOn").value(today()));
    patch(curator, n, Map.of("status", "IN_PROGRESS")).andExpect(jsonPath("$.completedOn").doesNotExist())
        .andExpect(jsonPath("$.closedOn").doesNotExist());
    // 신규에서 바로 종료 — 종료일만 채운다
    patch(curator, n, Map.of("status", "CLOSED")).andExpect(jsonPath("$.closedOn").value(today()))
        .andExpect(jsonPath("$.completedOn").doesNotExist());
    patch(curator, n, Map.of("status", "IN_PROGRESS")).andExpect(status().isOk());
    patch(curator, n, Map.of("status", "WAITING")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ISSUE_STATUS"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ON_HOLD")));
    // 직접 넣은 날짜는 그대로 둔다
    patch(curator, n, Map.of("status", "DONE", "completedOn", "2026-11-01"))
        .andExpect(jsonPath("$.completedOn").value("2026-11-01"));
    patch(curator, n, Map.of("closedOn", "2026-10-01")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ISSUE_DATES"));
  }

  @Test
  void 관리자를_바꾸려면_관리자_지정_권한이_있어야_하고_독자만_관리자_담당자가_된다() throws Exception {
    int n = newIssue(editor, "관리자 바꾸기");
    patch(editor, n, Map.of("managerId", member.id().toString())).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    patch(curator, n, Map.of("managerId", member.id().toString())).andExpect(status().isOk())
        .andExpect(jsonPath("$.manager.id").value(member.id().toString()));
    patch(curator, n, Map.of("assigneeId", outsider.id().toString())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_NOT_PROJECT_READER"));
    // 담당자는 비울 수 있다
    patch(curator, n, Map.of("assigneeId", editor.id().toString())).andExpect(status().isOk());
    Map<String, Object> clear = new HashMap<>();
    clear.put("assigneeId", null);
    patch(curator, n, clear).andExpect(status().isOk()).andExpect(jsonPath("$.assignee").doesNotExist());
  }

  // ── 상위 · 하위 ────────────────────────────────────────────────────

  @Test
  void 상위_하위는_같은_프로젝트_안에서_순환과_10단계_초과를_막는다() throws Exception {
    int parent = newIssue(curator, "상위");
    int child = TestApi.<Integer>read(create(curator, P, Map.of("type", "feature", "title", "하위", "parent", parent))
        .andExpect(status().isCreated()), "$.number");
    get(owner, child).andExpect(jsonPath("$.parent.number").value(parent));
    get(owner, parent).andExpect(jsonPath("$.children[0].number").value(child));

    patch(curator, parent, Map.of("parent", child)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PARENT_CYCLE"));

    // 10단계까지는 되고 11단계는 안 된다
    int top = newIssue(curator, "1단계");
    int last = top;
    for (int level = 2; level <= 10; level++) {
      last = TestApi.<Integer>read(create(curator, P, Map.of("type", "feature", "title", level + "단계", "parent", last))
          .andExpect(status().isCreated()), "$.number");
    }
    int loose = newIssue(curator, "11단계가 될 일감");
    patch(curator, loose, Map.of("parent", last)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PARENT_DEPTH_EXCEEDED"));

    // 다른 프로젝트의 번호는 이 프로젝트에 없는 일감이다
    create(curator, P, Map.of("type", "feature", "title", "남의 상위", "parent", 99999)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PARENT_NOT_FOUND"));
  }

  // ── 연결 ───────────────────────────────────────────────────────────

  @Test
  void 연결은_양쪽에서_보이고_한_쌍에_하나다() throws Exception {
    int a = newIssue(curator, "연결 A");
    int b = newIssue(curator, "연결 B");
    api.post(editor, "/workspaces/{ws}/issues/{n}/links", Map.of("target", b, "type", "BLOCKS"), ws, a)
        .andExpect(status().isOk()).andExpect(jsonPath("$.links[0].issue.number").value(b))
        .andExpect(jsonPath("$.links[0].direction").value("OUTGOING"));
    get(owner, b).andExpect(jsonPath("$.links[0].issue.number").value(a))
        .andExpect(jsonPath("$.links[0].direction").value("INCOMING"))
        .andExpect(jsonPath("$.links[0].type").value("BLOCKS"))
        // 이력에도 이 일감 쪽에서 본 방향이 남는다 (차단됨)
        .andExpect(jsonPath("$.journal[1].changes[0].after.direction").value("INCOMING"))
        .andExpect(jsonPath("$.journal[1].changes[0].after.number").value(a));

    api.post(editor, "/workspaces/{ws}/issues/{n}/links", Map.of("target", a, "type", "RELATES"), ws, b)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ISSUE_LINK_EXISTS"));
    api.post(editor, "/workspaces/{ws}/issues/{n}/links", Map.of("target", a, "type", "RELATES"), ws, a)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ISSUE_LINK"));
    api.post(member, "/workspaces/{ws}/issues/{n}/links", Map.of("target", b, "type", "RELATES"), ws, a)
        .andExpect(status().isForbidden());
    api.post(editor, "/workspaces/{ws}/issues/{n}/links", Map.of("target", 99999, "type", "RELATES"), ws, a)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LINK_TARGET_NOT_FOUND"));

    String linkId = TestApi.read(get(owner, a), "$.links[0].id");
    api.delete(editor, "/workspaces/{ws}/issues/{n}/links/{id}", ws, b, linkId).andExpect(status().isOk())
        .andExpect(jsonPath("$.links").isEmpty());
    get(owner, a).andExpect(jsonPath("$.links").isEmpty());
  }

  // ── 댓글 ───────────────────────────────────────────────────────────

  @Test
  void 멤버는_댓글을_달_수_있다() throws Exception {
    int n = newIssue(curator, "댓글");
    api.post(member, "/workspaces/{ws}/issues/{n}/comments", Map.of("notes", "확인했습니다"), ws, n)
        .andExpect(status().isOk()).andExpect(jsonPath("$.journal[1].notes").value("확인했습니다"))
        .andExpect(jsonPath("$.journal[1].changes").isEmpty());
    api.post(member, "/workspaces/{ws}/issues/{n}/comments", Map.of("notes", " "), ws, n).andExpect(status().isBadRequest());
  }

  // ── 지우기 ─────────────────────────────────────────────────────────

  @Test
  void 지우기는_삭제_권한이_있어야_하고_하위가_있으면_막는다() throws Exception {
    int parent = newIssue(curator, "지울 상위");
    int child = TestApi.<Integer>read(create(curator, P, Map.of("type", "feature", "title", "지울 하위", "parent", parent))
        .andExpect(status().isCreated()), "$.number");
    api.delete(curator, "/workspaces/{ws}/issues/{n}", ws, child).andExpect(status().isForbidden());
    api.delete(admin, "/workspaces/{ws}/issues/{n}", ws, parent).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ISSUE_HAS_CHILDREN"));
    api.delete(admin, "/workspaces/{ws}/issues/{n}", ws, child).andExpect(status().isNoContent());
    get(owner, child).andExpect(status().isNotFound());
    api.delete(admin, "/workspaces/{ws}/issues/{n}", ws, parent).andExpect(status().isNoContent());

    List<String> actions = TestApi.read(api.get(owner, "/workspaces/{ws}/audit?limit=500", ws), "$[*].action");
    assertThat(actions).contains("issue.create", "issue.delete", "issue.update", "issue.comment", "issue.link.add",
        "issue.link.remove");
  }

  // ── 목록 ───────────────────────────────────────────────────────────

  @Test
  void 목록은_상태_유형_담당자_검색어로_거르고_정렬한다() throws Exception {
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", "listing", "name", "목록", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    int login = TestApi.read(create(owner, "listing", Map.of("type", "bug", "title", "로그인 버그", "assigneeId",
        curator.id().toString(), "dueDate", "2026-12-01")).andExpect(status().isCreated()), "$.number");
    create(owner, "listing", Map.of("type", "feature", "title", "보고서 기능", "dueDate", "2026-11-25"))
        .andExpect(status().isCreated());
    create(owner, "listing", Map.of("type", "bug", "title", "끝난 버그", "status", "DONE")).andExpect(status().isCreated());

    String list = BASE + "/issues";
    assertThat(TestApi.<List<String>>read(api.get(member, list + "?type=bug", ws, "listing"), "$.items[*].title"))
        .containsExactlyInAnyOrder("로그인 버그", "끝난 버그");
    assertThat(TestApi.<List<String>>read(api.get(member, list + "?status=NEW&status=IN_PROGRESS", ws, "listing"),
        "$.items[*].title")).containsExactlyInAnyOrder("로그인 버그", "보고서 기능");
    assertThat(TestApi.<List<String>>read(api.get(member, list + "?assignee=none", ws, "listing"), "$.items[*].title"))
        .containsExactlyInAnyOrder("보고서 기능", "끝난 버그");
    assertThat(TestApi.<List<String>>read(api.get(member, list + "?q=보고서", ws, "listing"), "$.items[*].title"))
        .containsExactly("보고서 기능");
    assertThat(TestApi.<List<String>>read(api.get(member, list + "?q={q}", ws, "listing", "#" + login), "$.items[*].title"))
        .containsExactly("로그인 버그");
    assertThat(TestApi.<List<String>>read(api.get(member, list + "?sort=due", ws, "listing"), "$.items[*].title"))
        .containsExactly("보고서 기능", "로그인 버그", "끝난 버그");
    api.get(member, list + "?size=2", ws, "listing").andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.items.length()").value(2));
    api.get(member, list + "?sort=priority", ws, "listing").andExpect(status().isBadRequest());
    // 별칭 — open(신규 · 진행) · me(나) · none(맨 위)
    assertThat(TestApi.<List<String>>read(api.get(member, list + "?status=open", ws, "listing"), "$.items[*].title"))
        .containsExactlyInAnyOrder("로그인 버그", "보고서 기능");
    assertThat(TestApi.<List<String>>read(api.get(curator, list + "?status=open&assignee=me", ws, "listing"),
        "$.items[*].title")).containsExactly("로그인 버그");
    api.get(member, list + "?parent=none", ws, "listing").andExpect(jsonPath("$.total").value(3));
    api.get(member, list + "?status=OPENED", ws, "listing").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
  }

  // ── 보관한 프로젝트 ────────────────────────────────────────────────

  @Test
  void 보관한_프로젝트의_일감은_바꿀_수_없다() throws Exception {
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", "frozen", "name", "얼음", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    int n = TestApi.<Integer>read(create(owner, "frozen", Map.of("type", "feature", "title", "남은 일"))
        .andExpect(status().isCreated()), "$.number");
    api.post(owner, "/workspaces/{ws}/projects/{project}/archive", null, ws, "frozen").andExpect(status().isOk());
    // 관리하는 사람은 읽을 수 있지만 고칠 수는 없다
    api.get(owner, "/workspaces/{ws}/issues/{n}", ws, n).andExpect(status().isOk());
    create(owner, "frozen", Map.of("type", "feature", "title", "새 일")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_WRITABLE"));
  }
}
