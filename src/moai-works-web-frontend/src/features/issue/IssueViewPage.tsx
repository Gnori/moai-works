import AddRounded from '@mui/icons-material/AddRounded';
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import EditRounded from '@mui/icons-material/EditRounded';
import ExpandLessRounded from '@mui/icons-material/ExpandLessRounded';
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import SubdirectoryArrowRightRounded from '@mui/icons-material/SubdirectoryArrowRightRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import LinearProgress from '@mui/material/LinearProgress';
import Link from '@mui/material/Link';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useParams, useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { issueEditPath, issuePath, issueRevisionsPath, newIssuePath, projectPath } from '@/app/routes';
import { useProject } from '@/features/project/project-context';
import { projectCan } from '@/features/project/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import { issueStatuses, type IssueDetail, type IssueStatus } from '@/shared/api/types';
import { date, dateTimeMs, fromNow } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { useElementWidth } from '@/shared/lib/use-element-width';
import { ErrorState, LoadingState } from '@/shared/ui/States';
import { layout } from '@/theme/tokens';

import { IssueAttachmentsProvider } from './attachment-context';
import { canUploadAttachment, countComments } from './attachments';
import { personName, useIssueTypeName } from './issue-labels';
import { IssueAttachments } from './IssueAttachments';
import { CommentBox, IssueHistory } from './IssueHistory';
import { IssueNotFound } from './IssueLayout';
import { IssueLinks, IssueRefRow } from './IssueLinks';
import { IssueRevisions } from './IssueRevisions';
import { IssueStatusChip } from './IssueStatusChip';
import { readIssueViewTab, withIssueViewTab, type IssueViewTab } from './issue-view-tabs';
import { IssueWorkLogs } from './IssueWorkLogs';
import { MarkdownView } from './MarkdownView';
import { parseIssueNumber, storeIssue, useAttachmentsQuery, useIssueQuery } from './queries';
import { RemoveIssueDialog } from './RemoveIssueDialog';
import { IssueTagFields } from './IssueTags';
import { formatHours, spentProgress } from './work-hours';

/**
 * 일감 보기 — 머리(#번호 · 유형 · 상태 · 제목)와 속성(관리자 · 담당자 · 날짜 다섯 · 상위 일감 · 예상 공수 · 쓴 시간)은 늘 보이고,
 * 본문은 탭 넷으로 나눈다 (2026-11-22 사용자: "내용, 댓글, 첨부파일 이렇게 탭으로 볼 수 있게" ·
 * 2026-11-23 사용자: "리비전을 첨부파일 옆에 탭으로 해서 추가하고 타임라인으로 볼 수 있게 해줘").
 *   내용 — 설명 · 하위 일감 · 연결 일감 · 작업 시간
 *   댓글 — 이력 · 댓글(한 줄기, 내 댓글은 제자리에서 고치고 지운다 — FR-ISS-019) · 댓글 쓰기
 *   첨부파일 — 첨부 목록 · 올리기
 *   리비전 — 리비전 타임라인 · 고른 리비전의 차이 · 그 리비전 전체(IssueRevisions. 예전 issues/:number/history 는 이 탭으로 돌린다)
 * 탭은 주소(?tab=)에 남아 새로 고침 · 링크에도 같은 탭이 열린다. 탭 이름에 댓글 수 · 첨부 수 · 리비전 수를 붙인다.
 * 네 탭은 늘 그려 두고 고른 것만 보인다 — 탭을 오가도 쓰던 댓글 · 올리던 파일 · 보던 리비전이 사라지지 않는다.
 * 바뀐 때(만든 때 · 고친 때 · 이력 · 리비전)는 밀리초까지 보인다.
 * 배치는 가운데(제목 · 탭 · 본문)를 넓게 둔다(2026-11-23 사용자: "숨길 건 숨기고 보일 건 보이고 가운데 이 부분이 넓게 잘 나와야지"):
 *   제목은 한 줄을 다 쓰고, 동작은 고치기 · 상태 바꾸기만 보이고 나머지는 ⋮ 에 둔다.
 *   리비전은 머리(상태 옆)가 아니라 첨부파일 옆 탭이다(2026-11-23 사용자: "위에 상태 옆에 있는데 첨부파일 옆 탭으로 구성해").
 *   속성은 탭(내용 · 댓글 · 첨부파일 · 리비전) 바로 위에 두 줄로 둔다(2026-11-23 사용자: "이 상태 정보 내용 위쪽에 잘 배열해서 보여 줘" ·
 *   "내용, 댓글, 첨부파일 위로" · "상태도 유형도 이미 중복 정보지?") — 오른쪽 칸을 없애 가운데가 화면 폭을 다 쓴다.
 *   상태 · 유형은 머리의 칩에, 고친 때는 만든 때 옆에 있어서 속성에는 넣지 않는다.
 * moai-wiki 의 페이지 보기 자리다. 단추는 서버가 준 이 프로젝트의 내 권한(myPermissions)으로 보인다(표시만 — 판정은 api).
 * 없는 일감 · 지운 일감은 서버가 404 로 답한다.
 */
export function IssueViewPage() {
  const { workspace } = useWorkspace();
  const number = parseIssueNumber(useParams().number);
  // 틀(IssueLayout)이 같은 번호로 이미 읽었다 — 같은 캐시를 쓴다
  const query = useIssueQuery(workspace.slug, number);

  if (number === undefined || (query.isError && isApiError(query.error) && query.error.status === 404)) {
    return <IssueNotFound />;
  }
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  return <IssueView slug={workspace.slug} issue={query.data} onReload={() => void query.refetch()} />;
}

/** 이 너비(이 화면이 받은 너비)보다 좁으면 속성은 주요 다섯만 보이고 나머지는 펼쳐 본다 */
const NARROW_BELOW = 560;
/** 본문(설명 · 댓글)의 최대 폭 */
const BODY_MAX = 1200;

function IssueView({ slug, issue, onReload }: { slug: string; issue: IssueDetail; onReload: () => void }) {
  const { t } = useTranslation(['issue', 'common']);
  const project = useProject();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const labels = useLabels();
  const typeName = useIssueTypeName();
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const [statusMenu, setStatusMenu] = useState<HTMLElement | null>(null);
  const [removing, setRemoving] = useState(false);
  const [changing, setChanging] = useState(false);
  const [params, setParams] = useSearchParams();
  const tab = readIssueViewTab(params);
  const tabsId = useId();
  const tabsRef = useRef<HTMLDivElement>(null);
  const shownTab = useRef(tab);
  const attachments = useAttachmentsQuery(slug, issue.number);

  /** 탭을 바꾼다 — 주소의 다른 값(?settings= · 고른 리비전 등)은 그대로 둔다 */
  const changeTab = (next: IssueViewTab) => setParams((p) => withIssueViewTab(p, next), { replace: true });

  /**
   * 탭이 바뀌었는데 탭 줄이 화면 위로 지나가 있으면(댓글 탭 아래쪽 이력의 "리비전 N" 을 눌러 리비전 탭으로 온 때) 탭 줄로 올려 준다 —
   * 그대로 두면 짧아진 새 탭의 아래쪽 빈 곳이나 중간이 보인다. 탭 줄을 눌러 바꿀 때는 탭 줄이 이미 보이므로 움직이지 않는다.
   */
  useEffect(() => {
    if (shownTab.current === tab) {
      return;
    }
    shownTab.current = tab;
    const tabs = tabsRef.current;
    if (tabs && tabs.getBoundingClientRect().top < 0) {
      tabs.scrollIntoView({ block: 'start' });
    }
  }, [tab]);

  const open = project.status === 'ACTIVE';
  const canEdit = open && projectCan(project, 'issue.edit');
  const canComment = open && projectCan(project, 'issue.comment');
  const canDelete = open && projectCan(project, 'issue.delete');
  const canManage = projectCan(project, 'project.manage');
  const canCreate = project.myActions.includes('createIssue');
  const canUpload = canUploadAttachment(project);
  /** 글 속 #번호 · 상위 · 하위 · 연결 — 정본 주소(번호는 워크스페이스 안에서 하나라 다른 프로젝트의 일감도 같은 모양) */
  const toIssue = useCallback((n: number) => issuePath(slug, n), [slug]);
  /** 이력의 "리비전 N" — 이 화면의 리비전 탭에서 그 리비전의 차이를 연다 */
  const toRevision = useCallback((no: number) => issueRevisionsPath(slug, issue.number, { rev: no }), [slug, issue.number]);
  /** 속성을 줄일지는 창 너비가 아니라 이 화면이 실제로 받은 너비로 정한다(주 메뉴를 접으면 늘어난다 — moai-wiki 와 같은 방식) */
  const [areaRef, areaWidth] = useElementWidth<HTMLDivElement>();
  const narrow = areaWidth !== null && areaWidth < NARROW_BELOW;
  const edited = issue.updatedAt !== issue.createdAt;

  /** 상태만 바로 바꾼다 — 다른 사람이 먼저 고쳤으면 지금 내용을 다시 읽어 보인다 */
  const changeStatus = async (status: IssueStatus) => {
    setStatusMenu(null);
    if (status === issue.status) {
      return;
    }
    setChanging(true);
    try {
      const saved = await issueApi.update(slug, issue.number, { rowVersion: issue.rowVersion, status });
      storeIssue(queryClient, slug, saved);
      toast.success(t('view.statusChanged'));
    } catch (e) {
      if (isApiError(e, 'CONCURRENT_UPDATE')) {
        toast.warning(t('view.conflict'));
        onReload();
      } else {
        toast.error(errorMessage(e));
      }
    } finally {
      setChanging(false);
    }
  };

  const creator = personName(issue.createdBy) ?? t('view.unknownPerson');

  const tabProps = (value: IssueViewTab) => ({ value, id: `${tabsId}-${value}`, 'aria-controls': `${tabsId}-${value}-panel` });
  const panelProps = (value: IssueViewTab) => ({
    role: 'tabpanel',
    id: `${tabsId}-${value}-panel`,
    'aria-labelledby': `${tabsId}-${value}`,
    hidden: tab !== value,
  });

  return (
    <IssueAttachmentsProvider slug={slug} number={issue.number}>
      <Box component="article" ref={areaRef} sx={{ containerType: 'inline-size' }}>
        {issue.parent && (
          <Link
            component={RouterLink}
            to={toIssue(issue.parent.number)}
            underline="hover"
            color="text.secondary"
            variant="body2"
            sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, mb: 1, overflowWrap: 'anywhere' }}
          >
            <SubdirectoryArrowRightRounded fontSize="small" sx={{ transform: 'scaleY(-1)' }} />
            {t('fields.parent')} #{issue.parent.number} {issue.parent.title}
          </Link>
        )}

        {/* 머리 — [#번호 · 유형 · 상태 … 동작] 한 줄 아래에 제목이 폭을 다 쓴다. 좁으면 번호 줄 → 제목 → 동작 순서로 쌓는다.
            리비전은 여기(상태 옆)가 아니라 첨부파일 옆 탭에 있다 */}
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1fr)',
            gridTemplateAreas: '"meta" "title" "created" "actions"',
            columnGap: 2,
            rowGap: 1,
            mb: 2.5,
            '@container (min-width: 640px)': {
              gridTemplateColumns: 'minmax(0, 1fr) auto',
              gridTemplateAreas: '"meta actions" "title title" "created created"',
            },
          }}
        >
          <Stack direction="row" spacing={1} useFlexGap sx={{ gridArea: 'meta', alignItems: 'center', flexWrap: 'wrap', minWidth: 0 }}>
            <Typography variant="subtitle2" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
              #{issue.number}
            </Typography>
            <Chip size="small" variant="outlined" label={typeName(issue.type)} />
            <IssueStatusChip status={issue.status} />
          </Stack>
          <Stack direction="row" spacing={1} useFlexGap sx={{ gridArea: 'actions', alignItems: 'center', flexWrap: 'wrap' }}>
            {canEdit && (
              <Button component={RouterLink} to={issueEditPath(slug, issue.number)} variant="contained" startIcon={<EditRounded />}>
                {t('view.edit')}
              </Button>
            )}
            {canEdit && (
              <Button
                variant="outlined"
                endIcon={<ExpandMoreRounded />}
                onClick={(event) => setStatusMenu(event.currentTarget)}
                loading={changing}
                aria-haspopup="menu"
              >
                {t('view.changeStatus')}
              </Button>
            )}
            {(canCreate || canDelete) && (
              <Tooltip title={t('view.more')}>
                <IconButton aria-label={t('view.more')} aria-haspopup="menu" onClick={(event) => setMenu(event.currentTarget)}>
                  <MoreVertRounded />
                </IconButton>
              </Tooltip>
            )}
            <Menu anchorEl={menu} open={!!menu} onClose={() => setMenu(null)}>
              {[
                canCreate && (
                  <MenuItem key="child" component={RouterLink} to={newIssuePath(slug, project.key, issue.number)} onClick={() => setMenu(null)}>
                    <ListItemIcon>
                      <AddRounded fontSize="small" />
                    </ListItemIcon>
                    {t('view.addChildIssue')}
                  </MenuItem>
                ),
                canCreate && canDelete && <Divider key="divider" />,
                canDelete && (
                  <MenuItem
                    key="delete"
                    onClick={() => {
                      setMenu(null);
                      setRemoving(true);
                    }}
                    sx={{ color: 'error.main' }}
                  >
                    <ListItemIcon sx={{ color: 'inherit' }}>
                      <DeleteOutlineRounded fontSize="small" />
                    </ListItemIcon>
                    {t('view.delete')}
                  </MenuItem>
                ),
              ].filter(Boolean)}
            </Menu>
            <Menu anchorEl={statusMenu} open={!!statusMenu} onClose={() => setStatusMenu(null)}>
              {issueStatuses.map((status) => (
                <MenuItem key={status} selected={status === issue.status} onClick={() => void changeStatus(status)}>
                  {labels.issueStatus(status)}
                </MenuItem>
              ))}
            </Menu>
          </Stack>
          <Typography variant="h4" component="h1" sx={{ gridArea: 'title', overflowWrap: 'anywhere', textWrap: 'pretty' }}>
            {issue.title}
          </Typography>
          <Typography variant="caption" sx={{ gridArea: 'created', color: 'text.secondary', display: 'block', fontVariantNumeric: 'tabular-nums' }}>
            {t('view.createdMeta', { name: creator, time: dateTimeMs(issue.createdAt), when: fromNow(issue.createdAt) })}
            {edited && (
              <>
                {' · '}
                <time dateTime={issue.updatedAt}>{t('view.updatedMeta', { time: dateTimeMs(issue.updatedAt), when: fromNow(issue.updatedAt) })}</time>
              </>
            )}
          </Typography>
        </Box>

        {!open && (
          <Alert severity="warning" sx={{ mb: 2.5 }}>
            {t('view.archivedBanner')}
          </Alert>
        )}

        <Box sx={{ minWidth: 0 }}>
          <IssueProperties slug={slug} issue={issue} toIssue={toIssue} narrow={narrow} />
          <Tabs
            ref={tabsRef}
            value={tab}
            onChange={(_, next: IssueViewTab) => changeTab(next)}
            aria-label={t('view.tabsLabel')}
            variant="scrollable"
            scrollButtons={false}
            sx={{
              mb: 2.5,
              borderBottom: 1,
              borderColor: 'divider',
              // 좁은 화면은 위 막대(56px)가 붙어 있어 그 아래로 올린다
              scrollMarginTop: { xs: `${layout.topbarHeight + 8}px`, md: '16px' },
              '& .MuiTab-root': { minWidth: 0, px: { xs: 1.5, sm: 2 } },
            }}
          >
            <Tab {...tabProps('content')} label={t('view.tabContent')} />
            <Tab {...tabProps('comments')} label={<TabLabel text={t('view.tabComments')} count={countComments(issue.journal)} />} />
            <Tab {...tabProps('attachments')} label={<TabLabel text={t('view.tabAttachments')} count={attachments.data?.length} />} />
            {/* 리비전은 1부터 빠짐없이 쌓이므로 지금 리비전 번호가 곧 리비전 수다 */}
            <Tab {...tabProps('revisions')} label={<TabLabel text={t('view.tabRevisions')} count={issue.revisionNo} />} />
          </Tabs>

          <Box {...panelProps('content')}>
            {/* 본문은 가운데 폭을 넓게 쓰되 1200px 까지(사용자: "가운데 이 부분이 넓게" · 한 줄이 너무 길면 읽기 어렵다 — moai-wiki 와 같다).
                넓은 표 · 긴 코드는 그 상자 안에서만 가로로 밀린다 */}
            <Box component="section" aria-label={t('fields.description')} sx={{ minWidth: 0, maxWidth: BODY_MAX }}>
              {issue.description.trim() ? (
                <MarkdownView content={issue.description} issuePath={toIssue} />
              ) : (
                <Typography variant="body2" sx={{ color: 'text.secondary', py: 1 }}>
                  {t('view.emptyDescription')}
                </Typography>
              )}
            </Box>

            <Box component="section" sx={{ mt: 3.5 }}>
              <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2, mb: issue.children.length === 0 ? 0 : 1 }}>
                <Stack direction="row" useFlexGap sx={{ alignItems: 'baseline', flexWrap: 'wrap', columnGap: 1.5, minWidth: 0 }}>
                  <Typography variant="h6" component="h2">
                    {t('fields.children')}
                  </Typography>
                  {issue.children.length === 0 && (
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                      {t('view.childrenEmpty')}
                    </Typography>
                  )}
                </Stack>
                {canCreate && (
                  <Button size="small" component={RouterLink} to={newIssuePath(slug, project.key, issue.number)} startIcon={<AddRounded />}>
                    {t('view.addChild')}
                  </Button>
                )}
              </Stack>
              {issue.children.length > 0 && (
                <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden' }}>
                  <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                    {issue.children.map((child, index) => (
                      <IssueRefRow key={child.number} slug={slug} currentProjectKey={issue.project.key} issue={child} divider={index < issue.children.length - 1} />
                    ))}
                  </Box>
                </Paper>
              )}
            </Box>

            <IssueLinks slug={slug} issue={issue} canEdit={canEdit} />

            <IssueWorkLogs slug={slug} issue={issue} canLog={canEdit} canManage={canManage} />
          </Box>

          <Box {...panelProps('comments')}>
            <Box component="section" sx={{ maxWidth: BODY_MAX }}>
              <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
                {t('view.history')}
              </Typography>
              <IssueHistory
                issue={issue}
                issuePath={toIssue}
                revisionPath={toRevision}
                comments={{ slug, open, canComment, canManage, canAttach: canUpload }}
              />
              {canComment && <CommentBox slug={slug} issue={issue} canAttach={canUpload} />}
            </Box>
          </Box>

          <Box {...panelProps('attachments')}>
            <IssueAttachments slug={slug} number={issue.number} canUpload={canUpload} canManage={canManage} />
          </Box>

          <Box {...panelProps('revisions')}>
            <IssueRevisions slug={slug} issue={issue} active={tab === 'revisions'} />
          </Box>
        </Box>

        {removing && (
          <RemoveIssueDialog
            slug={slug}
            issue={issue}
            onClose={() => setRemoving(false)}
            onRemoved={() =>
              void navigate(issue.parent ? toIssue(issue.parent.number) : projectPath(slug, project.key), { replace: true })
            }
          />
        )}
      </Box>
    </IssueAttachmentsProvider>
  );
}

/** 탭 이름 + 개수 (멤버 화면의 탭과 같은 모양) */
function TabLabel({ text, count }: { text: string; count?: number }) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <span>{text}</span>
      {count !== undefined && <Chip size="small" label={count} sx={{ height: 20, fontSize: 12, fontVariantNumeric: 'tabular-nums' }} />}
    </Stack>
  );
}

/**
 * 일감 속성 — 탭 바로 위, 본문 폭(1200px 까지)에 두 줄로 놓는다. 이름을 값 위에 둔다.
 *   첫 줄: 사람 · 공수 — 관리자 · 담당자 · 상위 일감 · 예상 공수 · 쓴 시간
 *   둘째 줄: 날짜 다섯 — 완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일
 *   셋째 줄: 꼬리표 — 관련 모듈 · 에이전트 세션(IssueTagFields, 좁은 화면에서도 접지 않는다)
 * 한 줄의 칸 수는 받은 너비로 정한다(880px 이상 다섯 · 560px 이상 셋 · 그 아래 둘 — 이 화면 article 의 container query).
 * 좁은 화면(narrow)은 주요 다섯(관리자 · 담당자 · 완료 기한 · 완료 예정일 · 쓴 시간)만 보이고 나머지는 펼쳐 본다.
 * 상태 · 유형은 머리의 칩에, 고친 때는 만든 때 옆에 있어 여기에는 없다.
 */
function IssueProperties({ slug, issue, toIssue, narrow }: { slug: string; issue: IssueDetail; toIssue: (n: number) => string; narrow: boolean }) {
  const { t } = useTranslation('issue');
  const [expanded, setExpanded] = useState(false);
  const progress = spentProgress(issue.spentHours, issue.estimatedHours);
  const moreId = useId();

  /**
   * 프로젝트 — 일감의 속성이다(Redmine 의 Project 칸 — 2026-11-24 사용자: "이슈는 프로젝트는 속성처럼 가지고 있어야해"). 누르면 그 프로젝트의 일감 목록.
   * 모듈은 프로젝트마다라 꼬리표 줄(관련 모듈 · 에이전트 세션) 맨 앞에 둔다 — 좁은 화면에서도 접지 않는다
   */
  const tags = (
    <IssueTagFields
      agentSession={issue.agentSession}
      modules={issue.modules}
      project={{
        label: t('fields.project'),
        value: (
          <Link
            component={RouterLink}
            to={projectPath(slug, issue.project.key)}
            underline="hover"
            title={t('view.projectLink', { name: issue.project.name })}
            sx={{ overflowWrap: 'anywhere' }}
          >
            {issue.project.name}
          </Link>
        ),
      }}
    />
  );
  const manager = <Field label={t('fields.manager')}>{personName(issue.manager) ?? <Muted>{t('form.nobody')}</Muted>}</Field>;
  const assignee = <Field label={t('fields.assignee')}>{personName(issue.assignee) ?? <Muted>{t('form.nobody')}</Muted>}</Field>;
  const parent = (
    <Field label={t('fields.parent')}>
      {issue.parent ? (
        <Link component={RouterLink} to={toIssue(issue.parent.number)} underline="hover" sx={{ overflowWrap: 'anywhere' }}>
          #{issue.parent.number} {issue.parent.title}
        </Link>
      ) : (
        <Muted>{t('form.nobody')}</Muted>
      )}
    </Field>
  );
  const due = (
    <Field label={t('fields.dueDate')}>
      <DateValue value={issue.dueDate} />
      {issue.overdue && (
        <Typography component="span" variant="caption" sx={{ ml: 1, color: 'warning.main', fontWeight: 600 }}>
          {t('list.overdue')}
        </Typography>
      )}
    </Field>
  );
  const start = (
    <Field label={t('fields.startDate')}>
      <DateValue value={issue.startDate} />
    </Field>
  );
  const planned = (
    <Field label={t('fields.plannedEndDate')}>
      <DateValue value={issue.plannedEndDate} />
    </Field>
  );
  const completed = (
    <Field label={t('fields.completedOn')}>
      <DateValue value={issue.completedOn} />
    </Field>
  );
  const closed = (
    <Field label={t('fields.closedOn')}>
      <DateValue value={issue.closedOn} />
    </Field>
  );
  const estimate = (
    <Field label={t('fields.estimatedHours')}>
      {issue.estimatedHours !== null ? t('hours.value', { hours: formatHours(issue.estimatedHours) }) : <Muted>—</Muted>}
    </Field>
  );
  const spent = (
    <Field label={t('fields.spentHours')}>
      {t('hours.value', { hours: formatHours(issue.spentHours) })}
      {progress && issue.estimatedHours !== null && (
        <>
          <Typography
            component="span"
            variant="caption"
            sx={{ display: 'block', color: progress.over > 0 ? 'warning.main' : 'text.secondary', fontWeight: progress.over > 0 ? 600 : 400 }}
          >
            {t('view.spentOfEstimate', { percent: progress.percent, spent: formatHours(issue.spentHours), estimate: formatHours(issue.estimatedHours) })}
          </Typography>
          {progress.over > 0 && (
            <Typography component="span" variant="caption" sx={{ display: 'block', color: 'warning.main', fontWeight: 600 }}>
              {t('view.overEstimate', { hours: formatHours(progress.over) })}
            </Typography>
          )}
          {/* 막대는 보탬일 뿐 — 뜻은 위의 글이 전한다 */}
          <LinearProgress
            aria-hidden
            variant="determinate"
            value={Math.min(100, progress.percent)}
            color={progress.over > 0 ? 'warning' : 'primary'}
            sx={{ mt: 0.5, height: 4, borderRadius: 2 }}
          />
        </>
      )}
    </Field>
  );

  const row = {
    m: 0,
    display: 'grid',
    columnGap: 3,
    rowGap: 1.5,
    gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
    '@container (min-width: 560px)': { gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' },
    '@container (min-width: 880px)': { gridTemplateColumns: 'repeat(5, minmax(0, 1fr))' },
  } as const;
  const panel = { borderRadius: 2.5, px: { xs: 2, sm: 2.5 }, py: 1.75, mb: 2.5, maxWidth: BODY_MAX } as const;

  if (narrow) {
    return (
      <Paper variant="outlined" component="aside" aria-label={t('form.fieldsTitle')} sx={{ ...panel, pb: 0.5 }}>
        <Box component="dl" sx={row}>
          {manager}
          {assignee}
          {due}
          {planned}
          {spent}
        </Box>
        <Divider sx={{ my: 1.5 }} />
        {tags}
        <Collapse in={expanded} id={moreId}>
          <Box component="dl" sx={{ ...row, mt: 1.5 }}>
            {parent}
            {estimate}
            {start}
            {completed}
            {closed}
          </Box>
        </Collapse>
        <Button
          size="small"
          color="inherit"
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
          aria-controls={moreId}
          endIcon={expanded ? <ExpandLessRounded /> : <ExpandMoreRounded />}
          sx={{ mt: 0.5, color: 'text.secondary' }}
        >
          {expanded ? t('view.fewerFields') : t('view.allFields')}
        </Button>
      </Paper>
    );
  }

  return (
    <Paper variant="outlined" component="aside" aria-label={t('form.fieldsTitle')} sx={panel}>
      <Box component="dl" sx={row}>
        {manager}
        {assignee}
        {parent}
        {estimate}
        {spent}
      </Box>
      <Divider sx={{ my: 1.5 }} />
      <Box component="dl" sx={row}>
        {due}
        {start}
        {planned}
        {completed}
        {closed}
      </Box>
      <Divider sx={{ my: 1.5 }} />
      {tags}
    </Paper>
  );
}

/** 속성 한 칸 — 이름(dt)을 값(dd) 위에 둔다 */
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography component="dt" variant="caption" sx={{ color: 'text.secondary', whiteSpace: 'nowrap', display: 'block' }}>
        {label}
      </Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, minWidth: 0, overflowWrap: 'anywhere', fontVariantNumeric: 'tabular-nums' }}>
        {children}
      </Typography>
    </Box>
  );
}

function Muted({ children }: { children: ReactNode }) {
  return (
    <Box component="span" sx={{ color: 'text.secondary' }}>
      {children}
    </Box>
  );
}

function DateValue({ value }: { value: string | null }) {
  return value ? <>{date(value)}</> : <Muted>—</Muted>;
}
