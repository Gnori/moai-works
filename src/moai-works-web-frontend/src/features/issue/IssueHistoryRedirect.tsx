import { Navigate, useLocation } from 'react-router';

import { withIssueViewTab } from './issue-view-tabs';

/**
 * 예전 리비전 화면 주소(issues/:number/history?rev=&from=&view=) — 일감 보기의 리비전 탭(issues/:number?tab=revisions&rev=…)으로 돌린다.
 * 예전 링크 · 즐겨찾기가 깨지지 않게, 고른 리비전 · 견줄 리비전 · 보기와 다른 주소 값은 그대로 가져간다.
 * 번호가 맞는지 · 읽을 수 있는지는 돌린 곳(일감 보기)이 서버 응답으로 보인다.
 */
export function IssueHistoryRedirect() {
  const { pathname, search, hash } = useLocation();
  const params = withIssueViewTab(new URLSearchParams(search), 'revisions');
  return <Navigate replace to={{ pathname: pathname.replace(/\/history\/?$/, ''), search: `?${params.toString()}`, hash }} />;
}
