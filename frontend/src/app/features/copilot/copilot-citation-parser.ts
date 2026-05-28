import { CopilotCitation } from '../../core/api/api-models';

export type AnswerSegmentKind = 'text' | 'citation' | 'unknownCitation';

export interface AnswerSegment {
  kind: AnswerSegmentKind;
  text: string;
  publicationId: number | null;
  citationIndex: number | null;
}

export interface AnswerBlock {
  kind: 'heading' | 'paragraph' | 'list';
  segments?: AnswerSegment[];
  items?: AnswerSegment[][];
}

const citationMarkerPattern = /\[(?:pub|publication):(\d+)]/gi;

export function parseAnswerWithCitations(answer: string, citedPublications: CopilotCitation[]): AnswerBlock[] {
  const citationByPublicationId = new Map(citedPublications.map((citation) => [citation.id, citation]));
  const blocks: AnswerBlock[] = [];
  let paragraphLines: string[] = [];
  let listItems: AnswerSegment[][] = [];

  const flushParagraph = (): void => {
    if (paragraphLines.length > 0) {
      blocks.push({
        kind: 'paragraph',
        segments: parseSegments(paragraphLines.join(' '), citationByPublicationId)
      });
      paragraphLines = [];
    }
  };

  const flushList = (): void => {
    if (listItems.length > 0) {
      blocks.push({ kind: 'list', items: listItems });
      listItems = [];
    }
  };

  for (const rawLine of answer.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) {
      flushParagraph();
      flushList();
      continue;
    }

    const listMatch = line.match(/^([-*]|\d+[.)])\s+(.*)$/);
    if (listMatch) {
      flushParagraph();
      listItems.push(parseSegments(listMatch[2], citationByPublicationId));
      continue;
    }

    flushList();
    if (line.startsWith('#')) {
      flushParagraph();
      blocks.push({
        kind: 'heading',
        segments: parseSegments(line.replace(/^#+\s*/, ''), citationByPublicationId)
      });
    } else if (line.endsWith(':') && line.length <= 90) {
      flushParagraph();
      blocks.push({
        kind: 'heading',
        segments: parseSegments(line.slice(0, -1), citationByPublicationId)
      });
    } else {
      paragraphLines.push(line);
    }
  }

  flushParagraph();
  flushList();
  return blocks;
}

function parseSegments(text: string, citationByPublicationId: Map<number, CopilotCitation>): AnswerSegment[] {
  const segments: AnswerSegment[] = [];
  let cursor = 0;

  for (const match of text.matchAll(citationMarkerPattern)) {
    const marker = match[0];
    const index = match.index ?? 0;
    if (index > cursor) {
      segments.push(textSegment(text.slice(cursor, index)));
    }

    const publicationId = Number(match[1]);
    const citation = citationByPublicationId.get(publicationId);
    if (citation) {
      segments.push({
        kind: 'citation',
        text: `[${citation.citationIndex}]`,
        publicationId,
        citationIndex: citation.citationIndex
      });
    } else {
      segments.push({
        kind: 'unknownCitation',
        text: 'cita no disponible',
        publicationId,
        citationIndex: null
      });
    }

    cursor = index + marker.length;
  }

  if (cursor < text.length) {
    segments.push(textSegment(text.slice(cursor)));
  }

  return segments;
}

function textSegment(text: string): AnswerSegment {
  return {
    kind: 'text',
    text,
    publicationId: null,
    citationIndex: null
  };
}
