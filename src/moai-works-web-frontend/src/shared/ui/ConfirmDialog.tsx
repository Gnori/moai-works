import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

interface ConfirmOptions {
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  /** 되돌릴 수 없는 동작이면 빨간 단추 */
  destructive?: boolean;
}

type Confirm = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<Confirm | null>(null);

/** 확인 대화상자 — `const confirm = useConfirm(); if (await confirm({...})) …` */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const resolver = useRef<((value: boolean) => void) | null>(null);

  const confirm = useCallback<Confirm>((next) => {
    setOptions(next);
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const close = (value: boolean) => {
    resolver.current?.(value);
    resolver.current = null;
    setOptions(null);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Dialog open={options !== null} onClose={() => close(false)} maxWidth="xs" fullWidth>
        {options && (
          <>
            <DialogTitle sx={{ fontWeight: 700 }}>{options.title}</DialogTitle>
            {options.description && (
              <DialogContent>
                <DialogContentText component="div">{options.description}</DialogContentText>
              </DialogContent>
            )}
            <DialogActions sx={{ px: 3, pb: 2.5 }}>
              <Button onClick={() => close(false)} color="inherit">
                {t('actions.cancel')}
              </Button>
              <Button
                onClick={() => close(true)}
                variant="contained"
                color={options.destructive ? 'error' : 'primary'}
                autoFocus
              >
                {options.confirmLabel ?? t('actions.confirm')}
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): Confirm {
  const confirm = useContext(ConfirmContext);
  if (!confirm) {
    throw new Error('useConfirm 은 ConfirmProvider 안에서만 쓸 수 있습니다');
  }
  return confirm;
}
