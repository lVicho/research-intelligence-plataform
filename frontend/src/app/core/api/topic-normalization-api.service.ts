import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, delay, map, of, throwError } from 'rxjs';

import { TopicNormalizationCandidateGroup } from './api-models';

@Injectable({ providedIn: 'root' })
export class TopicNormalizationApiService {
  private readonly candidateGroupsSubject = new BehaviorSubject<TopicNormalizationCandidateGroup[]>(
    structuredClone(MOCK_TOPIC_NORMALIZATION_GROUPS)
  );

  candidateGroups(): Observable<TopicNormalizationCandidateGroup[]> {
    return this.candidateGroupsSubject.asObservable().pipe(
      map((groups) => groups.map((group) => structuredClone(group))),
      delay(220)
    );
  }

  updateCanonicalLabel(groupId: string, canonicalLabel: string): Observable<TopicNormalizationCandidateGroup> {
    const normalizedLabel = canonicalLabel.trim();
    if (!normalizedLabel) {
      return throwError(() => new Error('EMPTY_CANONICAL_LABEL'));
    }

    const updatedGroup = this.updateGroup(groupId, (group) => ({
      ...group,
      canonicalLabel: normalizedLabel,
      updatedAt: new Date().toISOString()
    }));

    return of(structuredClone(updatedGroup)).pipe(delay(220));
  }

  mergeGroup(groupId: string, canonicalLabel: string): Observable<void> {
    const normalizedLabel = canonicalLabel.trim();
    if (!normalizedLabel) {
      return throwError(() => new Error('EMPTY_CANONICAL_LABEL'));
    }

    const groups = this.candidateGroupsSubject.value;
    const nextGroups = groups.filter((group) => group.id !== groupId);
    if (nextGroups.length === groups.length) {
      return throwError(() => new Error('GROUP_NOT_FOUND'));
    }

    this.candidateGroupsSubject.next(nextGroups);
    return of(void 0).pipe(delay(320));
  }

  ignoreGroup(groupId: string): Observable<void> {
    const groups = this.candidateGroupsSubject.value;
    const nextGroups = groups.filter((group) => group.id !== groupId);
    if (nextGroups.length === groups.length) {
      return throwError(() => new Error('GROUP_NOT_FOUND'));
    }

    this.candidateGroupsSubject.next(nextGroups);
    return of(void 0).pipe(delay(220));
  }

  private updateGroup(
    groupId: string,
    updater: (group: TopicNormalizationCandidateGroup) => TopicNormalizationCandidateGroup
  ): TopicNormalizationCandidateGroup {
    const groups = this.candidateGroupsSubject.value;
    const index = groups.findIndex((group) => group.id === groupId);
    if (index < 0) {
      throw new Error('GROUP_NOT_FOUND');
    }

    const updatedGroup = updater(groups[index]);
    const nextGroups = [...groups];
    nextGroups[index] = updatedGroup;
    this.candidateGroupsSubject.next(nextGroups);
    return updatedGroup;
  }
}

const MOCK_TOPIC_NORMALIZATION_GROUPS: TopicNormalizationCandidateGroup[] = [
  {
    id: 'topic-group-1',
    confidence: 0.96,
    affectedPublicationsCount: 18,
    suggestedCanonicalLabel: 'salud digital',
    canonicalLabel: 'salud digital',
    topicsToMerge: [
      { id: 301, label: 'Salud digital', publicationCount: 9 },
      { id: 302, label: 'salud digital', publicationCount: 6 },
      { id: 303, label: 'Digital Health', publicationCount: 3 }
    ],
    affectedPublications: [
      { id: 96, title: 'Estrategias de salud digital en seguimiento remoto', path: '/publications/96' },
      { id: 104, title: 'Modelos híbridos para salud digital en atención primaria', path: '/publications/104' },
      { id: 141, title: 'Plataformas clínicas interoperables y salud digital', path: '/publications/141' }
    ],
    updatedAt: '2026-05-18T09:35:00Z'
  },
  {
    id: 'topic-group-2',
    confidence: 0.89,
    affectedPublicationsCount: 11,
    suggestedCanonicalLabel: 'ia clinica',
    canonicalLabel: 'ia clinica',
    topicsToMerge: [
      { id: 311, label: 'IA clínica', publicationCount: 4 },
      { id: 312, label: 'IA clinica', publicationCount: 5 },
      { id: 313, label: 'Inteligencia artificial clínica', publicationCount: 2 }
    ],
    affectedPublications: [
      { id: 137, title: 'Soporte a decisión con IA clínica explicable', path: '/publications/137' },
      { id: 145, title: 'Triángulo clínico de IA aplicada a urgencias', path: '/publications/145' }
    ],
    updatedAt: '2026-05-18T09:33:00Z'
  },
  {
    id: 'topic-group-3',
    confidence: 0.74,
    affectedPublicationsCount: 5,
    suggestedCanonicalLabel: 'metodos computacionales',
    canonicalLabel: 'metodos computacionales',
    topicsToMerge: [
      { id: 321, label: 'Métodos computacionales', publicationCount: 2 },
      { id: 322, label: 'Metodos computacionales', publicationCount: 2 },
      { id: 323, label: 'Computational methods', publicationCount: 1 }
    ],
    affectedPublications: [],
    updatedAt: '2026-05-18T09:31:00Z'
  }
];
