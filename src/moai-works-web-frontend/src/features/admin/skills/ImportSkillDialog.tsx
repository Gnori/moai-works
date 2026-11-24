import UploadFileRounded from '@mui/icons-material/UploadFileRounded';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';

import { skillPath } from '@/app/routes';
import { skillApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';

import { storeSkill } from './queries';
import { SKILL_BODY_MAX, SKILL_NOTE_MAX, SKILL_NOTE_MIN, skillErrorField, skillNoteProblem } from './skill-rules';

/** 가져올 파일의 크기 상한 — 서버의 SKILL.md 상한(본문 10만 자 + frontmatter)에 한글 3바이트를 넉넉히 잡은 것 */
const FILE_BYTES_MAX = (SKILL_BODY_MAX + 8_192) * 3;

/** 같은 이름의 스킬이 이미 있다 — 그 스킬(그 리비전)을 이 내용으로 수정할지 묻는다 */
interface Existing {
  name: string;
  revisionNo: number;
}

/**
 * SKILL.md 가져오기 — 파일을 고르거나 글을 붙여 넣는다. frontmatter 의 name 으로 새 스킬을 만들거나, 같은 이름이 있으면 한 번 묻고
 * 그 스킬을 수정한다(새 리비전 — "가져옴"). 내보낸 사본의 출처 표시 줄은 서버가 버린다. 지운 스킬이면 먼저 되살리라고 알린다.
 * frontmatter 를 읽고 규칙을 보는 것은 서버 한 곳이다(SkillFile) — 화면은 서버의 오류 문구를 그대로 보인다.
 * 바뀐 점 요약(Agent 에게 보인다)은 새로 만들 때도 꼭 넣는다(일감 #183 — 서버도 400 CHANGE_NOTE_REQUIRED).
 */
export function ImportSkillDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [content, setContent] = useState('');
  const [fileName, setFileName] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);
  const [existing, setExisting] = useState<Existing | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const pick = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }
    if (file.size > FILE_BYTES_MAX) {
      setProblem(t('skills.import.tooLarge'));
      return;
    }
    setContent(await file.text());
    setFileName(file.name);
    setExisting(null);
    setProblem(null);
  };

  const submit = async () => {
    const noteIssue = skillNoteProblem(note);
    if (noteIssue) {
      setNoteError(t(`skills.form.note_${noteIssue}`, { min: SKILL_NOTE_MIN, max: SKILL_NOTE_MAX }));
      return;
    }
    setBusy(true);
    setProblem(null);
    try {
      const result = await skillApi.importFile(content, existing?.revisionNo, note.trim());
      storeSkill(queryClient, result.skill);
      toast.success(
        result.created
          ? t('skills.import.created', { name: result.skill.name })
          : t('skills.import.updated', { name: result.skill.name, no: result.skill.revisionNo }),
      );
      onClose();
      void navigate(skillPath(result.skill.name));
    } catch (e) {
      setBusy(false);
      if (isApiError(e, 'SKILL_EXISTS')) {
        const d = e.details;
        setExisting({ name: String(d.name), revisionNo: Number(d.revisionNo) });
        return;
      }
      if (isApiError(e, 'SKILL_CHANGED')) {
        // 묻는 사이 누가 고쳤다 — 다시 물어 본다(새 리비전으로)
        setExisting(null);
      }
      if (isApiError(e) && skillErrorField(e.code) === 'changeNote') {
        setNoteError(e.message);
        return;
      }
      setProblem(errorMessage(e));
    }
  };

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="md" fullWidth fullScreen={fullScreen}>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('skills.import.title')}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{t('skills.import.body')}</DialogContentText>
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { xs: 'stretch', sm: 'center' } }}>
            <Button variant="outlined" startIcon={<UploadFileRounded />} onClick={() => fileInput.current?.click()} disabled={busy}>
              {t('skills.import.pickFile')}
            </Button>
            <Typography variant="body2" sx={{ color: 'text.secondary', overflowWrap: 'anywhere' }}>
              {fileName ? t('skills.import.picked', { name: fileName }) : t('skills.import.orPaste')}
            </Typography>
            <input
              ref={fileInput}
              type="file"
              accept=".md,text/markdown,text/plain"
              hidden
              onChange={(event) => void pick(event)}
              aria-label={t('skills.import.pickFile')}
            />
          </Stack>
          <TextField
            label={t('skills.import.contentLabel')}
            value={content}
            onChange={(event) => {
              setContent(event.target.value);
              setFileName(null);
              setExisting(null);
            }}
            multiline
            minRows={8}
            maxRows={18}
            placeholder={'---\nname: my-skill\ndescription: …\n---\n\n# …'}
            slotProps={{ htmlInput: { spellCheck: false, sx: { fontFamily: "'JetBrains Mono', monospace", fontSize: 13 } } }}
          />
          <TextField
            label={t('skills.form.noteLabel')}
            helperText={noteError ?? t('skills.form.noteHelp', { min: SKILL_NOTE_MIN, max: SKILL_NOTE_MAX })}
            error={!!noteError}
            placeholder={t('skills.form.notePlaceholder')}
            required
            value={note}
            onChange={(event) => {
              setNote(event.target.value);
              setNoteError(null);
            }}
            slotProps={{ htmlInput: { maxLength: SKILL_NOTE_MAX } }}
          />
          {existing && (
            <Alert severity="warning">{t('skills.import.exists', { name: existing.name, no: existing.revisionNo })}</Alert>
          )}
          {problem && (
            <Alert severity="error" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
              {problem}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit" disabled={busy}>
          {t('common:actions.cancel')}
        </Button>
        <Button variant="contained" onClick={() => void submit()} loading={busy} disabled={!content.trim()}>
          {existing ? t('skills.import.updateExisting') : t('skills.import.submit')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
