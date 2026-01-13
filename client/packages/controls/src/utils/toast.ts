/**
 * Toast notification utility
 * Shows temporary notifications without blocking the UI
 */

export type ToastType = 'info' | 'success' | 'warning' | 'error';

interface ToastOptions {
  type?: ToastType;
  duration?: number;
  onClose?: () => void;
}

/**
 * Show a toast notification
 */
export function showToast(message: string, options: ToastOptions = {}) {
  const {
    type = 'info',
    duration = 3000,
    onClose
  } = options;

  // Create toast container if it doesn't exist
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 10px;
      pointer-events: none;
    `;
    document.body.appendChild(container);
  }

  // Create toast element
  const toast = document.createElement('div');
  toast.style.cssText = `
    pointer-events: auto;
    padding: 16px 20px;
    border-radius: 8px;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    color: white;
    font-size: 14px;
    max-width: 400px;
    animation: slideIn 0.3s ease-out;
    display: flex;
    align-items: center;
    gap: 12px;
  `;

  // Set background color based on type
  const colors = {
    info: '#3b82f6',
    success: '#10b981',
    warning: '#f59e0b',
    error: '#ef4444'
  };
  toast.style.backgroundColor = colors[type];

  // Add icon
  const icons = {
    info: `<svg style="width: 20px; height: 20px; flex-shrink: 0;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>`,
    success: `<svg style="width: 20px; height: 20px; flex-shrink: 0;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>`,
    warning: `<svg style="width: 20px; height: 20px; flex-shrink: 0;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
    </svg>`,
    error: `<svg style="width: 20px; height: 20px; flex-shrink: 0;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>`
  };

  toast.innerHTML = `
    ${icons[type]}
    <span style="flex: 1;">${message}</span>
  `;

  // Add animation styles if not already added
  if (!document.getElementById('toast-animations')) {
    const style = document.createElement('style');
    style.id = 'toast-animations';
    style.textContent = `
      @keyframes slideIn {
        from {
          transform: translateX(400px);
          opacity: 0;
        }
        to {
          transform: translateX(0);
          opacity: 1;
        }
      }
      @keyframes slideOut {
        from {
          transform: translateX(0);
          opacity: 1;
        }
        to {
          transform: translateX(400px);
          opacity: 0;
        }
      }
    `;
    document.head.appendChild(style);
  }

  container.appendChild(toast);

  // Auto remove after duration
  setTimeout(() => {
    toast.style.animation = 'slideOut 0.3s ease-in';
    setTimeout(() => {
      container?.removeChild(toast);
      if (onClose) {
        onClose();
      }
    }, 300);
  }, duration);
}

/**
 * Show an error toast
 */
export function showErrorToast(message: string, duration?: number) {
  showToast(message, { type: 'error', duration });
}

/**
 * Show a success toast
 */
export function showSuccessToast(message: string, duration?: number) {
  showToast(message, { type: 'success', duration });
}

/**
 * Show an info toast
 */
export function showInfoToast(message: string, duration?: number) {
  showToast(message, { type: 'info', duration });
}

/**
 * Show a warning toast
 */
export function showWarningToast(message: string, duration?: number) {
  showToast(message, { type: 'warning', duration });
}
