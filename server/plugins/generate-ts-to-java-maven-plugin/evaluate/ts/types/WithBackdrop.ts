import type { Backdrop } from './Backdrop';

/** Test interface with inline backdrop object having n,e,s,w arrays */
export interface WithBackdrop {
  cx: number;
  cz: number;
  size: number;

  /** Inline object should become separate Java class WithBackdropBackdrop */
  backdrop?: {
    n?: Array<Backdrop>;
    e?: Array<Backdrop>;
    s?: Array<Backdrop>;
    w?: Array<Backdrop>;
  };
}
