import { marked } from 'marked';
import DOMPurify from 'dompurify';

/**
 * Renders untrusted Markdown to sanitized HTML safe for use with `v-html`.
 *
 * Markdown (e.g. chat messages, documents, library content) can originate from
 * other users, so the HTML produced by `marked` is passed through DOMPurify to
 * strip scripts, event handlers and other XSS vectors before it reaches the DOM.
 */
export function renderMarkdown(text: string | null | undefined): string {
  const html = marked.parse(text || '', { async: false }) as string;
  return DOMPurify.sanitize(html);
}
