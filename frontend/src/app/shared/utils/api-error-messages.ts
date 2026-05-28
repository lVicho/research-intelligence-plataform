import { HttpErrorResponse } from '@angular/common/http';

const PERMISSION_MESSAGE = 'No tienes permiso para ver este contenido.';

export function contentAccessErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401 || error.status === 403 || error.status === 404) {
      return PERMISSION_MESSAGE;
    }
    const body = error.error as { message?: string } | null;
    return body?.message || fallback;
  }
  return fallback;
}
