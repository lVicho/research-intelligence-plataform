import { AuthUser } from '../../core/api/api-models';

export type VisibilityScope = 'PUBLIC_VALIDATED' | 'MY_DATA' | 'ADMIN_ALL';

export function publicVisibilityNote(): string {
  return 'Mostrando solo datos validados';
}

export function visibilityNoteForUser(user: AuthUser | null): string {
  if (user?.roles.includes('ADMIN')) {
    return 'Incluye datos no validados';
  }
  if (user?.roles.includes('RESEARCHER') && user.researcherId !== null) {
    return 'Incluye tus datos no validados';
  }
  return publicVisibilityNote();
}

export function visibilityNoteFromMetadata(
  visibilityScope?: string | null,
  validationFilterApplied?: boolean | null
): string {
  if (visibilityScope === 'ADMIN_ALL' || validationFilterApplied === false) {
    return 'Incluye datos no validados';
  }
  if (visibilityScope === 'MY_DATA') {
    return 'Incluye tus datos no validados';
  }
  return 'Contexto limitado a datos validados';
}
