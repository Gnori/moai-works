/** 일감 보기의 탭 — 주소에 ?tab=comments|attachments 로 남는다(내용은 기본이라 적지 않는다). 새로 고침 · 링크에도 같은 탭이 열린다 */
export const issueViewTabs = ['content', 'comments', 'attachments'] as const;
export type IssueViewTab = (typeof issueViewTabs)[number];

/** 주소의 탭 — 없거나 모르는 값이면 내용 */
export function readIssueViewTab(params: URLSearchParams): IssueViewTab {
  const value = params.get('tab');
  return value === 'comments' || value === 'attachments' ? value : 'content';
}

/** 탭을 주소에 적는다 — 내용이면 지운다. 주소의 다른 값(?settings= 등)은 그대로 둔다 */
export function withIssueViewTab(params: URLSearchParams, tab: IssueViewTab): URLSearchParams {
  if (tab === 'content') {
    params.delete('tab');
  } else {
    params.set('tab', tab);
  }
  return params;
}
