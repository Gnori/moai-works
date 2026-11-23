/**
 * 일감 보기의 탭 — 주소에 ?tab=comments|attachments|revisions 로 남는다(내용은 기본이라 적지 않는다). 새로 고침 · 링크에도 같은 탭이 열린다.
 * 리비전 탭에서 고른 리비전(?rev= · from= · view=)은 탭을 옮겨도 주소에 그대로 둔다 — 돌아오면 보던 리비전이 다시 열린다(쓰던 댓글이 남는 것과 같다).
 */
export const issueViewTabs = ['content', 'comments', 'attachments', 'revisions'] as const;
export type IssueViewTab = (typeof issueViewTabs)[number];

/** 주소의 탭 — 없거나 모르는 값이면 내용 */
export function readIssueViewTab(params: URLSearchParams): IssueViewTab {
  const value = params.get('tab');
  return value === 'comments' || value === 'attachments' || value === 'revisions' ? value : 'content';
}

/** 탭을 주소에 적는다 — 내용이면 지운다. 주소의 다른 값(?settings= · rev= 등)은 그대로 둔다 */
export function withIssueViewTab(params: URLSearchParams, tab: IssueViewTab): URLSearchParams {
  if (tab === 'content') {
    params.delete('tab');
  } else {
    params.set('tab', tab);
  }
  return params;
}
