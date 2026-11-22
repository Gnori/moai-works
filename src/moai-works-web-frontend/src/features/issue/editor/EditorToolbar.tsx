import ChecklistRounded from '@mui/icons-material/ChecklistRounded';
import CodeRounded from '@mui/icons-material/CodeRounded';
import FormatBoldRounded from '@mui/icons-material/FormatBoldRounded';
import FormatItalicRounded from '@mui/icons-material/FormatItalicRounded';
import FormatListBulletedRounded from '@mui/icons-material/FormatListBulletedRounded';
import FormatListNumberedRounded from '@mui/icons-material/FormatListNumberedRounded';
import FormatQuoteRounded from '@mui/icons-material/FormatQuoteRounded';
import FormatStrikethroughRounded from '@mui/icons-material/FormatStrikethroughRounded';
import HorizontalRuleRounded from '@mui/icons-material/HorizontalRuleRounded';
import IntegrationInstructionsRounded from '@mui/icons-material/IntegrationInstructionsRounded';
import LinkRounded from '@mui/icons-material/LinkRounded';
import RedoRounded from '@mui/icons-material/RedoRounded';
import SourceRounded from '@mui/icons-material/SourceRounded';
import TableChartRounded from '@mui/icons-material/TableChartRounded';
import TitleRounded from '@mui/icons-material/TitleRounded';
import TocRounded from '@mui/icons-material/TocRounded';
import UndoRounded from '@mui/icons-material/UndoRounded';
import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import { Fragment, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { EditorAction, EditorActive } from './editor-types';

/** 도구 막대 한 칸 — 명령 이름 · 말 열쇠 · 아이콘 · 단축키(도구 설명에 함께 보인다) */
interface ToolItem {
  action: EditorAction;
  label: 'bold' | 'italic' | 'strike' | 'link' | 'bulletList' | 'orderedList' | 'taskList' | 'table' | 'quote' | 'codeBlock' | 'inlineCode' | 'hr' | 'undo' | 'redo';
  icon: ReactNode;
  shortcut?: string;
}

/**
 * 도구 막대의 묶음 — VS Code Markdown Editor 화면의 차례를 따른다(2026-11-23 사용자: "이렇게 md 편집기를 만들고").
 * [목차] | [제목 ▾] 굵게 · 기울임 · 취소선 · 링크 | 글머리 · 번호 · 할 일 · 표 | 인용 · 코드 블록 · 줄 안 코드 · 가로줄 | 되돌리기 · 다시 | … [원문]
 */
export const toolGroups: readonly (readonly ToolItem[])[] = [
  [
    { action: 'bold', label: 'bold', icon: <FormatBoldRounded fontSize="small" />, shortcut: 'Mod+B' },
    { action: 'italic', label: 'italic', icon: <FormatItalicRounded fontSize="small" />, shortcut: 'Mod+I' },
    { action: 'strike', label: 'strike', icon: <FormatStrikethroughRounded fontSize="small" />, shortcut: 'Mod+Alt+X' },
    { action: 'link', label: 'link', icon: <LinkRounded fontSize="small" /> },
  ],
  [
    { action: 'bulletList', label: 'bulletList', icon: <FormatListBulletedRounded fontSize="small" /> },
    { action: 'orderedList', label: 'orderedList', icon: <FormatListNumberedRounded fontSize="small" /> },
    { action: 'taskList', label: 'taskList', icon: <ChecklistRounded fontSize="small" /> },
    { action: 'table', label: 'table', icon: <TableChartRounded fontSize="small" /> },
  ],
  [
    { action: 'quote', label: 'quote', icon: <FormatQuoteRounded fontSize="small" /> },
    { action: 'codeBlock', label: 'codeBlock', icon: <IntegrationInstructionsRounded fontSize="small" /> },
    { action: 'inlineCode', label: 'inlineCode', icon: <CodeRounded fontSize="small" />, shortcut: 'Mod+E' },
    { action: 'hr', label: 'hr', icon: <HorizontalRuleRounded fontSize="small" /> },
  ],
  [
    { action: 'undo', label: 'undo', icon: <UndoRounded fontSize="small" />, shortcut: 'Mod+Z' },
    { action: 'redo', label: 'redo', icon: <RedoRounded fontSize="small" />, shortcut: 'Mod+Shift+Z' },
  ],
];

/** 단축키를 이 기기의 말로 — Mac 이면 ⌘, 아니면 Ctrl */
function shortcutText(shortcut: string): string {
  const mac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);
  return shortcut
    .replace('Mod', mac ? '⌘' : 'Ctrl')
    .replace('Alt', mac ? '⌥' : 'Alt')
    .replace('Shift', mac ? '⇧' : 'Shift')
    .replace(/\+/g, mac ? '' : '+');
}

interface Props {
  /** 서식 명령을 돌린다(원문 보기에서는 서식 단추를 꺼 둔다) */
  onAction: (action: EditorAction) => void;
  /** 링크 단추 — 주소를 묻는 칸을 연다 */
  onLink: () => void;
  source: boolean;
  onToggleSource: () => void;
  /** 커서 자리의 서식 — 굵게 · 기울임 · 취소선 · 줄 안 코드 · 제목 단추의 눌림 표시 */
  active: EditorActive;
  /** 목차 단추 — 주지 않으면 그리지 않는다 */
  outlineOpen?: boolean;
  onToggleOutline?: () => void;
  /** 오른쪽 끝에 둘 것(이미지 · 파일 넣기) */
  extra?: ReactNode;
}

/**
 * 편집기 위 고정 도구 막대 — 아이콘 단추와 도구 설명(단축키 함께). 좁은 화면에서는 막대 안에서만 가로로 밀린다(페이지는 밀리지 않는다).
 * "Markdown 원문" 단추로 원문 편집(CodeMirror)과 쓰면서 바로 그려지는 편집을 오간다. 원문 보기에서는 서식 단추를 꺼 둔다.
 */
export function EditorToolbar({ onAction, onLink, source, onToggleSource, active, outlineOpen, onToggleOutline, extra }: Props) {
  const { t } = useTranslation('issue');
  const [headingMenu, setHeadingMenu] = useState<HTMLElement | null>(null);

  const tool = (
    key: string,
    label: string,
    icon: ReactNode,
    onClick: (anchor: HTMLElement) => void,
    options: { pressed?: boolean; disabled?: boolean; menu?: boolean } = {},
  ) => (
    <Tooltip key={key} title={label}>
      <span>
        <IconButton
          size="small"
          aria-label={label}
          aria-pressed={options.pressed}
          aria-haspopup={options.menu ? 'menu' : undefined}
          disabled={options.disabled}
          onClick={(event) => onClick(event.currentTarget)}
          // 누를 때 편집 칸의 커서 · 고른 글을 잃지 않게
          onMouseDown={(event) => event.preventDefault()}
          sx={{ borderRadius: 1.5, color: options.pressed ? 'primary.main' : 'text.secondary', bgcolor: options.pressed ? 'action.selected' : undefined }}
        >
          {icon}
        </IconButton>
      </span>
    </Tooltip>
  );

  return (
    <Box
      role="toolbar"
      aria-label={t('editor.toolbar')}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.25,
        px: 0.75,
        py: 0.5,
        minHeight: 44,
        borderBottom: 1,
        borderColor: 'divider',
        bgcolor: 'background.subtle',
        overflowX: 'auto',
        overflowY: 'hidden',
        scrollbarWidth: 'thin',
        '& > *': { flexShrink: 0 },
      }}
    >
      {onToggleOutline && (
        <>
          {tool('outline', t('editor.outline'), <TocRounded fontSize="small" />, onToggleOutline, { pressed: !!outlineOpen, disabled: source })}
          <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.75 }} />
        </>
      )}
      {tool('heading', t('editor.heading'), <TitleRounded fontSize="small" />, (anchor) => setHeadingMenu(anchor), { disabled: source, menu: true, pressed: active.heading > 0 })}
      {toolGroups.map((group, index) => (
        <Fragment key={index}>
          {index > 0 && <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.75 }} />}
          {group.map((item) => {
            const label = item.shortcut ? `${t(`editor.${item.label}`)} (${shortcutText(item.shortcut)})` : t(`editor.${item.label}`);
            // 원문 보기에서는 서식 단추를 모두 끈다 — 원문 칸은 자기 되돌리기(Ctrl+Z)가 있다
            const pressed =
              item.action === 'bold' || item.action === 'italic' || item.action === 'strike' || item.action === 'inlineCode' ? active[item.action] : undefined;
            return tool(item.action, label, item.icon, () => (item.action === 'link' ? onLink() : onAction(item.action)), { disabled: source, pressed });
          })}
        </Fragment>
      ))}
      <Box sx={{ flex: '1 0 8px' }} />
      {extra}
      {tool('source', t('editor.source'), <SourceRounded fontSize="small" />, onToggleSource, { pressed: source })}
      <Menu anchorEl={headingMenu} open={!!headingMenu} onClose={() => setHeadingMenu(null)}>
        {(['paragraph', 'heading1', 'heading2', 'heading3'] as const).map((action) => (
          <MenuItem
            key={action}
            selected={action === 'paragraph' ? active.heading === 0 : active.heading === Number(action.slice(-1))}
            onClick={() => {
              setHeadingMenu(null);
              onAction(action);
            }}
          >
            {t(`editor.${action}`)}
          </MenuItem>
        ))}
      </Menu>
    </Box>
  );
}
