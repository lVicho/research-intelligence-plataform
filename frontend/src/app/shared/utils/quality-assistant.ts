import { MeActivity, MeActivityDetail } from '../../core/api/api-models';

export type QualityAssistantIssueCode =
  | 'missing-doi'
  | 'missing-abstract'
  | 'missing-topics'
  | 'missing-public-summary'
  | 'changes-requested';

export type QualityAssistantActionKind = 'summary' | 'topics' | 'review' | 'view';

export interface QualityAssistantDefinition {
  code: QualityAssistantIssueCode;
  badge: string;
  issue: string;
  priority: number;
  tone: 'neutral' | 'info' | 'warning';
  actionKind: QualityAssistantActionKind;
  actionLabel: string;
}

const QUALITY_ASSISTANT_DEFINITIONS: Record<QualityAssistantIssueCode, QualityAssistantDefinition> = {
  'missing-doi': {
    code: 'missing-doi',
    badge: 'Sin DOI',
    issue: 'Falta registrar el DOI de esta publicación.',
    priority: 10,
    tone: 'warning',
    actionKind: 'view',
    actionLabel: 'Ver publicación'
  },
  'missing-abstract': {
    code: 'missing-abstract',
    badge: 'Sin resumen',
    issue: 'Falta completar el resumen de esta publicación.',
    priority: 12,
    tone: 'warning',
    actionKind: 'summary',
    actionLabel: 'Sugerir resumen'
  },
  'missing-topics': {
    code: 'missing-topics',
    badge: 'Sin temas',
    issue: 'Este registro todavía no tiene temas asociados.',
    priority: 14,
    tone: 'warning',
    actionKind: 'topics',
    actionLabel: 'Sugerir temas'
  },
  'missing-public-summary': {
    code: 'missing-public-summary',
    badge: 'Sin resumen público',
    issue: 'Falta preparar un resumen público para este registro.',
    priority: 16,
    tone: 'info',
    actionKind: 'summary',
    actionLabel: 'Sugerir resumen'
  },
  'changes-requested': {
    code: 'changes-requested',
    badge: 'Requiere cambios',
    issue: 'Este registro requiere cambios antes de poder reenviarse.',
    priority: 0,
    tone: 'warning',
    actionKind: 'review',
    actionLabel: 'Revisar cambios'
  }
};

const REMINDER_MATCHERS: Array<{ code: Exclude<QualityAssistantIssueCode, 'changes-requested'>; includes: string[] }> = [
  { code: 'missing-doi', includes: ['anade doi', 'añade doi'] },
  { code: 'missing-abstract', includes: ['anade un resumen', 'añade un resumen'] },
  { code: 'missing-topics', includes: ['anade al menos un tema', 'añade al menos un tema'] },
  {
    code: 'missing-public-summary',
    includes: ['resumen publico', 'resumen público', 'sintesis publica', 'síntesis pública', 'texto publico', 'texto público']
  }
];

export function qualityAssistantDefinitionForCode(code: QualityAssistantIssueCode): QualityAssistantDefinition {
  return QUALITY_ASSISTANT_DEFINITIONS[code];
}

export function qualityAssistantDefinitionForReminder(reminder: string): QualityAssistantDefinition | null {
  const normalizedReminder = normalizeAssistantText(reminder);
  const match = REMINDER_MATCHERS.find((candidate) =>
    candidate.includes.some((value) => normalizedReminder.includes(normalizeAssistantText(value)))
  );
  return match ? qualityAssistantDefinitionForCode(match.code) : null;
}

export function qualityAssistantDefinitionsForActivity(
  activity: Pick<MeActivity | MeActivityDetail, 'validationStatus' | 'validationComment' | 'dataQualityReminders'>
): QualityAssistantDefinition[] {
  const definitions: QualityAssistantDefinition[] = [];
  const seen = new Set<QualityAssistantIssueCode>();

  if (activity.validationStatus === 'CHANGES_REQUESTED') {
    definitions.push(qualityAssistantDefinitionForCode('changes-requested'));
    seen.add('changes-requested');
  }

  for (const reminder of activity.dataQualityReminders) {
    const definition = qualityAssistantDefinitionForReminder(reminder);
    if (!definition || seen.has(definition.code)) {
      continue;
    }
    seen.add(definition.code);
    definitions.push(definition);
  }

  return definitions.sort((left, right) => left.priority - right.priority || left.badge.localeCompare(right.badge, 'es'));
}

export function supportsAiSuggestionForQualityIssue(code: QualityAssistantIssueCode): boolean {
  return code === 'missing-doi'
    || code === 'missing-abstract'
    || code === 'missing-topics'
    || code === 'missing-public-summary';
}

export function assistantActionQueryValue(actionKind: QualityAssistantActionKind): string | null {
  switch (actionKind) {
    case 'summary':
      return 'summary';
    case 'topics':
      return 'topics';
    default:
      return null;
  }
}

function normalizeAssistantText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es-ES')
    .trim();
}
